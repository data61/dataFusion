package au.csiro.data61.dataFusion.util

import java.io.File

import scala.io.Source
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.Doc
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.docFormat
import au.csiro.data61.dataFusion.common.Data.META_EN_SCORE
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Util.{ bufWriter, englishScore }
import resource.managed
import spray.json.{ pimpAny, pimpString }

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(hits: Option[File], output: Option[File], startId: Long, proximity: Boolean, resetEnglishScore: Boolean, resetId: Boolean, numWorkers: Int)
  
  val defaultCliOption = CliOption(None, None, 0L, false, false, false, Runtime.getRuntime.availableProcessors)
  
  val defGazOut = "gaz.json" // gaz for gazetteer
  val node = "node.json"
  val edge = "edge.json"
  val defProximity = "proximity-" // prefix for node.json & edge.json
  val defResetOut = "reset.json"
  
  val GAZ = "D61GAZ"
  val GAZ2 = "D61GAZ2"
  
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
    opt[Unit]("proximity") action { (_, c) =>
      c.copy(proximity = true, output = c.output.orElse(Some(new File(defProximity))))
    } text (s"create proximity network, output defaults to ${defProximity + node} and ${defProximity + edge}, --output sets the prefix")
    opt[Unit]("resetEnglishScore") action { (_, c) =>
      c.copy(resetEnglishScore = true, output = c.output.orElse(Some(new File(defResetOut))))
    } text (s"reset englishScore in metdata (to reprocess after a change to the scoring), output defaults to $defResetOut")
    opt[Unit]("resetId") action { (_, c) =>
      c.copy(resetId = true, output = c.output.orElse(Some(new File(defResetOut))))
    } text (s"reset id (to reprocess after an incorrect startId), output defaults to $defResetOut")
    opt[Int]("numWorkers") action { (v, c) =>
      c.copy(numWorkers = v)
    } text (s"numWorkers for CLI queries, (default ${defaultCliOption.numWorkers} the number of CPUs)")
    help("help") text ("prints this usage text")
  }
    
  def main(args: Array[String]): Unit = {
    try {
      log.info("start")
      parser.parse(args, defaultCliOption).foreach { c => 
        log.info(s"main: cliOptions = $c")
        if (c.hits.isDefined) Hits.doHits(c)
        else if (c.proximity) Proximity.doProximity(c)
        else if (c.resetEnglishScore || c.resetId) resetEnglishScoreId(c)
        else log.info("Nothing to do. Try --help")
        log.info("complete")
      }
    } catch {
      case NonFatal(e) => log.error("Main.main:", e)
    }
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