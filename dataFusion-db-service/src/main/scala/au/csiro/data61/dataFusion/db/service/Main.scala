package au.csiro.data61.dataFusion.db.service

import scala.language.postfixOps
import scala.reflect.runtime.universe.typeOf

import com.github.swagger.akka.{ HasActorSystem, SwaggerHttpService }
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(create: Boolean)
  val defaultCliOption = CliOption(false)
  
  val parser = new scopt.OptionParser[CliOption]("db-service") {
    head("db-service", "0.x")
    note("Web service providing read-only database access.")
    opt[Unit]('c', "create") action { (_, c) =>
      c.copy(create = true)
    } text (s"Create the database schema e.g. for non-prod H2 database (default ${defaultCliOption.create})")
    help("help") text ("prints this usage text")
  }
  
  val conf = ConfigFactory.load
  val host = conf.getString("http.host")
  val port = conf.getInt("http.port")
  
  implicit val system = ActorSystem("dbActorSystem")
  implicit val exec = system.dispatcher
  implicit val materializer = ActorMaterializer()
  
  def main(args: Array[String]): Unit = {
    for (c <- parser.parse(args, defaultCliOption)) {
      val dbService = new DbService(conf)
      if (c.create) dbService.create
      start(dbService)
    }
  }
    
  def swaggerService(hst: String, prt: Int)(implicit s: ActorSystem, m: ActorMaterializer) = new SwaggerHttpService with HasActorSystem {
    override implicit val actorSystem = s
    override implicit val materializer = m
    override val apiTypes = Seq(typeOf[DbService])
    // override def swaggerConfig = new Swagger().basePath(prependSlashIfNecessary(basePath)) // don't specify protocol://host basePath
    override val host = s"${hst}:${prt}" // the url of your api, not swagger's json endpoint
    override val basePath = "/"          // the basePath for the API you are exposing
    override val info = new io.swagger.models.Info()                    // provides license and other description details
    override val apiDocsPath = "api-docs"   // http://host:port/api-docs/swagger.json
  }
  
  def start(dbService: DbService) = {
    val routes = cors() {
      dbService.routes ~ 
      swaggerService(host, port).routes
    }
    Http().bindAndHandle(routes, host, port)
    log.info(s"""starting server at: http://${host}:${port}
Test with:
  curl --header 'Content-Type: application/json' http://${host}:${port}/api-docs/swagger.json
  curl --header 'Content-Type: application/json' http://${host}:${port}/tikaMain/1
""")
  }
  
}