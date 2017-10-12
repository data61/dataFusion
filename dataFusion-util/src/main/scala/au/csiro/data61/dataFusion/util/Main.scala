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
import java.io.BufferedWriter
import java.io.InputStream

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(hits: Option[File], output: Option[File])
  
  val defaultCliOption = CliOption(None, None)
  
  val parser = new scopt.OptionParser[CliOption]("util") {
    head("util", "0.x")
    opt[File]("hits") action { (v, c) =>
      c.copy(hits = Some(v), output = c.output.orElse(Some(new File("gaz.json"))))
    } text (s"read tika/ner json on stdin and write same augmented with NER data derived from hits (arg is input hits.json file)")
    opt[File]("output") action { (v, c) =>
      c.copy(output = Some(v))
    } text (s"output JSON file")
    help("help") text ("prints this usage text")
  }
    
  def main(args: Array[String]): Unit = {
    try {
      for {
        c <- parser.parse(args, defaultCliOption)
        hFile <- c.hits
        hIn <- managed(new FileInputStream(hFile))
        oFile <- c.output
        w <- managed(bufWriter(oFile))
        augment = augmentWithHits(hitsMap(hitIter(hIn))) _
        d <- docIter(System.in)
      } {
        w.write(augment(d).toJson.compactPrint)
        w.write('\n')
      }
    } catch {
      case NonFatal(e) => log.error("Main.main:", e)
    }
  }
  
  def hitIter(hIn: InputStream): Iterator[PHits] = Source.fromInputStream(hIn, "UTF-8").getLines.map(_.parseJson.convertTo[PHits])
  def docIter(dIn: InputStream): Iterator[Doc] = Source.fromInputStream(dIn, "UTF-8").getLines.map(_.parseJson.convertTo[Doc])
  
  /** idEmbIdx -> extRefId, score, typ, lposdoc */
  type HitsMap = Map[IdEmbIdx, Seq[(Long, Float, String, LPosDoc)]]
  
  def hitsMap(iter: Iterator[PHits]): HitsMap =
    iter.flatMap { x =>
      x.hits.map(lposdoc => (x.extRefId, x.score, x.typ, lposdoc))
    }.toSeq.groupBy(_._4.idEmbIdx)    
  
  def toNer(content: String, pi: PosInfo, extRefId: Long, score: Double, typ: String) = 
    Ner(pi.posStr, pi.posEnd, pi.offStr, pi.offEnd, score, content.substring(pi.offStr, pi.offEnd), typ, "D61GAZ", Some(extRefId))
      
  def augmentWithHits(hs: HitsMap)(d: Doc): Doc = {
    
    def searchNers(content: Option[String], idEmbIdx: IdEmbIdx): Seq[Ner] = for {
      c <- content.toSeq
      hits <- hs.get(idEmbIdx).toSeq
      (extRefId, score, typ, lposdoc) <- hits
      pi <- lposdoc.posInfos
    } yield toNer(c, pi, extRefId, score, typ)
      
    val ner = d.ner ++ searchNers(d.content, IdEmbIdx(d.id, EMB_IDX_MAIN))
    val embedded = d.embedded.zipWithIndex.map { case (e, embIdx) =>
      val ner = e.ner ++ searchNers(e.content, IdEmbIdx(d.id, embIdx))
      e.copy(ner = ner)
    }
    d.copy(ner = ner, embedded = embedded)
  }
  
}