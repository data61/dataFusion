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
  
  case class CliOption(hits: Option[File], output: Option[File], numWorkers: Int)
  
  val defaultCliOption = CliOption(None, None, Runtime.getRuntime.availableProcessors)
  val defGazOut = "gaz.json"
  val parser = new scopt.OptionParser[CliOption]("util") {
    head("util", "0.x")
    opt[File]("hits") action { (v, c) =>
      c.copy(hits = Some(v), output = c.output.orElse(Some(new File(defGazOut))))
    } text (s"read hits from specified file, read tika/ner json from stdin and write it augmented with NER data derived from hits to output file (defaults to $defGazOut)")
    opt[File]("output") action { (v, c) =>
      c.copy(output = Some(v))
    } text (s"output JSON file")
    help("help") text ("prints this usage text")
  }
    
  def main(args: Array[String]): Unit = {
    try {
      log.info("start")
      parser.parse(args, defaultCliOption).foreach { c => 
        log.info(s"main: cliOptions = $c")
        if (c.hits.isDefined) doHits(c)
        else log.info("Nothing to do. Try --help")
      log.info("complete")
      }
    } catch {
      case NonFatal(e) => log.error("Main.main:", e)
    }
  }
  
  def doHits(c: CliOption) = {
    for {
      hFile <- c.hits
      hIn <- managed(new FileInputStream(hFile))
      oFile <- c.output
      w <- managed(bufWriter(oFile))
    } {
      val hMap = hitsMap(hitIter(hIn))
      log.info(s"doHits: loaded hits for ${hMap.size} docs/embedded docs")
      val augment: Doc => Doc = augmentWithHits(hMap)

      val in = Source.fromInputStream(System.in, "UTF-8").getLines
      val work: String => String = s => augment(s.parseJson.convertTo[Doc]).toJson.compactPrint
      val out: String => Unit = json => {
        w.write(json)
        w.write('\n')
      }
      doParallel(in, work, out, "done", "done", c.numWorkers)
    }
  }
  
  def hitIter(hIn: InputStream): Iterator[PHits] = Source.fromInputStream(hIn, "UTF-8").getLines.map(_.parseJson.convertTo[PHits])
  
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