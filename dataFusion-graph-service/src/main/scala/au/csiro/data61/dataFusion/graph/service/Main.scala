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
import au.csiro.data61.dataFusion.common.Data.{ NodeEdgeCount, Edge, Node, T_ORGANIZATION, T_PERSON }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import io.swagger.annotations.{ Api, ApiOperation }
import javax.ws.rs.{ Consumes, Path }
import javax.ws.rs.core.MediaType
import spray.json.pimpString
import au.csiro.data61.dataFusion.common.Util
import au.csiro.data61.dataFusion.common.Timer
import au.csiro.data61.dataFusion.common.Data.WeightMap
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader

// keeps getting deleted by Eclipse > Source > Organize Imports
// import javax.ws.rs.core.MediaType

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(cacheSize: Int, nodePath: File, edgePath: File, host: String, port: Int)

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory.load
    val defaultCliOption = CliOption(conf.getInt("graph.cacheSize"), new File(conf.getString("graph.nodePath")), new File(conf.getString("graph.edgePath")), conf.getString("http.host"), conf.getInt("http.port"))
    val parser = new scopt.OptionParser[CliOption]("graph") {
      head("graph", "0.x")
      note("Run Graph web service.")
      opt[Int]("cacheSize") action { (v, c) =>
        c.copy(cacheSize = v)
      } text (s"max number of results cached by each service method, default ${defaultCliOption.cacheSize}")
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
    
    val graphService = new GraphService(c.cacheSize, Source.fromFile(c.nodePath), Source.fromFile(c.edgePath))
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
  case class GEdge(source: Node, target: Node, typ: String, weights: WeightMap, minScore: Double, totalWeight: Double, totalCount: Int)
  case class GrEdge(source: Int, target: Int, typ: String, weights: WeightMap, minScore: Double, totalWeight: Double, totalCount: Int)
//  case class NodeQuery(text: String, typ: String)
  case class TopClientsQuery(includePerson2: Boolean, minScore: Double, extRefIds: List[Long], maxNodes: Int)
  case class TopConnectedQuery(includePerson2: Boolean, minScore: Double, collections: Option[Set[String]], maxEdges: Int)
  case class GraphQuery(includePerson2: Boolean, minScore: Double, collections: Option[Set[String]], nodeId: Option[Int], extRefId: Option[Long], maxHops: Int, maxEdges: Int) // specify either nodeId or extRefId
  case class Graph(nodes: List[Node], edges: List[GrEdge])
  case class NodeEdgeCounts(counts: List[NodeEdgeCount])
  
  type NodeMap = Map[Int, Node]
  type EdgeMap = Map[Int, IndexedSeq[GEdge]]
  case class NodeEdgeMap(nodes: NodeMap, edges: IndexedSeq[GEdge], edgesBySource: EdgeMap, edgesByTarget: EdgeMap)
  
  object JsonProtocol {
    implicit val nodesCodec = jsonFormat1(Nodes)
    implicit val grEdgeCodec = jsonFormat7(GrEdge)
//    implicit val nodeQueryCodec = jsonFormat2(NodeQuery)
    implicit val topClientsQueryCodec = jsonFormat4(TopClientsQuery)
    implicit val topConnectedQueryCodec = jsonFormat4(TopConnectedQuery)
    implicit val graphQueryCodec = jsonFormat7(GraphQuery)
    implicit val graphCodec = jsonFormat2(Graph)
    implicit val clientEdgeCountsCodec = jsonFormat1(NodeEdgeCounts)
  }
  import JsonProtocol._
  
  def load(nodeSource: Source, edgeSource: Source) = {
    val nodes = nodeSource.getLines.map { json =>
      val n = json.parseJson.convertTo[Node]
      n.nodeId -> n
    }.toMap
    log.info(s"load: loaded ${nodes.size} nodes")
        
    val edges = edgeSource.getLines.map{ json =>
      val e = json.parseJson.convertTo[Edge]
      val (w, c) = e.weights.values.foldLeft((0.0, 0)) { case ((w, c), (w1, c1)) => (w + w1, c + c1) }
      val src = nodes(e.source)
      val tgt = nodes(e.target)
      GEdge(src, tgt, e.typ, e.weights, Math.min(src.score, tgt.score), w, c)
    }.toIndexedSeq
    log.info(s"load: loaded ${edges.size} edges")
    
    // exclude T_PERSON2 and D61EMAIL FROM|TO etc. nodes
    // user can choose network restricted to these nodes or not
    def perOrgPred(n: Node) = n.typ == T_PERSON || n.typ == T_ORGANIZATION    
    val edges3 = edges.filter(e => perOrgPred(e.source) && perOrgPred(e.target))
    val node3Ids = (edges3.view.map(_.source.nodeId) ++ edges3.view.map(_.target.nodeId)).toSet
    val nodes3 = nodes.filter { case (nodeId, _) => node3Ids.contains(nodeId) }
    log.info(s"load: filtered typ = ${T_PERSON} or ${T_ORGANIZATION}: ${nodes3.size} nodes and ${edges3.size} edges")
    
    // extRef.id -> node.id
    def extRefIdToNode(pred: Node => Boolean) = (for {
      n <- nodes.values.view if pred(n)
      id <- n.extRef.ids
    } yield id -> n).toMap // a many to one mapping
    
    def edgeMap(edges: IndexedSeq[GEdge], key: GEdge => Int) = edges.groupBy(key).withDefaultValue(IndexedSeq.empty)
    
    (
      NodeEdgeMap(nodes, edges, edgeMap(edges, _.source.nodeId), edgeMap(edges, _.target.nodeId)), 
      NodeEdgeMap(nodes3, edges3, edgeMap(edges3, _.source.nodeId), edgeMap(edges3, _.target.nodeId)), 
      extRefIdToNode(perOrgPred), 
      extRefIdToNode(n => !perOrgPred(n))
    )
  }
  
  
  @Api(value = "graph", description = "graph service", produces = "application/json")
  @Path("")
  class GraphService(cacheSize: Int, nodeSource: Source, edgeSource: Source) {
    val (nodeEdgeMap, nodeEdgeMap3, extRefIdToPersonNode, extRefIdToPerson2EmailNode) = load(nodeSource, edgeSource)
    log.info(s"GraphService.ctor: load complete: extRefIdToPersonNodeId.size = ${extRefIdToPersonNode.size}, extRefIdToPerson2EmailNodeId.size = ${extRefIdToPerson2EmailNode.size}")
    
    /** extRef.id -> list of 0, 1, or 2 nodeIds */
    def extRefIdToNode(includePerson2: Boolean): Long => List[Node] = 
      if (includePerson2) id => extRefIdToPersonNode.get(id).toList ++ extRefIdToPerson2EmailNode.get(id).toList
      else id => extRefIdToPersonNode.get(id).toList
    
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
    @ApiOperation(httpMethod = "POST", response = classOf[NodeEdgeCounts], value = "graph of the n strongest edges from nodes matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def topConnectedClients(q: TopClientsQuery): NodeEdgeCounts = {
      log.info(s"topConnectedClients: q = $q")
      val nem = if (q.includePerson2) nodeEdgeMap else nodeEdgeMap3
      val nodeIds = q.extRefIds.flatMap(extRefIdToNode(q.includePerson2)).filter(_.score >= q.minScore).map(_.nodeId).toSet
      val iter = nodeIds.iterator.map { id => 
        NodeEdgeCount(id, nodeEdgeMap.edgesBySource(id).size + nodeEdgeMap.edgesByTarget(id).size)
      }
      val top = Util.top(q.maxNodes, iter)(Ordering.by(_.numEdges))
      NodeEdgeCounts(top)
    }
    
    val topConnectedClientsCache = CacheBuilder.newBuilder.maximumSize(cacheSize).build(
       new CacheLoader[TopClientsQuery, NodeEdgeCounts] {
         def load(q: TopClientsQuery) = topConnectedClients(q)
       }
    )
    
    def topConnectedClientsRoute =
      post { path("topConnectedClients") { entity(as[TopClientsQuery]) { q => complete {
        topConnectedClientsCache.get(q)
      }}}}
  
    // ----------------------------------------------------------
    
    val toGrEdge = (e: GEdge) => GrEdge(e.source.nodeId, e.target.nodeId, e.typ, e.weights, e.minScore, e.totalWeight, e.totalCount)
    
    def edgeTotalWeight(collections: Option[Set[String]]): GEdge => GEdge = collections.map { col => 
      (e: GEdge) => 
        val (w, c) = e.weights.foldLeft((0.0, 0)) { case (z@(w, c), (k, (w1, c1))) => if (col contains k) (w + w1, c + c1) else z }
        e.copy(totalWeight = w, totalCount = c)
    }.getOrElse(identity)
    
    @Path("topConnectedGraph")
    @ApiOperation(httpMethod = "POST", response = classOf[Graph], value = "graph of the strongest edges from the selected collections")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def topConnectedGraph(q: TopConnectedQuery): Graph = {
      log.info(s"topConnectedGraph: q = $q")
      val nem = if (q.includePerson2) nodeEdgeMap else nodeEdgeMap3
      val etw = edgeTotalWeight(q.collections)
      val edges1 = for {
        e <- nem.edges.iterator if e.minScore >= q.minScore
      } yield etw(e)
      val edges = Util.top(q.maxEdges, edges1)(Ordering.by(_.totalWeight)) // top edges by weight
      log.debug("topConnectedGraph: got highest weight edges")
      val nodes = (edges.view.map(_.source) ++ edges.view.map(_.target)).toSet.toList
      log.debug("topConnectedGraph: got nodes")
      Graph(nodes, edges map toGrEdge)
    }
      
    val topConnectedGraphCache = CacheBuilder.newBuilder.maximumSize(cacheSize).build(
       new CacheLoader[TopConnectedQuery, Graph] {
         def load(q: TopConnectedQuery) = topConnectedGraph(q)
       }
    )
    
    def topConnectedGraphRoute =
      post { path("topConnectedGraph") { entity(as[TopConnectedQuery]) { q => complete {
        topConnectedGraphCache.get(q)
      }}}}
    
    // ----------------------------------------------------------
    
    // this way of combining distances gives a shorter multi-hop distance than just summing the lengths of the edges
    // so we'll slightly favour multi-hop paths over single edges.
    def distance(a: Double, b: Double) = Math.sqrt(a * a + b * b)
    
    @tailrec final def expand(nem: NodeEdgeMap, n: Int, minScore: Double, newIds: Set[Int], nodeDist: Map[Int, Double], edges: Set[GEdge]= Set.empty): (Int, Set[Int], Map[Int, Double], Set[GEdge]) = {
      if (n < 1) (n, newIds, nodeDist, edges) else {
        // TODO: filter out those outside maxEdges shortest? Check that end result is the same.
        val knownSource = newIds.view.flatMap(id => nem.edgesBySource(id)).filter(_.minScore >= minScore) // edges with known node as source
        val nodeDist1 = knownSource.foldLeft(nodeDist) { (z, e) => 
          val d0 = z.get(e.target.nodeId)
          val d1 = distance(z(e.source.nodeId), 1.0/e.totalWeight)
          val d3 = d0.map(Math.min(_, d1)).getOrElse(d1)
          z + (e.target.nodeId -> d3)
        }
        // TODO: filter out those outside maxEdges shortest?
        val knownTarget = newIds.view.flatMap(id => nem.edgesByTarget(id)).filter(_.minScore >= minScore) // edges with known node as target
        val nodeDist2 = knownTarget.foldLeft(nodeDist1) { (z, e) => 
          val d0 = z.get(e.source.nodeId)
          val d1 = distance(z(e.target.nodeId), 1.0/e.totalWeight)
          val d3 = d0.map(Math.min(_, d1)).getOrElse(d1)
          z + (e.source.nodeId -> d3)
        }
        val nIds = nodeDist2.keySet &~ nodeDist.keySet
        // log.debug(s"expand: nIds = $nIds, knownSource = $knownSource, knownTarget = $knownTarget, nodeDist2 = $nodeDist2")
        log.debug("expand: recurse ...")
        expand(nem, n - 1, minScore, nIds, nodeDist2, edges ++ (knownSource ++ knownTarget))
      }
    }
    
    // ----------------------------------------------------------
      
    @Path("graph")
    @ApiOperation(httpMethod = "POST", response = classOf[Graph], value = "graph matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def graph(q: GraphQuery): Graph = {
      log.info(s"graph: q = $q")
      val nem = if (q.includePerson2) nodeEdgeMap else nodeEdgeMap3
      val idToNode = extRefIdToNode(q.includePerson2)
      q.nodeId.map(Set(_)).orElse(q.extRefId.map(id => idToNode(id).map(_.nodeId).toSet)).map { nodeIds =>
        log.debug(s"graph: nodeIds = ${nodeIds}")
        val (_, _, nodeDist, edges) = expand(nem, q.maxHops, q.minScore, nodeIds, nodeIds.map(_ -> 0.0).toMap)
        log.debug(s"graph: got ${edges.size} edges")
        // take the closest maxEdges
        val topEdges = Util.bottom(q.maxEdges, edges.iterator)(Ordering.by(e => Math.max(nodeDist(e.source.nodeId), nodeDist(e.target.nodeId))))
        log.debug(s"graph: got top edges")
        val topNodes = (topEdges.view.map(_.source) ++ topEdges.view.map(_.target)).toSet.toList
        log.debug(s"graph: got top nodes")
        Graph(topNodes, topEdges map toGrEdge)
      }.getOrElse {
        log.info(s"graph: no nodes match query q = $q")
        Graph(List.empty, List.empty)
      }
    }
    
    val graphCache = CacheBuilder.newBuilder.maximumSize(cacheSize).build(
       new CacheLoader[GraphQuery, Graph] {
         def load(q: GraphQuery) = graph(q)
       }
    )
    
    def graphRoute =
      post { path("graph") { entity(as[GraphQuery]) { q => complete {
        graphCache.get(q)
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