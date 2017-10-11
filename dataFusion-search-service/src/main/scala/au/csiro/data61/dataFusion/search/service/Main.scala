package au.csiro.data61.dataFusion.search.service

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.reflect.runtime.universe.typeOf
import scala.util.control.NonFatal

import com.github.swagger.akka.{ HasActorSystem, SwaggerHttpService }
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.pHitsCodec
import au.csiro.data61.dataFusion.common.Data.PHits
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.DocSearch.DHits
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.DocSearch.JsonProtocol.dHitsCodec
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.JsonProtocol.queryCodec
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.MetaSearch.JsonProtocol.mHitsCodec
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.MetaSearch.MHits
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.NerSearch.JsonProtocol.nHitsCodec
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.NerSearch.NHits
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.PosDocSearch.{ PMultiHits, PosMultiQuery, PosQuery }
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.PosDocSearch.JsonProtocol.{ pMultiHitsCodec, posMultiQueryCodec, posQueryCodec }
import au.csiro.data61.dataFusion.search.DataFusionLucene.DFSearching.Query
import au.csiro.data61.dataFusion.search.Search.{ DocSearcher, MetaSearcher, NerSearcher, PosDocSearcher }
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import io.swagger.annotations.{ Api, ApiOperation }
import javax.ws.rs.{ Consumes, Path }
import javax.ws.rs.core.MediaType

// deleted by Organise Imports:
// import javax.ws.rs.core.MediaType

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(posQuery: String, slop: Int)
    
  @Api(value = "search", description = "lucene search", produces = "application/json")
  @Path("")
  class SearchService(cliOption: CliOption)(implicit val ec: ExecutionContext) {
    
    @Path("doc/search")
    @ApiOperation(httpMethod = "POST", response = classOf[DHits], value = "search hits matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def docSearch(q: Query): DHits = DocSearcher.search(q)
    
    def docSearchRoute =
      post { path("doc/search") { entity(as[Query]) { q => complete {
        docSearch(q)
      }}}}
    
    // ----------------------------------------------------------
    
    @Path("pos/search")
    @ApiOperation(httpMethod = "POST", response = classOf[PHits], value = "search hits matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def posSearch(q: PosQuery): PHits = PosDocSearcher.search(cliOption.slop, q)
    
    def posSearchRoute =
      post { path("pos/search") { entity(as[PosQuery]) { q => complete {
        posSearch(q)
      }}}}
  
    // ----------------------------------------------------------
    
    @Path("pos/multiSearch")
    @ApiOperation(httpMethod = "POST", response = classOf[PMultiHits], value = "search hits matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def posMultiSearch(qs: PosMultiQuery): PMultiHits = PosDocSearcher.multiSearch(cliOption.slop, qs)
    
    def posMultiSearchRoute =
      post { path("pos/multiSearch") { entity(as[PosMultiQuery]) { qs => complete {
        posMultiSearch(qs)
      }}}}
    
    // ----------------------------------------------------------
    
    @Path("meta/search")
    @ApiOperation(httpMethod = "POST", response = classOf[MHits], value = "search hits matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def metaSearch(q: Query): MHits = MetaSearcher.search(q)
    
    def metaSearchRoute =
      post { path("meta/search") { entity(as[Query]) { q => complete {
        metaSearch(q)
      }}}}
    
    // ----------------------------------------------------------
    
    @Path("ner/search")
    @ApiOperation(httpMethod = "POST", response = classOf[NHits], value = "search hits matching the query")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def nerSearch(q: Query): NHits = NerSearcher.search(q)
    
    def nerSearchRoute =
      post { path("ner/search") { entity(as[Query]) { q => complete {
        nerSearch(q)
      }}}}
    
    // ----------------------------------------------------------
    
    val routes = 
      docSearchRoute ~
      posSearchRoute ~
      posMultiSearchRoute ~
      metaSearchRoute ~
      nerSearchRoute  
  }

  def swaggerService(hst: String, prt: Int)(implicit s: ActorSystem, m: ActorMaterializer) = new SwaggerHttpService with HasActorSystem {
    override implicit val actorSystem = s
    override implicit val materializer = m
    override val apiTypes = Seq(typeOf[SearchService])
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
    
    implicit val system = ActorSystem("searchActorSystem")
    implicit val exec = system.dispatcher
    implicit val materializer = ActorMaterializer()
    
    val routes = cors() {
      new SearchService(c).routes ~ 
      swaggerService(host, port).routes
    }
    Http().bindAndHandle(routes, host, port)
    log.info(s"""starting server at: http://${host}:${port}
Test with:
  curl --header 'Content-Type: application/json' http://${host}:${port}/api-docs/swagger.json
""")
  }

  val defaultCliOption = CliOption("unord", 0)
  
  val parser = new scopt.OptionParser[CliOption]("search-service") {
    head("search-service", "0.x")
    note("Run Lucene search web service unless one of --indexer, --docFreq or --filterQuery is specified.")
    opt[String]('p', "posQuery") action { (v, c) =>
      c.copy(posQuery = v)
    } text (s"position query uses ord | unord query, (default ${defaultCliOption.posQuery})")
    opt[Int]('s', "slop") action { (v, c) =>
      c.copy(slop = v)
    } text (s"slop for posQuery, (default ${defaultCliOption.slop})")
    help("help") text ("prints this usage text")
  }
  
  def main(args: Array[String]): Unit = {
    try {
      parser.parse(args, defaultCliOption) foreach start
    } catch {
      case NonFatal(e) => log.error("Main.main:", e)
    }
  }
  
}