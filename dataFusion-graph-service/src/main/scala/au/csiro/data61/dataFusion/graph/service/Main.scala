package au.csiro.data61.dataFusion.graph.service

import java.io.File

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
import akka.http.scaladsl.server.Directives.{ _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, complete, entity, path, post }
import akka.stream.ActorMaterializer
import au.csiro.data61.dataFusion.common.Data.{ ClientEdgeCount, Edge, Node, T_ORGANIZATION, T_PERSON }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import io.swagger.annotations.{ Api, ApiOperation }
import javax.ws.rs.{ Consumes, Path }
import javax.ws.rs.core.MediaType
import spray.json.pimpString

// keeps getting deleted by Eclipse > Source > Organize Imports
// import javax.ws.rs.core.MediaType

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
  case class EdgeTotal(edge: Edge, totalWeight: Double, totalCount: Int)
//  case class NodeQuery(text: String, typ: String)
  case class TopClientsQuery(includePerson2: Boolean, extRefIds: List[Long], n: Int)
  case class TopConnectedQuery(includePerson2: Boolean, collections: Option[Set[String]], n: Int)
  case class GraphQuery(includePerson2: Boolean, collections: Option[Set[String]], nodeId: Option[Int], extRefId: Option[Long], maxHops: Int, maxEdges: Int) // specify either nodeId or extRefId
  case class Graph(nodes: List[Node], edges: List[EdgeTotal])
  case class ClientEdgeCounts(counts: List[ClientEdgeCount])
  
  type NodeMap = Map[Int, Node]
  type EdgeMap = Map[Int, IndexedSeq[Edge]]
  case class NodeEdgeMap(nodes: NodeMap, edges: IndexedSeq[Edge], edgesBySource: EdgeMap, edgesByTarget: EdgeMap)
  
  object JsonProtocol {
    implicit val nodesCodec = jsonFormat1(Nodes)
    implicit val edgeTotalCodec = jsonFormat3(EdgeTotal)
//    implicit val nodeQueryCodec = jsonFormat2(NodeQuery)
    implicit val topClientsQueryCodec = jsonFormat3(TopClientsQuery)
    implicit val topConnectedQueryCodec = jsonFormat3(TopConnectedQuery)
    implicit val graphQueryCodec = jsonFormat6(GraphQuery)
    implicit val graphCodec = jsonFormat2(Graph)
    implicit val clientEdgeCountsCodec = jsonFormat1(ClientEdgeCounts)
  }
  import JsonProtocol._
  
  def load(nodeSource: Source, edgeSource: Source) = {
    val nodes = nodeSource.getLines.map { json =>
      val n = json.parseJson.convertTo[Node]
      n.nodeId -> n
    }.toMap
    log.info(s"load: loaded ${nodes.size} nodes")
        
    val edges = edgeSource.getLines.map(_.parseJson.convertTo[Edge]).toIndexedSeq
    log.info(s"load: loaded ${edges.size} edges")
    
    // exclude T_PERSON2 and D61EMAIL FROM|TO etc. nodes
    // user can choose network restricted to these nodes or not
    def perOrgPred(n: Node) = n.typ == T_PERSON || n.typ == T_ORGANIZATION    
    val node3Ids = nodes.values.view.filter(perOrgPred).map(_.nodeId).toSet
    val edges3 = edges.filter(e => node3Ids.contains(e.source) && node3Ids.contains(e.target))
    val node3Ids2 = (edges3.view.map(_.source) ++ edges3.view.map(_.target)).toSet
    val nodes3 = nodes.filter { case (nodeId, _) => node3Ids2.contains(nodeId) }
    log.info(s"load: filtered typ = ${T_PERSON} or ${T_ORGANIZATION}: ${nodes3.size} nodes and ${edges3.size} edges")
    
    // extRef.id -> node.id
    def extRefIdToNodeId(pred: Node => Boolean) = (for {
      n <- nodes.values.view if pred(n)
      id <- n.extRef.ids
    } yield id -> n.nodeId).toMap // a many to one mapping
    
    def edgeMap(edges: IndexedSeq[Edge], key: Edge => Int) = edges.groupBy(key).withDefaultValue(IndexedSeq.empty)
    
    (NodeEdgeMap(nodes, edges, edgeMap(edges, _.source), edgeMap(edges, _.target)), NodeEdgeMap(nodes3, edges3, edgeMap(edges3, _.source), edgeMap(edges3, _.target)), extRefIdToNodeId(perOrgPred), extRefIdToNodeId(n => !perOrgPred(n)))
  }
  
  @Api(value = "graph", description = "graph service", produces = "application/json")
  @Path("")
  class GraphService(nodeSource: Source, edgeSource: Source) {
    val (nodeEdgeMap, nodeEdgeMap3, extRefIdToPersonNodeId, extRefIdToPerson2EmailNodeId) = load(nodeSource, edgeSource)
    log.info(s"GraphService.ctor: load complete: extRefIdToPersonNodeId.size = ${extRefIdToPersonNodeId.size}, extRefIdToPerson2EmailNodeId.size = ${extRefIdToPerson2EmailNodeId.size}")
    
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
    @ApiOperation(httpMethod = "POST", response = classOf[ClientEdgeCounts], value = "graph of the n strongest edges from nodes matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def topConnectedClients(q: TopClientsQuery): ClientEdgeCounts = {
      log.info(s"topConnectedClients: q = $q")
      val nem = if (q.includePerson2) nodeEdgeMap else nodeEdgeMap3
      val nodeIds = q.extRefIds.flatMap(extRefIdToNodeId).toSet
      val l = nodeIds.view.map { id => 
        ClientEdgeCount(id, nodeEdgeMap.edgesBySource(id).size + nodeEdgeMap.edgesByTarget(id).size)
      }.toList.sortBy(-_.numEdges).take(q.n)
      ClientEdgeCounts(l)
    }
    
    def topConnectedClientsRoute =
      post { path("topConnectedClients") { entity(as[TopClientsQuery]) { q => complete {
        topConnectedClients(q)
      }}}}
  
    // ----------------------------------------------------------
    
    // TODO: needs to be as fast as possible
    def edgeTotalWeight(collections: Option[Set[String]]): Edge => EdgeTotal = collections.map { col => 
      (e: Edge) => 
        val (w, c) = e.weights.foldLeft((0.0, 0)) { case (z@(w, c), (k, (w1, c1))) => if (col contains k) (w + w1, c + c1) else z }
        EdgeTotal(e, w, c)
    }.getOrElse {
      (e: Edge) => 
        val (w, c) = e.weights.values.foldLeft((0.0, 0)) { case ((w, c), (w1, c1)) => (w + w1, c + c1) }
        EdgeTotal(e, w, c)
    }
    
    @Path("topConnectedGraph")
    @ApiOperation(httpMethod = "POST", response = classOf[Graph], value = "graph of the strongest edges from nodes matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def topConnectedGraph(q: TopConnectedQuery): Graph = {
      log.info(s"topConnectedGraph: q = $q")
      
      val nem = if (q.includePerson2) nodeEdgeMap else nodeEdgeMap3
      val etw = edgeTotalWeight(q.collections)
      val edges1 = nem.edges.view.map(etw).filter(_.totalWeight > 0.0).toIndexedSeq
      log.info("topConnectedGraph: got total weights for all edges")
      val edges = edges1.sortBy(-_.totalWeight).view.take(q.n).toList // sort by weight descending
      log.info("topConnectedGraph: got highest weight edges")
      val nodes = (edges.view.map(_.edge.source) ++ edges.view.map(_.edge.target)).toSet.view.map(nem.nodes).toList
      log.info("topConnectedGraph: got nodes")
      Graph(nodes, edges)
    }
      
    def topConnectedGraphRoute =
      post { path("topConnectedGraph") { entity(as[TopConnectedQuery]) { q => complete {
        topConnectedGraph(q)
      }}}}
    
    // ----------------------------------------------------------
    
    def distance(a: Double, b: Double) = Math.sqrt(a * a + b * b)
    
    @tailrec final def expand(nem: NodeEdgeMap, ew: Edge => EdgeTotal, n: Int, newIds: Set[Int], nodeDist: Map[Int, Double], edges: Set[EdgeTotal]= Set.empty): (Int, Set[Int], Map[Int, Double], Set[EdgeTotal]) = {
      if (n < 1) (n, newIds, nodeDist, edges) else {
        val knownSource = newIds.view.flatMap(id => nem.edgesBySource(id).view.map(ew)) // edges with known node as source
        val nodeDist1 = knownSource.foldLeft(nodeDist) { (z, e) => 
          val d0 = z.get(e.edge.target)
          val d1 = distance(z(e.edge.source), 1.0/e.totalWeight)
          val d3 = d0.map(Math.min(_, d1)).getOrElse(d1)
          z + (e.edge.target -> d3)
        }
        val knownTarget = newIds.view.flatMap(id => nem.edgesByTarget(id).view.map(ew)) // edges with known node as target
        val nodeDist2 = knownTarget.foldLeft(nodeDist1) { (z, e) => 
          val d0 = z.get(e.edge.source)
          val d1 = distance(z(e.edge.target), 1.0/e.totalWeight)
          val d3 = d0.map(Math.min(_, d1)).getOrElse(d1)
          z + (e.edge.source -> d3)
        }
        val nIds = nodeDist2.keySet &~ nodeDist.keySet
        // log.debug(s"expand: nIds = $nIds, knownSource = $knownSource, knownTarget = $knownTarget, nodeDist2 = $nodeDist2")
        log.info("expand: recurse ...")
        expand(nem, ew, n - 1, nIds, nodeDist2, (knownSource ++ knownTarget).toSet ++ edges)
      }
    }
    
    // ----------------------------------------------------------
  
    @Path("graph")
    @ApiOperation(httpMethod = "POST", response = classOf[Graph], value = "graph matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def graph(q: GraphQuery): Graph = {
      log.info(s"graph: q = $q")
      val nem = if (q.includePerson2) nodeEdgeMap else nodeEdgeMap3
      val etw = edgeTotalWeight(q.collections)
      q.nodeId.map(Set(_)).orElse(q.extRefId.map(extRefIdToNodeId(_).toSet)).map { nodeIds =>
        log.debug(s"graph: nodeIds = ${nodeIds}")
        val (_, _, nodeDist, edges) = expand(nem, etw, q.maxHops, nodeIds, nodeIds.map(_ -> 0.0).toMap)
        log.info(s"graph: got ${edges.size} edges")
        // sort edges by distance to furtherest end, ascending
        val topEdges = edges.toIndexedSeq.sortBy(e => Math.max(nodeDist(e.edge.source), nodeDist(e.edge.target))).take(q.maxEdges)
        log.info(s"graph: got top edges")
        val topNodes = (topEdges.view.map(_.edge.source) ++ topEdges.view.map(_.edge.target)).toSet.view.map(nem.nodes).toList
        log.info(s"graph: got top nodes")
        Graph(topNodes, topEdges.toList)
      }.getOrElse {
        log.info(s"graph: no nodes match query q = $q")
        Graph(List.empty, List.empty)
      }
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
    override def swaggerConfig = new io.swagger.models.Swagger().basePath(prependSlashIfNecessary(basePath)) // don't specify protocol://host basePath
//    override val host = s"${hst}:${prt}" // the url of your api, not swagger's json endpoint
//    override val basePath = "/"          // the basePath for the API you are exposing
    override val info = new io.swagger.models.Info()                    // provides license and other description details
    override val apiDocsPath = "api-docs"   // http://host:port/api-docs/swagger.json
  }
  
}