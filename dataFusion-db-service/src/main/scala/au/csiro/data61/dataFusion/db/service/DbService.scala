package au.csiro.data61.dataFusion.db.service

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.{ ToResponseMarshallable, ToResponseMarshaller }
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import au.csiro.data61.dataFusion.common.Data, Data.JsonProtocol._
import au.csiro.data61.dataFusion.common.Util
import au.csiro.data61.dataFusion.db.Tables, Tables._
import io.swagger.annotations.{ Api, ApiOperation, ApiResponse, ApiResponses }
import javax.ws.rs.{ Path, PathParam }

// deleted by Eclipse > Source > Organize Imports
// import io.swagger.annotations.ApiResponse

object DbService {
  
    // teach spray.json how to en/decode java.sql.Date
//    val longFormat = implicitly[JsonFormat[Long]]
//    implicit val sqlDateFormat = new JsonFormat[java.sql.Date] {
//      override def read(json: JsValue): java.sql.Date = new java.sql.Date(longFormat.read(json))
//      override def write(obj: java.sql.Date): JsValue = longFormat.write(obj.getTime)
//    }
    
  implicit val docRowCodec = jsonFormat4(DocRow)
  implicit val metaRowCodec = jsonFormat4(MetaRow)
  implicit val nerRowCodec = jsonFormat11(NerRow)
}
import DbService._

@Api(value = "db", description = "read-only access to dataFusion database", produces = "application/json")
@Path("")
class DbService(conf: Config)(implicit val executionContext: ExecutionContext) {
  private val log = Logger(getClass)
  
  val myTables = new Tables {
    val profile = Util.getObject[slick.jdbc.JdbcProfile](conf.getString("db.profile")) // e.g. slick.jdbc.H2Profile or slick.jdbc.PostgresProfile
  }
  import myTables._
  import myTables.profile.api._
  
  val db = Database.forConfig("db", conf)


  
  val qDocById = {
    def q(id: Rep[Long]) = Doc.filter(_.docId === id)
    Compiled(q _)
  }
  
  @Path("doc/{id}")
  @ApiOperation(httpMethod = "GET", response = classOf[Array[DocRow]], responseContainer = "List", value = "Main Doc and embedded Docs")
  def docById(@PathParam("id") id: Long): Future[Seq[DocRow]] = 
    db.run(qDocById(id).result)
      
  def docByIdRoute =
    get { path("doc" / LongNumber) { id => complete {
      docById(id)
    }}}

  
  
  val qMetaById = {
    def q(id: Rep[Long]) = Meta.filter(_.docId === id)
    Compiled(q _)
  }
  
  @Path("meta/{id}")
  @ApiOperation(httpMethod = "GET", response = classOf[Array[MetaRow]], responseContainer = "List", value = "Metadata for main Doc and embedded Docs")
  def metaById(@PathParam("id") id: Long): Future[Seq[MetaRow]] = 
    db.run(qMetaById(id).result)
      
  def metaByIdRoute =
    get { path("meta" / LongNumber) { id => complete {
      metaById(id)
    }}}

  
  
  val qNerById = {
    def q(id: Rep[Long]) = Ner.filter(_.docId === id)
    Compiled(q _)
  }
  
  @Path("ner/{id}")
  @ApiOperation(httpMethod = "GET", response = classOf[Array[NerRow]], responseContainer = "List", value = "Named Entities for main Doc and embedded Docs")
  def nerById(@PathParam("id") id: Long): Future[Seq[NerRow]] = 
    db.run(qNerById(id).result)
      
  def nerByIdRoute =
    get { path("ner" / LongNumber) { id => complete {
      nerById(id)
    }}}

  
  
  val qExtNameById = {
    def q(id: Rep[Long]) = ExtName.filter(_.extNameId === id).map(_.name)
    Compiled(q _)
  }
  val qExtNameLinkById = {
    def q(id: Rep[Long]) = ExtNameLink.filter(_.extNameId === id).map(_.extRefId)
    Compiled(q _)
  }
  
  @Path("extRef/{extNameId}")
  @ApiOperation(httpMethod = "GET", response = classOf[Data.ExtRef], value = "name and ids (from external system) associated with a Named Entity")
  def extRefById(@PathParam("extNameId") id: Long) = {
    val oRef = for {
      onam <- db.run(qExtNameById(id).result.headOption)
      ids <- db.run(qExtNameLinkById(id).result)
    } yield onam.map(Data.ExtRef(_, ids.toList))
    optOrElse(oRef, (StatusCodes.NotFound, ""))
  }

  def extRefByIdRoute =
    get { path("extRef" / LongNumber) { id => complete {
      extRefById(id)
  }}}

  
  
  /** if Some(a) marshall the a, else marshall the orElse */
  def optOrElse[A](x: Future[Option[A]], orElse: => (StatusCode, String))(implicit m: ToResponseMarshaller[A]): ToResponseMarshallable =
    x.map(_.map { s => ToResponseMarshallable(s) }.getOrElse(ToResponseMarshallable(orElse)) )
        
  val routes = docByIdRoute ~ metaByIdRoute ~ nerByIdRoute ~ extRefByIdRoute
}
