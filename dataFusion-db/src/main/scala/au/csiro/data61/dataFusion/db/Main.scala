package au.csiro.data61.dataFusion.db

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.language.{ implicitConversions, postfixOps }

import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import com.typesafe.scalalogging.Logger

import Tables.{ DocRow, ExtNameLinkRow, ExtNameRow, MetaRow, NerRow }
import au.csiro.data61.dataFusion.common.Data
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.docFormat
import au.csiro.data61.dataFusion.common.Util
import resource.managed
import spray.json.pimpString


object Main {
  private val log = Logger(getClass)
  val conf = ConfigFactory.load
  def cg(k: String) = conf.getString(k)
  
  case class CliOption(drop: Boolean, create: Boolean, load: Boolean, slickGen: Boolean, dburl: String, profile: String, driver: String, user: String, password: String)
  val defaultCliOption = CliOption(false, false, false, false, cg("db.url"), cg("db.profile"), cg("db.driver"), cg("db.properties.user"), cg("db.properties.password"))

  def main(args: Array[String]): Unit = {
     val parser = new scopt.OptionParser[CliOption]("db") {
      head("db", "0.x")
      note("Slick binding generator and database loader.")
      opt[Unit]("drop") action { (_, c) =>
        c.copy(drop = true)
      } text (s"Drop database schema, default ${defaultCliOption.drop}")
      opt[Unit]("create") action { (_, c) =>
        c.copy(create = true)
      } text (s"Create database schema, default ${defaultCliOption.create}")
      opt[Unit]("load") action { (_, c) =>
        c.copy(load = true)
      } text (s"Drop and recreate database schema and load tables from JSON on stdin, default ${defaultCliOption.load}")
      opt[Unit]("slickGen") action { (_, c) =>
        c.copy(slickGen = true)
      } text (s"Create Slick binding (Tables.scala) from existing database tables, default ${defaultCliOption.slickGen}")
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
      if (c.slickGen) {
        slick.codegen.SourceCodeGenerator.main(Array(c.profile, c.driver, c.dburl, "target/generated", "au.csiro.data61.dataFusion.db", c.user, c.password))
        log.info("Slick binding generated at target/generated/au/csiro/data61/dataFusion/db/Tables.scala")
      } else if (c.drop || c.create || c.load) {
        dbStuff(c)
        log.info("dbStuff() complete")
      }
    }      
  }
  
  val myTables = new Tables {
    val profile = Util.getObject[slick.jdbc.JdbcProfile](conf.getString("db.profile")) // e.g. slick.jdbc.H2Profile or slick.jdbc.PostgresProfile
  }
  import myTables._
  import myTables.profile.api._
    
  def dbStuff(c: CliOption) = {
    val conf2 = conf.withValue("db.url", ConfigValueFactory.fromAnyRef(c.dburl)) // CliOption overrides
      .withValue("db.driver", ConfigValueFactory.fromAnyRef(c.driver))
      .withValue("db.properties.user", ConfigValueFactory.fromAnyRef(c.user))
      .withValue("db.properties.password", ConfigValueFactory.fromAnyRef(c.password))
    for (db <- managed(Database.forConfig("db", conf2))) {
      implicit val db2 = db
      if (c.drop) Await.result(db.run(schema.drop), 1 minute)
      if (c.create) Await.result(db.run(schema.create), 1 minute)
      if (c.load) doLoad
    }
  }
  
  // (name, typ) -> extNameId
  val idMap = mutable.HashMap[(String, String), Long]()
  
  var _id = 0L
  def nextId = {
    _id += 1L
    _id
  }
    
  def dbDocAction(d: Data.Doc)(implicit db: Database) = {
    val (extNameRows, extNameLinkRows) = {
      val x = for {
        n <- d.ner ++ d.embedded.flatMap { e => e.ner }
        r <- n.extRef
        k = (r.name, n.typ)
        _ <- Some(()) if !idMap.contains(k)
        id = nextId
        _ = idMap += k -> id
      } yield (ExtNameRow(id, r.name, n.typ), r.ids.map(ExtNameLinkRow(id, _)) )
      (x.map(_._1), x.flatMap(_._2))
    }
    
    def extNameId(n: Data.Ner) = n.extRef.flatMap { r => idMap.get((r.name, n.typ)) }
    
    DBIO.seq(
        
      Doc ++= (
        DocRow(d.id, Data.EMB_IDX_MAIN, Some(d.path), d.content)
        +: d.embedded.zipWithIndex.map { case (e, embIdx) => 
          DocRow(d.id, embIdx, None, e.content)
        }
      ),
    
      Meta ++= (
        d.meta.map { case (k, v) => MetaRow(d.id, Data.EMB_IDX_MAIN, k, v) }
        ++ d.embedded.zipWithIndex.flatMap { case (e, embIdx) => 
          e.meta.map { case (k, v) => MetaRow(d.id, embIdx, k, v) }
        }
      ),
      
      ExtName ++= extNameRows,
      
      ExtNameLink ++= extNameLinkRows,
      
      Ner ++= (
        d.ner.map { n => NerRow(d.id, Data.EMB_IDX_MAIN, n.text, n.typ, n.impl, n.score, extNameId(n), n.posStr, n.posEnd, n.offStr, n.offEnd) }
        ++ d.embedded.zipWithIndex.flatMap { case (e, embIdx) => 
          e.ner.map { n => NerRow(d.id, embIdx, n.text, n.typ, n.impl, n.score, extNameId(n), n.posStr, n.posEnd, n.offStr, n.offEnd) }
        }
      )
      
    )
  }
  
  def doLoad(implicit db: Database) = {
    var n = 0
    Source.fromInputStream(System.in, "UTF-8").getLines.map { json =>
      n += 1
      if (n % 100 == 0) log.info(s"doLoad: done $n docs")
      dbDocAction(json.parseJson.convertTo[Data.Doc])
    }
    .grouped(10)
    .foreach { s => 
      Await.result(db.run(DBIO.sequence(s)), 5 minute)
    }
  }

}