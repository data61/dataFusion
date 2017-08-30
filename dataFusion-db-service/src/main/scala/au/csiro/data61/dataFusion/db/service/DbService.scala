package au.csiro.data61.dataFusion.db.service

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.{ ToResponseMarshallable, ToResponseMarshaller }
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import au.csiro.data61.dataFusion.common.Data.ClientEdgeCount
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._
import au.csiro.data61.dataFusion.db.Tables, Tables._
import io.swagger.annotations.{ Api, ApiImplicitParam, ApiImplicitParams, ApiModel, ApiOperation, ApiResponse, ApiResponses }
import javax.ws.rs.{ Path, PathParam }
import slick.collection.heterogeneous.HNil
import slick.collection.heterogeneous.syntax.::
import spray.json.{ DefaultJsonProtocol, JsValue, JsonFormat }

// deleted by Eclipse > Source > Organize Imports
// import slick.collection.heterogeneous.syntax.::
// import io.swagger.annotations.{ ApiImplicitParam, ApiResponse }

object DbService {
  
  case class EntityIds(entityIds: List[Long])
  case class Paths(paths: List[String])
    
  case class ClientEdgeCounts(counts: List[ClientEdgeCount])
  
  case class SearchHitsResult(hits: Seq[SearchHitsRow])

  case class TikaMetas(meta: Seq[TikaMetaRow])
  case class TikaNers(ner: Seq[TikaNerRow])
  case class TikaEmbMains(embMain: Seq[TikaEmbMainRow])
  case class TikaEmbMetas(embMeta: Seq[TikaEmbMetaRow])
  case class TikaEmbNers(embNer: Seq[TikaEmbNerRow])
  case class TikaMainAndEmbedded(main: TikaMainRow, embedded: List[TikaEmbMainRow])

  trait DbJsonProtocol extends DefaultJsonProtocol {
    // teach spray.json how to en/decode java.sql.Date
    val longFormat = implicitly[JsonFormat[Long]]
    implicit val sqlDateFormat = new JsonFormat[java.sql.Date] {
      override def read(json: JsValue): java.sql.Date = new java.sql.Date(longFormat.read(json))
      override def write(obj: java.sql.Date): JsValue = longFormat.write(obj.getTime)
    }
    implicit val entityIdsCodec = jsonFormat1(EntityIds)
    implicit val pathsCodec = jsonFormat1(Paths)
    implicit val searchHitCodec = jsonFormat11(SearchHitsRow)
    implicit val tikaMainCodec = jsonFormat4(TikaMainRow)
    implicit val tikaMetaCodec = jsonFormat3(TikaMetaRow)
    implicit val tikaNerCodec = jsonFormat6(TikaNerRow)
    implicit val tikaEmbMainCodec = jsonFormat3(TikaEmbMainRow)
    implicit val tikaEmbMetaCodec = jsonFormat4(TikaEmbMetaRow)
    implicit val tikaEmbNerCodec = jsonFormat7(TikaEmbNerRow)
    implicit val clientEdgeCountsCodec = jsonFormat1(ClientEdgeCounts)
    implicit val searchHitsResultsCodec = jsonFormat1(SearchHitsResult)
    implicit val tikaMetaDataCodec = jsonFormat1(TikaMetas)
    implicit val tikaNersCodec = jsonFormat1(TikaNers)
    implicit val tikaEmbMainsCodec = jsonFormat1(TikaEmbMains)
    implicit val tikaEmbMetasCodec = jsonFormat1(TikaEmbMetas)
    implicit val tikaEmbNersCodec = jsonFormat1(TikaEmbNers)
    implicit val tikaMainAndEmbeddedCodec = jsonFormat2(TikaMainAndEmbedded)
  }
  object DbJsonProtocol extends DbJsonProtocol
  
}
import DbService._
import javax.ws.rs.Consumes
import javax.ws.rs.core.MediaType
import javax.ws.rs.QueryParam

@Api(value = "db", description = "read-only access to dataFusion database", produces = "application/json")
@Path("")
class DbService(conf: Config)(implicit val executionContext: ExecutionContext) {
  private val log = Logger(getClass)
  
  object DbTables extends Tables {
    val schemaName = Option(conf.getString("db.schemaName")).flatMap(n => if (n.isEmpty) None else Some(n))
    val profile: slick.jdbc.JdbcProfile = Util.getObject(conf.getString("db.profile"))
  }
  import DbTables._, profile.api._

  // Demo code seems to prefer `extends ... with ...` inheritance to bring these implicit conversions into scope
  // but when imports will do that seems cleaner and more flexible to me.
  import DbService.DbJsonProtocol._
  
  val db = Database.forConfig("", conf.getConfig("db"))

  def create = Await.result(db.run(schema.create), 15 seconds)
  def drop = Await.result(db.run(schema.drop), 15 seconds)
  
  // ----------------------------------------------------------
  
  val qSearchHitByEntityId = {
    def q(entityId: Rep[Long]) = SearchHits.filter(_.entityId === entityId)
    Compiled(q _)
  }
  
  @Path("client/{entityId}/searchHit")
  @ApiOperation(httpMethod = "GET", response = classOf[SearchHitsResult], value = "SearchHits matching this client")
  def searchHitByEntityId(@PathParam("entityId") entityId: Long): Future[SearchHitsResult] = 
    db.run(qSearchHitByEntityId(entityId).result).map(x => SearchHitsResult(x))
  
  def searchHitByEntityIdRoute =
    get { path("client" / LongNumber / "searchHit") { entityId => complete {
      searchHitByEntityId(entityId)
    }}}
      
  // ----------------------------------------------------------
  
  val qSearchHitByDocId = {
    def q(docId: Rep[Int]) = SearchHits.filter(_.docId === docId)
    Compiled(q _)
  }

  @Path("doc/{docId}/searchHit")
  @ApiOperation(httpMethod = "GET", response = classOf[SearchHitsResult], value = "SearchHits matching this doc")
  def searchHitByDocId(@PathParam("docId") docId: Int): Future[SearchHitsResult] = 
    db.run(qSearchHitByDocId(docId).result).map(x => SearchHitsResult(x))

  def searchHitByDocIdRoute =
    get { path("client" / IntNumber / "searchHit") { docId => complete {
      searchHitByDocId(docId)
    }}}

  // ----------------------------------------------------------
  
  // all client's mentioned in a doc (only those with text at least as long as search - same filter as in network building)
  // TODO: when search fixed remove this filter from here and network building
  val qClientsInDoc = {
    def q(doc: Rep[String]) = for {
      m <- TikaMain if m.path === doc // use TikaMain to look up path because there's no index on SearchHits.path
      h <- SearchHits if h.docId === m.searchId && h.text.length >= h.search.length
    } yield h.entityId
    Compiled(q _)
  }

  /**
   * @return all the clients mentioned in the specified docs
   */
  @Path("clientsInDocs/entityId")
  @ApiOperation(httpMethod = "POST", response = classOf[EntityIds], value = "list of entityIds of clients mentioned in the specified docs")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  def clientsInDocs(paths: Paths): Future[EntityIds] = {
    val a = paths.paths.map { path => 
      db.run(qClientsInDoc(path).result).map(_.flatMap(_.toSeq))
    }
    Future.reduceLeft(a)(_ ++ _).map(s => EntityIds(s.toSet.toList))
  }
  
  def clientsInDocsRoute =
    post { path("clientsInDocs/entityId") { entity(as[Paths]) { paths => complete {
      clientsInDocs(paths)
    }}}}
  
  // ----------------------------------------------------------


//  // number of edges for a client
//  val qEdgeCount = {
//    def q(entityId: Rep[Long]) = GraphEdge.filter(e => e.source === entityId || e.target === entityId).length
//    Compiled(q _)
//  }
//
//  /**
//   * For all the client's mentioned in the specified docs,
//   * return the n client's that have the most connections (edges) over all the docs (not just the specified ones).
//   * @return Seq of up to n * (clientIntrnlId, number of edges) sorted on number of edges descending
//   */
//  @Path("clientsInDocs/topConnected/clientEdgeCount")
//  @ApiOperation(httpMethod = "POST", response = classOf[ClientEdgeCounts], value = "list of ClientEdgeCount of top connected clients mentioned in the specified docs")
//  @Consumes(Array(MediaType.APPLICATION_JSON))
//  def topConnectedClientsInDocs(q: TopClientsQuery): Future[ClientEdgeCounts] = {
//    clientsInDocs(Paths(q.paths)).flatMap { ids => 
//      val x = ids.entityIds.map(id => db.run(qEdgeCount(id).result).map(ClientEdgeCount(id, _)))
//      Future.foldLeft(x)(List.empty[ClientEdgeCount])((z, i) => i :: z)
//        .map(x => ClientEdgeCounts(x.filter(_.numEdges > 0).sortBy(-_.numEdges).take(q.n)))
//    }
//  }
//  
//  def topConnectedClientsInDocsRoute =
//    post { path("clientsInDocs/topConnected/clientEdgeCount") { entity(as[TopClientsQuery]) { q => complete {
//      topConnectedClientsInDocs(q)
//    }}}}
//
//  // ----------------------------------------------------------
//
//  @Path("clientsInDocs/topConnected/clientDetails")
//  @ApiOperation(httpMethod = "POST", response = classOf[TopConnected], value = "list of TopConnectedClientDetails")
//  @Consumes(Array(MediaType.APPLICATION_JSON))
//  def topConnectedClientDetailsInDocs(q: TopClientsQuery): Future[TopConnected] = {
//    topConnectedClientsInDocs(q).flatMap { l => 
//      val a = l.counts.map { case ClientEdgeCount(entityId, numEdges) =>
//        db.run(qClientById(entityId).result.headOption).map(_.map(r => TopConnectedClientDetails(numEdges, toClientRegisterData(r))).toList)
//      }
//      Future.reduceLeft(a)(_ ++ _).map(l => TopConnected(l))
//    }
//  }
//  
//  def topConnectedClientDetailsInDocsRoute =
//    post { path("clientsInDocs/topConnected/clientDetails") { entity(as[TopClientsQuery]) { q => complete {
//      topConnectedClientDetailsInDocs(q)
//    }}}}

  // ----------------------------------------------------------

  val qTikaMainById = {
    def q(id: Rep[Int]) = TikaMain.filter(_.id === id)
    Compiled(q _)
  }
  
  @Path("tikaMain/{id}")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaMainRow], value = "TikaMainRow representation of doc")
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "tikaMain not found")
  ))
  def tikaMainById(@PathParam("id") id: Int): Future[Option[TikaMainRow]] = 
    db.run(qTikaMainById(id).result.headOption)
      
  def tikaMainByIdRoute =
    get { path("tikaMain" / IntNumber) { id => complete {
      optOrElse(tikaMainById(id), (StatusCodes.NotFound, s"tikaMain not found for id = $id"))
    }}}

  // ----------------------------------------------------------

  val qTikaMainBySearchId = {
    def q(searchId: Rep[Int]) = TikaMain.filter(_.searchId === searchId)
    Compiled(q _)
  }

  @Path("tikaMain")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaMainRow], value = "TikaMainRow representation of doc")
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "tikaMain not found")
  ))
  def tikaMainBySearchId(@QueryParam("searchId") searchId: Int): Future[Option[TikaMainRow]] =
    db.run(qTikaMainBySearchId(searchId).result.headOption)
      
  def tikaMainBySearchIdRoute =
    get { path("tikaMain") { parameters("searchId".as[Int]) { searchId => complete {
        optOrElse(tikaMainBySearchId(searchId), (StatusCodes.NotFound, s"tikaMain not found for searchId = $searchId"))
    }}}}

  // ----------------------------------------------------------

  val qTikaMainByPath = {
    def q(path: Rep[String]) = TikaMain.filter(_.path === path)
    Compiled(q _)
  }     

  @Path("tikaMain")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaMainRow], value = "TikaMainRow representation of doc")
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "tikaMain not found")
  ))
  def tikaMainByPath(@QueryParam("path") path: String): Future[Option[TikaMainRow]] = 
    db.run(qTikaMainByPath(path).result.headOption)
  
  def tikaMainByPathRoute =
    get { path("tikaMain") { parameters("path") { path => complete {
        optOrElse(tikaMainByPath(path), (StatusCodes.NotFound, s"tikaMain not found for path = $path"))
      }}}}
 
  // ----------------------------------------------------------

  val qTikaMetaById = {
      def q(id: Rep[Int]) = TikaMeta.filter(_.id === id)
      Compiled(q _)
    }

  @Path("tikaMain/{id}/meta")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaMetas], value = "list of TikaMetaRow for doc")
  def tikaMetaById(@PathParam("id") id: Int): Future[TikaMetas] = 
    db.run(qTikaMetaById(id).result).map(x => TikaMetas(x))
    
  def tikaMetaByIdRoute =
    get { path("tikaMain" / IntNumber / "meta") { id => complete {
      tikaMetaById(id)
    }}}

  // ----------------------------------------------------------

  val qTikaNerById = {
    def q(id: Rep[Int]) = TikaNer.filter(_.id === id)
    Compiled(q _)
  }

  @Path("tikaMain/{id}/ner")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaNers], value = "list of TikaNerRow for doc")
  def tikaNerById(@PathParam("id") id: Int): Future[TikaNers] = 
    db.run(qTikaNerById(id).result).map(x => TikaNers(x))
  
  def tikaNerByIdRoute =
    get { path("tikaMain" / IntNumber / "ner") { id => complete {
      tikaNerById(id)
    }}}

  // ----------------------------------------------------------

  val qTikaEmbMainById = {
    def q(id: Rep[Int]) = TikaEmbMain.filter(_.id === id)
    Compiled(q _)
  }
  
  @Path("tikaMain/{id}/emb")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaEmbMains], value = "list of TikaEmbMainRow for doc")
  def tikaEmbMainById(@PathParam("id") id: Int): Future[TikaEmbMains] = 
    db.run(qTikaEmbMainById(id).result).map(x => TikaEmbMains(x))
  
  def tikaEmbMainByIdRoute =
    path("tikaMain" / IntNumber / "emb") { id => get { complete {
      tikaEmbMainById(id)
    }}}

  // ----------------------------------------------------------

  val qTikaEmbMainByPath = {
    def q(path: Rep[String]) = for {
      m <- TikaMain if m.path === path
      e <- TikaEmbMain if e.id === m.id
    } yield e
    Compiled(q _)
  }

  @Path("tikaMain/emb")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaEmbMains], value = "list of TikaEmbMainRow for doc")
  def tikaEmbMainByPath(@QueryParam("path") path: String): Future[TikaEmbMains] = 
    db.run(qTikaEmbMainByPath(path).result).map(x => TikaEmbMains(x))
  
  def tikaEmbMainByPathRoute =
    get { path("tikaMain/emb") { parameters("path") { path => complete {
      tikaEmbMainByPath(path)
    }}}}

  // ----------------------------------------------------------

  val qTikaEmbMetaById = {
    def q(id: Rep[Int]) = TikaEmbMeta.filter(_.id === id)
    Compiled(q _)
  }

  @Path("tikaMain/{id}/emb/meta")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaEmbMetas], value = "list of TikaEmbMetaRow for doc")
  def tikaEmbMetaById(@PathParam("id") id: Int): Future[TikaEmbMetas] = 
    db.run(qTikaEmbMetaById(id).result).map(x => TikaEmbMetas(x))
  
  def tikaEmbMetaByIdRoute =
    get { path("tikaMain" / IntNumber / "emb/meta") { id => complete {
      tikaEmbMetaById(id)
    }}}

  // ----------------------------------------------------------

  val qTikaEmbNerById = {
    def q(id: Rep[Int]) = TikaEmbNer.filter(_.id === id)
    Compiled(q _)
  }

  @Path("tikaMain/{id}/emb/ner")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaEmbNers], value = "list of TikaEmbNerRow for doc")
  def tikaEmbNerById(@PathParam("id") id: Int): Future[TikaEmbNers] = 
    db.run(qTikaEmbNerById(id).result).map(x => TikaEmbNers(x))

  def tikaEmbNerByIdRoute =
    get { path("tikaMain" / IntNumber / "emb/ner") { id => complete {
      tikaEmbNerById(id)
    }}}

  // ----------------------------------------------------------

  @Path("tikaMainAndEmb")
  @ApiOperation(httpMethod = "GET", response = classOf[TikaMainAndEmbedded], value = "list of TikaMainAndEmbedded for doc")
  def tikaMainAndEmbMain(@QueryParam("path") path: String): Future[Option[TikaMainAndEmbedded]] = {
    for {
      m <- db.run(qTikaMainByPath(path).result.headOption)
      e <- db.run(qTikaEmbMainByPath(path).result)
    } yield m.map(m => TikaMainAndEmbedded(m, e.toList))
  }
  
  def tikaMainAndEmbMainRoute =
    get { path("tikaMainAndEmb") { parameters("path") { path => complete {
      optOrElse(tikaMainAndEmbMain(path), (StatusCodes.NotFound, s"tikaMain not found for path = $path"))
    }}}}

  // ----------------------------------------------------------

  /** if Some(a) marshall the a, else marshall the orElse */
  def optOrElse[A](x: Future[Option[A]], orElse: => (StatusCode, String))(implicit m: ToResponseMarshaller[A]): ToResponseMarshallable =
    x.map(_.map { s => ToResponseMarshallable(s) }.getOrElse(ToResponseMarshallable(orElse)) )
        
  val routes = 
    searchHitByEntityIdRoute ~
    searchHitByDocIdRoute ~
    tikaMainByIdRoute ~
    tikaMainBySearchIdRoute ~
    tikaMainByPathRoute ~
    tikaMetaByIdRoute ~
    tikaNerByIdRoute ~
    tikaEmbMainByIdRoute ~
    tikaEmbMainByPathRoute ~
    tikaEmbMetaByIdRoute ~
    tikaEmbNerByIdRoute ~
    tikaMainAndEmbMainRoute
}
