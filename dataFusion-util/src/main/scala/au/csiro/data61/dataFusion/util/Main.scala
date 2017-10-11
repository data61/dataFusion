package au.csiro.data61.dataFusion.util

import java.io.File

import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger
import java.io.FileInputStream
import scala.io.Source
import au.csiro.data61.dataFusion.common.Data.{ PHits, Stats }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.pHitsCodec
import au.csiro.data61.dataFusion.common.Parallel.{ bufWriter, doParallel }
import resource.managed
import spray.json.{ pimpAny, pimpString }
import au.csiro.data61.dataFusion.common.Data._
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(hits: Option[File])
  
  val defaultCliOption = CliOption(None)
  
  val parser = new scopt.OptionParser[CliOption]("util") {
    head("util", "0.x")
    opt[File]("hits") action { (v, c) =>
      c.copy(hits = Some(v))
    } text (s"read tika/ner json on stdin and write to stdout augmented with hits (arg is input hits.json file)")
    help("help") text ("prints this usage text")
  }
    
  def main(args: Array[String]): Unit = {
    try {
      parser.parse(args, defaultCliOption).foreach { c => 
        log.info(s"main: cliOptions = $c")
        if (c.hits.isDefined) augmentWithHits(c.hits.get)
        else log.info("Nothing to do. Try --help")
      }
    } catch {
      case NonFatal(e) => log.error("Main.main:", e)
    }
  }
  
  // TODO: the getOrElse case should log an error
  def toNer(content: String, pi: PosInfo, extRefId: Long, score: Double, typ: String) = 
    Ner(pi.posStr, pi.posEnd, pi.offStr, pi.offEnd, score, content.substring(pi.offStr, pi.offEnd), typ, "D61GAZ", Some(extRefId))
      
  def augmentWithHits(hits: File): Unit = {
    // idEmbIdx -> extRefId, score, typ, lposdoc
    val hs = Source.fromInputStream(new FileInputStream(hits), "UTF-8").getLines.flatMap { json => 
      val x = json.parseJson.convertTo[PHits]
      x.hits.map(lposdoc => (x.extRefId, x.score, x.typ, lposdoc))
    }.toSeq.groupBy(_._4.idEmbIdx)
    
    def searchNers(d: Doc, idEmbIdx: IdEmbIdx) = for {
      content <- d.content.toSeq
      hits <- hs.get(idEmbIdx).toSeq
      (extRefId, score, typ, lposdoc) <- hits
      pi <- lposdoc.posInfos
    } yield toNer(content, pi, extRefId, score, typ)
      
    for (json <- Source.fromInputStream(System.in, "UTF-8").getLines) {
      val d = json.parseJson.convertTo[Doc]
      val ner = d.ner ++ searchNers(d, IdEmbIdx(d.id, EMB_IDX_MAIN))
      val embedded = d.embedded.zipWithIndex.map { case (e, embIdx) =>
        val ner = e.ner ++ searchNers(d, IdEmbIdx(d.id, embIdx))
        e.copy(ner = ner)
      }
      // TODO: write with UTF-8 encoding
      System.out.println(d.copy(ner = ner, embedded = embedded).toJson.compactPrint)
    }
  }
  
}