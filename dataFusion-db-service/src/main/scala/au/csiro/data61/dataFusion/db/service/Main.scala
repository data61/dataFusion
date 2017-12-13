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
import com.typesafe.config.ConfigValueFactory

object Main {
  private val log = Logger(getClass)
  
  val conf = ConfigFactory.load
  def cg(k: String) = conf.getString(k)
  def cgi(k: String) = conf.getInt(k)
  
  case class CliOption(host: String, port: Int, dburl: String, profile: String, driver: String, user: String, password: String)
  val defaultCliOption = CliOption(cg("http.host"), cgi("http.port"), cg("db.url"), cg("db.profile"), cg("db.driver"), cg("db.properties.user"), cg("db.properties.password"))

  implicit val system = ActorSystem("dbActorSystem")
  implicit val exec = system.dispatcher
  implicit val materializer = ActorMaterializer()
  
  def main(args: Array[String]): Unit = {
     val parser = new scopt.OptionParser[CliOption]("db-service") {
      head("db-service", "0.x")
      note("web services for read-only access to datafusion database")
      opt[String]("host") action { (v, c) =>
        c.copy(dburl = v)
      } text (s"web service network interface/host/IP address, default ${defaultCliOption.host}")
      opt[Int]("port") action { (v, c) =>
        c.copy(port = v)
      } text (s"web service TCP port, default ${defaultCliOption.port}")
      opt[String]("dburl") action { (v, c) =>
        c.copy(dburl = v)
      } text (s"JDBC database URL, default ${defaultCliOption.dburl}")
      opt[String]("profile") action { (v, c) =>
        c.copy(profile = v)
      } text (s"full class name of Slick profile, default ${defaultCliOption.profile}")
      opt[String]("driver") action { (v, c) =>
        c.copy(driver = v)
      } text (s"full class name of JDBC driver, default ${defaultCliOption.driver}")
      opt[String]("user") action { (v, c) =>
        c.copy(user = v)
      } text (s"database username, default ${defaultCliOption.user}")
      opt[String]("password") action { (v, c) =>
        c.copy(password = v)
      } text (s"database user password, default ${defaultCliOption.password}")
      help("help") text ("prints this usage text")
    }
    for (c <- parser.parse(args, defaultCliOption)) {
      log.info(s"CliOption: $c}")
      val conf2 = conf.withValue("db.url", ConfigValueFactory.fromAnyRef(c.dburl)) // CliOption overrides
        .withValue("db.driver", ConfigValueFactory.fromAnyRef(c.driver))
        .withValue("db.properties.user", ConfigValueFactory.fromAnyRef(c.user))
        .withValue("db.properties.password", ConfigValueFactory.fromAnyRef(c.password))
      val dbService = new DbService(conf2)
      val routes = cors() {
        dbService.routes ~ 
        swaggerService.routes
      }
      Http().bindAndHandle(routes, c.host, c.port)
      log.info(s"""starting server at: http://${c.host}:${c.port}
Test with:
  curl --header 'Content-Type: application/json' http://${c.host}:${c.port}/api-docs/swagger.json
  curl --header 'Content-Type: application/json' http://${c.host}:${c.port}/tikaMain/1
""")
    }
  }
  
  def swaggerService(implicit s: ActorSystem, m: ActorMaterializer) = new SwaggerHttpService with HasActorSystem {
    override implicit val actorSystem = s
    override implicit val materializer = m
    override val apiTypes = Seq(typeOf[DbService])
    override def swaggerConfig = new io.swagger.models.Swagger().basePath(prependSlashIfNecessary(basePath)) // don't specify protocol://host basePath
//    override val host = s"${hst}:${prt}" // the url of your api, not swagger's json endpoint
//    override val basePath = "/"          // the basePath for the API you are exposing
    override val info = new io.swagger.models.Info()                    // provides license and other description details
    override val apiDocsPath = "api-docs"   // http://host:port/api-docs/swagger.json
  }
  
}