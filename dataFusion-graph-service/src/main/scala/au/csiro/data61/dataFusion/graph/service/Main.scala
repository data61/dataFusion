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

// keeps getting deleted by Eclipse > Source > Organize Imports

// TODO: change input from JSON files to some Hadoop data source?
object Main {
  private val log = Logger(getClass)
  
  case class CliOption(blazeTimeout: Int)
  case class Node(id: Long, label: String, `type`: Int)
  case class Nodes(nodes: List[Node])
  case class Edge(source: Long, target: Long, distance: Float, `type`: Int)
  case class NodeQuery(text: String, `type`: Int)
  case class TopClientsQuery(ids: List[Long], n: Int)
  case class GraphQuery(id: Long, maxHops: Int, maxEdges: Int)
  case class Graph(nodes: List[Node], edges: List[Edge])
  case class ClientEdgeCounts(counts: List[ClientEdgeCount])
  
  object JsonProtocol {
    implicit val nodeCodec = jsonFormat3(Node)
    implicit val nodesCodec = jsonFormat1(Nodes)
    implicit val edgeCodec = jsonFormat4(Edge)
    implicit val nodeQueryCodec = jsonFormat2(NodeQuery)
    implicit val topClientsQueryCodec = jsonFormat2(TopClientsQuery)
    implicit val graphQueryCodec = jsonFormat3(GraphQuery)
    implicit val graphCodec = jsonFormat2(Graph)
    implicit val clientEdgeCountsCodec = jsonFormat1(ClientEdgeCounts)
  }
  import JsonProtocol._
  
  def load(nodeSource: Source, edgeSource: Source) = {
    val nodes = nodeSource.getLines.map { json =>
      val n = json.parseJson.convertTo[Node]
      n.id -> n
    }.toMap
    val edges = edgeSource.getLines.map(_.parseJson.convertTo[Edge]).toList
    (nodes, edges.groupBy(_.source).withDefaultValue(List.empty), edges.groupBy(_.target).withDefaultValue(List.empty))
  }
  
  @Api(value = "graph", description = "graph service", produces = "application/json")
  @Path("")
  class GraphService(nodeSource: Source, edgeSource: Source) {
    val (nodes, edgesBySource, edgesByTarget) = load(nodeSource, edgeSource)
    
    val personType = 1
    val orgType = 2
    
    // ----------------------------------------------------------
  
    @Path("nodes")
    @ApiOperation(httpMethod = "POST", response = classOf[Nodes], value = "nodes matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def findNodes(q: NodeQuery) = {
      val pred: Node => Boolean =
        if (Seq(personType, orgType).contains(q.`type`))
          n => n.`type` == q.`type` && n.label.contains(q.text)
        else
          n => n.label.contains(q.text)
        
      Nodes(nodes.values.filter(pred).toList)
    }
        
    def findNodesRoute =
      post { path("nodes") { entity(as[NodeQuery]) { q => complete {
        findNodes(q)
      }}}}
  
    // ----------------------------------------------------------
  
    /**
     * return the q.n items from q.ids that have the most connections (edges) over all the docs.
     * @return Seq of n * (clientIntrnlId, number of edges) sorted on number of edges descending
     */
    @Path("topConnectedClients")
    @ApiOperation(httpMethod = "POST", response = classOf[ClientEdgeCounts], value = "graph of the most connected nodes matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def topConnectedClients(q: TopClientsQuery): ClientEdgeCounts = {
      val l = q.ids.map { id => 
        ClientEdgeCount(id, edgesBySource(id).size + edgesByTarget(id).size)
      }.sortBy(-_.numEdges).take(q.n)
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
      val topEdges = topIds.flatMap(id => edgesBySource(id) ++ edgesByTarget(id)).sortBy(_.distance).take(num)
      val ids = (topEdges.map(_.source) ++ topEdges.map(_.target)).toSet
      Graph(ids.toList.map(nodes), topEdges)
    }
      
    def topConnectedGraphRoute =
      get { path("topConnectedGraph") { parameters("num".as[Int]) { num => complete {
        topConnectedGraph(num)
      }}}}
    
    // ----------------------------------------------------------
    
    def distance(a: Float, b: Float) = Math.sqrt(a * a + b * b).toFloat
    
    @tailrec final def expand(n: Int, newIds: Set[Long], nodeDist: Map[Long, Float], edges: Set[Edge]= Set.empty): (Int, Set[Long], Map[Long, Float], Set[Edge]) = {
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
      val (_, _, nodeDist, edges) = expand(q.maxHops, Set(q.id), Map(q.id -> 0.0f))
      // sort edges by distance to furtherest end, ascending
      val topEdges = edges.toList.sortBy(e => Math.max(nodeDist(e.source), nodeDist(e.target))).take(q.maxEdges)
      val ids = (topEdges.map(_.source) ++ topEdges.map(_.target)).toSet
      Graph(ids.toList.map(nodes), topEdges)
    }
    
    def graphRoute =
      post { path("graph") { entity(as[GraphQuery]) { q => complete {
        graph(q)
      }}}}
  
    val routes = findNodesRoute ~ topConnectedClientsRoute ~ topConnectedGraphRoute ~ graphRoute
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
  
  def start(c: CliOption) = {
    val conf = ConfigFactory.load
    val host = conf.getString("http.host")
    val port = conf.getInt("http.port")
    
    implicit val system = ActorSystem("graphActorSystem")
    implicit val exec = system.dispatcher
    implicit val materializer = ActorMaterializer()
    
    val graphService = new GraphService(Source.fromFile(conf.getString("graph.nodePath")), Source.fromFile(conf.getString("graph.edgePath")))
    val routes = cors() {
      graphService.routes ~ 
      swaggerService(host, port).routes
    }
    Http().bindAndHandle(routes, host, port)
    log.info(s"""starting server at: http://${host}:${port}
Test with:
  curl --header 'Content-Type: application/json' http://${host}:${port}/api-docs/swagger.json
""")
  }

  def main(args: Array[String]): Unit = {
    val defaultCliOption = CliOption(600) // timeout in secs
    val parser = new scopt.OptionParser[CliOption]("graph") {
      head("graph", "0.x")
      note("Run Graph web service.")
      opt[Int]('b', "blaze-timeout") action { (x, c) =>
        c.copy(blazeTimeout = x)
      } text (s"Timout in seconds for Blaze HTTP server (default ${defaultCliOption.blazeTimeout})")
      help("help") text ("prints this usage text")
    }
    for (c <- parser.parse(args, defaultCliOption)) {
      start(c)
    }      
  }
  
}