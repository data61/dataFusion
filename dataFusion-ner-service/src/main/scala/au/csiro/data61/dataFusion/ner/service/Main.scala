package au.csiro.data61.dataFusion.ner.service

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
import au.csiro.data61.dataFusion.common.Data.Doc
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.docFormat
import au.csiro.data61.dataFusion.ner.Main.{ CliOption, Impl, defaultCliOption }
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import io.swagger.annotations.{ Api, ApiOperation }
import javax.ws.rs.{ Consumes, Path }
import javax.ws.rs.core.MediaType
import spray.json.DefaultJsonProtocol._

object Main {
  val log = Logger(getClass)
  
  case class Docs(docs: List[Doc])
  
  object JsonProtocol {
    implicit val docCodec = jsonFormat1(Docs)
  }
  import JsonProtocol._
    
  
  @Api(value = "ner", description = "ner service", produces = "application/json")
  @Path("")
  class NerService(impl: Impl)  {

    @Path("ner")
    @ApiOperation(httpMethod = "POST", response = classOf[Doc], value = "input augmented with Named Entities")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def ner(d: Doc): Doc = impl.langNer(d)
        
    def nerRoute =
      post { path("langNer") { entity(as[Doc]) { in => complete {
        ner(in)
      }}}}
    
    // ----------------------------------------------------------
  
    @Path("nerMulti")
    @ApiOperation(httpMethod = "POST", response = classOf[Docs], value = "input augmented with Named Entities")
    @Consumes(Array(MediaType.APPLICATION_JSON))
    def nerMulti(d: Docs) = Docs(d.docs.map(impl.langNer))
        
    def nerMultiRoute =
      post { path("langNerMulti") { entity(as[Docs]) { in => complete {
        nerMulti(in)
      }}}}
  
    // ----------------------------------------------------------
  
    val routes = nerRoute ~ nerMultiRoute
  }
  
  def swaggerService(hst: String, prt: Int)(implicit s: ActorSystem, m: ActorMaterializer) = new SwaggerHttpService with HasActorSystem {
    override implicit val actorSystem = s
    override implicit val materializer = m
    override val apiTypes = Seq(typeOf[NerService])
    // override def swaggerConfig = new Swagger().basePath(prependSlashIfNecessary(basePath)) // don't specify protocol://host basePath
    override val host = s"${hst}:${prt}" // the url of your api, not swagger's json endpoint
    override val basePath = "/"          // the basePath for the API you are exposing
    override val info = new com.github.swagger.akka.model.Info()                    // provides license and other description details
    override val apiDocsPath = "api-docs"   // http://host:port/api-docs/swagger.json
  }
  
  def start(impl: Impl) = {
    val conf = ConfigFactory.load
    val host = conf.getString("http.host")
    val port = conf.getInt("http.port")
    
    implicit val system = ActorSystem("nerActorSystem")
    implicit val exec = system.dispatcher
    implicit val materializer = ActorMaterializer()
    
    val routes = cors() {
      new NerService(impl).routes ~ 
      swaggerService(host, port).routes
    }
    
    Http().bindAndHandle(routes, host, port)
    log.info(s"""starting server at: http://${host}:${port}
Test with:
  curl --header 'Content-Type: application/json' http://${host}:${port}/api-docs/swagger.json
""")
  }

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[CliOption]("dataFusion-ner-service") {
      head("dataFusion-ner-service", "0.x")
      note("Named Entity Recognition web service.")
      opt[Boolean]('c', "corenlp") action { (v, c) =>
        c.copy(corenlp = v)
      } text (s"Use CoreNLP (default ${defaultCliOption.corenlp})")
      opt[Boolean]('o', "opennlp") action { (v, c) =>
        c.copy(opennlp = v)
      } text (s"Use OpenNLP (default ${defaultCliOption.opennlp})")
      opt[Boolean]('m', "mitie") action { (v, c) =>
        c.copy(mitie = v)
      } text (s"Use MITIE (default ${defaultCliOption.mitie})")
      opt[Boolean]('p', "preprocess") action { (v, c) =>
        c.copy(preprocess = v)
      } text (s"Preprocess text by adding `.` between consecutive new lines (default ${defaultCliOption.preprocess})")
      help("help") text ("prints this usage text")
    }
    
    for (c <- parser.parse(args, defaultCliOption)) {
      import scala.concurrent.ExecutionContext.Implicits.global // for Impl parallel initialization
      
      val impl = new Impl(c)
      start(impl)
    }
  }
  
}