package au.csiro.data61.dataFusion.graph.service

import scala.annotation.tailrec
import scala.io.Source
import scala.language.postfixOps
import scala.reflect.runtime.universe.typeOf

import com.github.swagger.akka.{ HasActorSystem, SwaggerHttpService }
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import au.csiro.data61.dataFusion.common.Data.ClientEdgeCount
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.clientEdgeCountFormat
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import io.swagger.annotations.{ Api, ApiOperation }
import javax.ws.rs.{ Consumes, Path, QueryParam }, javax.ws.rs.core.MediaType
import spray.json.DefaultJsonProtocol._
import spray.json.pimpString
import java.io.File
import au.csiro.data61.dataFusion.common.Data._
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._
import scala.collection.mutable

// keeps getting deleted by Eclipse > Source > Organize Imports

// TODO: change input from JSON files to some Hadoop data source?
object Main {
  private val log = Logger(getClass)
  
  case class CliOption(nodePath: File, edgePath: File, host: String, port: Int)

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory.load
    val defaultCliOption = CliOption(new File(conf.getString("graph.nodePath")), new File(conf.getString("graph.edgePath")), conf.getString("http.host"), conf.getInt("http.port"))
    val parser = new scopt.OptionParser[CliOption]("graph") {
      head("graph", "0.x")
      note("Run Graph web service.")
      opt[File]("nodePath") action { (v, c) =>
        c.copy(nodePath = v)
      } text (s"path of JSON file containing nodes, default ${defaultCliOption.nodePath}")
      opt[File]("edgePath") action { (v, c) =>
        c.copy(edgePath = v)
      } text (s"path of JSON file containing edges, default ${defaultCliOption.edgePath}")
      opt[String]("host") action { (v, c) =>
        c.copy(host = v)
      } text (s"host interface for web service, default ${defaultCliOption.host}")
      opt[Int]("port") action { (v, c) =>
        c.copy(port = v)
      } text (s"port for web service, default ${defaultCliOption.port}")
      help("help") text ("prints this usage text")
    }
    for (c <- parser.parse(args, defaultCliOption)) {
      log.info(s"CliOption: $c}")
      start(c)
    }      
  }
  
  def start(c: CliOption) = {
    implicit val system = ActorSystem("graphActorSystem")
    implicit val exec = system.dispatcher
    implicit val materializer = ActorMaterializer()
    
    val graphService = new GraphService(Source.fromFile(c.nodePath), Source.fromFile(c.edgePath))
    val routes = cors() {
      graphService.routes ~ 
      swaggerService(c.host, c.port).routes
    }
    Http().bindAndHandle(routes, c.host, c.port)
    log.info(s"""starting server at: http://${c.host}:${c.port}
Test with:
  curl --header 'Content-Type: application/json' http://${c.host}:${c.port}/api-docs/swagger.json
""")
  }

  case class Nodes(nodes: List[Node])
//  case class NodeQuery(text: String, typ: String)
  case class TopClientsQuery(extRefIds: List[Long], n: Int)
  case class GraphQuery(extRefId: Int, maxHops: Int, maxEdges: Int)
  case class Graph(nodes: List[Node], edges: List[Edge])
  case class ClientEdgeCounts(counts: List[ClientEdgeCount])
  
  object JsonProtocol {
    implicit val nodesCodec = jsonFormat1(Nodes)
//    implicit val nodeQueryCodec = jsonFormat2(NodeQuery)
    implicit val topClientsQueryCodec = jsonFormat2(TopClientsQuery)
    implicit val graphQueryCodec = jsonFormat3(GraphQuery)
    implicit val graphCodec = jsonFormat2(Graph)
    implicit val clientEdgeCountsCodec = jsonFormat1(ClientEdgeCounts)
  }
  import JsonProtocol._
  
  def load(nodeSource: Source, edgeSource: Source) = {
    val nodes = nodeSource.getLines.map { json =>
      val n = json.parseJson.convertTo[Node]
      n.nodeId -> n
    }.toMap
    
    // extRef.id -> node.id
    def extRefIdToNodeId(pred: Node => Boolean) = (for {
      n <- nodes.values if pred(n)
      id <- n.extRef.ids
    } yield id -> n.nodeId).toMap
    
    val edges = edgeSource.getLines.map(_.parseJson.convertTo[Edge]).toIndexedSeq
    def edgeMap(key: Edge => Int) = edges.groupBy(key).withDefaultValue(IndexedSeq.empty)
    val person2Email = Set(T_PERSON2, "FROM", "TO", "CC", "BCC")
    (nodes, extRefIdToNodeId(n => n.typ == T_PERSON), extRefIdToNodeId(n => person2Email.contains(n.typ)), edgeMap(_.source), edgeMap(_.target))
  }
  
  @Api(value = "graph", description = "graph service", produces = "application/json")
  @Path("")
  class GraphService(nodeSource: Source, edgeSource: Source) {
    val (nodes, extRefIdToPersonNodeId, extRefIdToPerson2EmailNodeId, edgesBySource, edgesByTarget) = load(nodeSource, edgeSource)
    
    /** extRef.id -> list of 0, 1, or 2 nodeIds */
    def extRefIdToNodeId(id: Long) = extRefIdToPersonNodeId.get(id).toList ++ extRefIdToPerson2EmailNodeId.get(id).toList
    
    // ----------------------------------------------------------
  
//    @Path("nodes")
//    @ApiOperation(httpMethod = "POST", response = classOf[Nodes], value = "nodes matching the query")
//    @Consumes(Array(MediaType.APPLICATION_JSON))
//    def findNodes(q: NodeQuery) = {
//      val pred: Node => Boolean =
//        if (Seq(T_PERSON, T_ORGANIZATION).contains(q.typ))
//          n => n.`type` == q.`type` && n.label.contains(q.text)
//        else
//          n => n.label.contains(q.text)
//        
//      Nodes(nodes.values.filter(pred).toList)
//    }
//        
//    def findNodesRoute =
//      post { path("nodes") { entity(as[NodeQuery]) { q => complete {
//        findNodes(q)
//      }}}}
  
    // ----------------------------------------------------------
  
    /**
     * return the q.n items from q.ids that have the most connections (edges) over all the docs.
     * @return Seq of n * (nodeId, number of edges) sorted on number of edges descending
     */
    @Path("topConnectedClients")
    @ApiOperation(httpMethod = "POST", response = classOf[ClientEdgeCounts], value = "graph of the most connected nodes matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def topConnectedClients(q: TopClientsQuery): ClientEdgeCounts = {
      val nodeIds = q.extRefIds.flatMap(extRefIdToNodeId).toSet
      val l = nodeIds.view.map { id => 
        ClientEdgeCount(id, edgesBySource(id).size + edgesByTarget(id).size)
      }.toList.sortBy(-_.numEdges).take(q.n)
      ClientEdgeCounts(l)
    }
    
    def topConnectedClientsRoute =
      post { path("topConnectedClients") { entity(as[TopClientsQuery]) { q => complete {
        topConnectedClients(q)
      }}}}
  
    // ----------------------------------------------------------
    
    @Path("topConnectedGraph")
    @ApiOperation(httpMethod = "GET", response = classOf[Graph], value = "graph of the num most connected nodes")
    def topConnectedGraph(@QueryParam("num") num: Int) = {
      // as above but for all nodes
      val topIds = nodes.keys.map { id => 
        (id, edgesBySource(id).size + edgesByTarget(id).size)
      }.toList.sortBy(-_._2).take(num).map(_._1)
      
      val topEdges = topIds.foldLeft(new mutable.HashSet[Edge]) { case (s, nodeId) => 
        s ++= edgesBySource(nodeId)
        s ++= edgesByTarget(nodeId)
      }.toList.sortBy(- _.distance).take(num)
      
      val nodesInTopeEdges = (topEdges.view.map(_.source) ++ topEdges.view.map(_.target)).toSet.view.map(nodes).toList
      Graph(nodesInTopeEdges, topEdges)
    }
      
    def topConnectedGraphRoute =
      get { path("topConnectedGraph") { parameters("num".as[Int]) { num => complete {
        topConnectedGraph(num)
      }}}}
    
    // ----------------------------------------------------------
    
    def distance(a: Float, b: Float) = Math.sqrt(a * a + b * b).toFloat
    
    @tailrec final def expand(n: Int, newIds: Set[Int], nodeDist: Map[Int, Float], edges: Set[Edge]= Set.empty): (Int, Set[Int], Map[Int, Float], Set[Edge]) = {
      if (n < 1) (n, newIds, nodeDist, edges) else {
        val l = newIds.toList
        val knownSource = l.flatMap(id => edgesBySource(id)) // edges with known node as source
        val knownTarget = l.flatMap(id => edgesByTarget(id)) // edges with known node as target
        val nodeDist1 = knownSource.foldLeft(nodeDist) { (z, e) => 
          val d0 = z.get(e.target)
          val d1 = distance(z(e.source), e.distance)
          val d3 = d0.map(Math.min(_, d1)).getOrElse(d1)
          z + (e.target -> d3)
        }
        val nodeDist2 = knownTarget.foldLeft(nodeDist1) { (z, e) => 
          val d0 = z.get(e.source)
          val d1 = distance(z(e.target), e.distance)
          val d3 = d0.map(Math.min(_, d1)).getOrElse(d1)
          z + (e.source -> d3)
        }
        val nIds = nodeDist2.keySet &~ nodeDist.keySet
        log.debug(s"expand: nIds = $nIds, knownSource = $knownSource, knownTarget = $knownTarget, nodeDist2 = $nodeDist2")
        expand(n - 1, nIds, nodeDist2, knownSource.toSet ++ knownTarget.toSet ++ edges)
      }
    }
    
    // ----------------------------------------------------------
  
    @Path("graph")
    @ApiOperation(httpMethod = "POST", response = classOf[Graph], value = "graph matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def graph(q: GraphQuery) = {
      val nodeIds = extRefIdToNodeId(q.extRefId).toSet
      val (_, _, nodeDist, edges) = expand(q.maxHops, nodeIds, nodeIds.map(_ -> 0.0f).toMap)
      // sort edges by distance to furtherest end, ascending
      val topEdges = edges.toList.sortBy(e => Math.max(nodeDist(e.source), nodeDist(e.target))).take(q.maxEdges)
      val ids = (topEdges.map(_.source) ++ topEdges.map(_.target)).toSet
      Graph(ids.toList.map(nodes), topEdges)
    }
    
    def graphRoute =
      post { path("graph") { entity(as[GraphQuery]) { q => complete {
        graph(q)
      }}}}
  
    val routes = topConnectedClientsRoute ~ topConnectedGraphRoute ~ graphRoute
  }
  
  def swaggerService(hst: String, prt: Int)(implicit s: ActorSystem, m: ActorMaterializer) = new SwaggerHttpService with HasActorSystem {
    override implicit val actorSystem = s
    override implicit val materializer = m
    override val apiTypes = Seq(typeOf[GraphService])
    // override def swaggerConfig = new Swagger().basePath(prependSlashIfNecessary(basePath)) // don't specify protocol://host basePath
    override val host = s"${hst}:${prt}" // the url of your api, not swagger's json endpoint
    override val basePath = "/"          // the basePath for the API you are exposing
    override val info = new io.swagger.models.Info()                    // provides license and other description details
    override val apiDocsPath = "api-docs"   // http://host:port/api-docs/swagger.json
  }
  
}