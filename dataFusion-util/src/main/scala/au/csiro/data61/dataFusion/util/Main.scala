package au.csiro.data61.dataFusion.util

import java.io.{ File, FileInputStream, InputStream }

import scala.io.Source
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, EMB_IDX_MAIN, IdEmbIdx }
import au.csiro.data61.dataFusion.common.Data.{ LPosDoc, META_EN_SCORE, Ner, PHits, PosInfo }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.{ docFormat, pHitsCodec }
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Util.{ bufWriter, englishScore }
import resource.managed
import spray.json.{ pimpAny, pimpString }

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(hits: Option[File], output: Option[File], startId: Long, resetEnglishScore: Boolean, resetId: Boolean, numWorkers: Int)
  
  val defaultCliOption = CliOption(None, None, 0L, false, false, Runtime.getRuntime.availableProcessors)
  val defGazOut = "gaz.json"
  val defResetOut = "reset.json"
  val parser = new scopt.OptionParser[CliOption]("util") {
    head("util", "0.x")
    opt[File]("hits") action { (v, c) =>
      c.copy(hits = Some(v), output = c.output.orElse(Some(new File(defGazOut))))
    } text (s"read hits from specified file, read tika/ner json from stdin and write it augmented with NER data derived from hits, output defaults to $defGazOut")
    opt[File]("output") action { (v, c) =>
      c.copy(output = Some(v))
    } text (s"output JSON file")
    opt[Long]("startId") action { (v, c) =>
      c.copy(startId = v)
    } text (s"id's for resetId option are reallocated incrementally starting with this value (default ${defaultCliOption.startId})")
    opt[Unit]("resetEnglishScore") action { (_, c) =>
      c.copy(resetEnglishScore = true, output = c.output.orElse(Some(new File(defResetOut))))
    } text (s"reset englishScore in metdata (to reprocess after a change to the scoring), output defaults to $defResetOut")
    opt[Unit]("resetId") action { (_, c) =>
      c.copy(resetId = true, output = c.output.orElse(Some(new File(defResetOut))))
    } text (s"reset id (to reprocess after an incorrect startId), output defaults to $defResetOut")
    help("help") text ("prints this usage text")
  }
    
  def main(args: Array[String]): Unit = {
    try {
      log.info("start")
      parser.parse(args, defaultCliOption).foreach { c => 
        log.info(s"main: cliOptions = $c")
        if (c.hits.isDefined) doHits(c)
        else if (c.resetEnglishScore || c.resetId) resetEnglishScoreId(c)
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

  /**
   * reprocess json, resetting englishScore in metadata or id.
   */
  def resetEnglishScoreId(cliOption: CliOption) = {
    for {
      o <- cliOption.output
      w <- managed(bufWriter(o))
    } {
      var cntIn = 0
      var id = cliOption.startId
      
      val in = Source.fromInputStream(System.in, "UTF-8").getLines.map { json =>
        if (cntIn % 1000 == 0) log.info(s"resetEnglishScoreId in: read $cntIn")
        cntIn += 1
        val d = json.parseJson.convertTo[Doc]
        if (cliOption.resetId) {
          val d2 = d.copy(id = id)
          id += 1
          d2
        } else {
          d
        }
      }
      
      def englishScoreWork(d: Doc): Doc = {
        val meta = d.content.map { c =>
          d.meta + (META_EN_SCORE -> englishScore(c).toString)
        }.getOrElse(d.meta - META_EN_SCORE)
        
        val embedded = d.embedded.map { e =>
          val meta = e.content.map { c =>
            e.meta + (META_EN_SCORE -> englishScore(c).toString)
          }.getOrElse(e.meta - META_EN_SCORE)
          e.copy(meta = meta)
        }
        d.copy(meta = meta, embedded = embedded)
      }
      
      def out(d: Doc): Unit = {
        w.write(d.toJson.compactPrint)
        w.write('\n')
      }
      
      val work: Doc => Doc = if (cliOption.resetEnglishScore) englishScoreWork else identity
      val done = Doc(0, None, Map.empty, "done", List.empty, List.empty)
      doParallel(in, work, out, done, done, cliOption.numWorkers)
      log.info("work complete")
    }
  }

}