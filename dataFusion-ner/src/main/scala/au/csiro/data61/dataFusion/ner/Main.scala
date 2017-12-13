package au.csiro.data61.dataFusion.ner

import java.io.File

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.language.postfixOps
import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, Embedded }
import au.csiro.data61.dataFusion.common.Data.{ META_LANG_CODE, Ner }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.docFormat
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Util.bufWriter
import resource.managed
import spray.json.{ pimpAny, pimpString }
import spray.json.DefaultJsonProtocol._

object Main {
  private val log = Logger(getClass)
  
  case class Docs(docOuts: List[Doc])
  
  object JsonProtocol {
    implicit val docsCodec = jsonFormat1(Docs)
  }
  import JsonProtocol._
  
  class Impl(val cliOption: CliOption)(implicit ec: ExecutionContext)  {
  
    // parallel initialization
    def tasksIf[A](p: Boolean, t: => Seq[Future[A]]) = if (p) t else Seq.empty
    val parallelInit = Future.sequence(
      tasksIf(cliOption.corenlp, Seq(Future { CoreNLP.English }/*, Future { CoreNLP.Spanish }*/)) ++
      tasksIf(cliOption.opennlp, Seq(Future { OpenNLP.English }/*, Future { OpenNLP.Spanish }*/)) ++
      tasksIf(cliOption.mitie,   Seq(Future { MITIE.English }/*, Future { MITIE.Spanish }*/))
    )
    Await.result(parallelInit, 5 minutes)
    log.info("NLP models loaded")
        
    type nerT = (String, String) => List[Ner] // (lang, content)
    def nerIf(p: Boolean, n: nerT) = if (p) List(n) else List.empty
    val ners = nerIf(cliOption.corenlp, CoreNLP.ner) ++
      nerIf(cliOption.opennlp, OpenNLP.ner) ++
      nerIf(cliOption.mitie, MITIE.ner)
      // ++ List(forever _)
    
    def ordering(a: Ner, b: Ner) = a.posStr < b.posStr || a.posStr == b.posStr && a.posEnd < b.posEnd
        
    def ner(lang: String, in: String): List[Ner] = ners.flatMap(_(lang, in)).sortWith(ordering)
    
    // all we do with this is compare it to "es" for Spanish processing else English
    def getLang(m: Map[String, String]) = m.get(META_LANG_CODE).getOrElse("en")
    
   def langNer(d: Doc): Doc = {
      val lang = getLang(d.meta)
      val ners = d.content.map(c => ner(lang, c)).getOrElse(List.empty)
      val embedded = d.embedded.map { e =>
        val lang = getLang(e.meta)
        val ners = e.content.map(c => ner(lang, c)).getOrElse(List.empty)
        e.copy(ner = ners ++ e.ner)
      }
      d.copy(ner = ners ++ d.ner, embedded = embedded)
    }
  }
  
  def cliNer(impl: Impl) = {
    // implicit val utf8 = Codec.UTF8
          
    // identify path for which runtime is long, perhaps infinite, so we can add it to black list next run
    val inProgress = TrieMap.empty[String, Long] // path -> start time
    @volatile var t0 = System.currentTimeMillis
    val logDone = -1L
    val oneMinute = 60000L
    
    def logLongerThanMinute(msg: String) = {
      val t1 = System.currentTimeMillis
      if (t0 != logDone && t1 - t0 > oneMinute) {
        val longerThanMinute = (for {
          (p, t) <- inProgress.iterator if t1 - t > 60000L
        } yield p -> (t1 - t) / 60000f).toList
        log.info(s"$msg: In progress for more than a minute: (path, minutes) = $longerThanMinute")
        t0 = t1
      }
    }
      
    val logThread = new Thread {
      override def run = {
        Iterator.continually(t0).takeWhile(_ != logDone).foreach { _ => 
          logLongerThanMinute("logThread")
          Thread.sleep(oneMinute)
        }
      }
    }
    logThread.setDaemon(true)
    logThread.start
      
    for (w <- managed(bufWriter(impl.cliOption.output))) {
      
      var cntIn = 0
      val in = Source.fromInputStream(System.in, "UTF-8").getLines.flatMap { json =>
        if (cntIn % 1000 == 0) log.info(s"NerCli.run() in: read $cntIn")
        try {
          val d = json.parseJson.convertTo[Doc]
          cntIn += 1
          Iterator.single(d)
        } catch { case NonFatal(e) =>
          log.error(s"Couldn't decode json as Doc: json = $json", e)
          Iterator.empty
        }
      }
      
      def work(d: Doc): Doc =  {
        inProgress += d.path -> System.currentTimeMillis
        try impl.langNer(d)
        finally inProgress.remove(d.path)
      }
      
      def out(d: Doc): Unit = {
        w.write(d.toJson.compactPrint)
        w.write('\n')
      }
      val done = Doc(0, None, Map.empty, "done", List.empty, List.empty)
      doParallel(in, work, out, done, done, impl.cliOption.numWorkers)
      log.info("work complete")
    }
    t0 = logDone
    // logThread.join // not necessary with daemon thread, can take up to 1 min to wake up
  }
  
  case class CliOption(output: File, corenlp: Boolean, opennlp: Boolean, mitie: Boolean, numWorkers: Int)
  val defaultCliOption = CliOption(new File("ner.json"), true, true, true, Runtime.getRuntime.availableProcessors)

  def main(args: Array[String]): Unit = {
    
    val parser = new scopt.OptionParser[CliOption]("dataFusion-ner") {
      head("dataFusion-ner", "0.x")
      note("Named Entity Recognition CLI.")
      opt[File]("output") action { (v, c) =>
        c.copy(output = v)
      } text (s"output JSON file, (default ${defaultCliOption.output.getPath})")
      opt[Boolean]('c', "corenlp") action { (v, c) =>
        c.copy(corenlp = v)
      } text (s"Use CoreNLP (default ${defaultCliOption.corenlp})")
      opt[Boolean]('o', "opennlp") action { (v, c) =>
        c.copy(opennlp = v)
      } text (s"Use OpenNLP (default ${defaultCliOption.opennlp})")
      opt[Boolean]('m', "mitie") action { (v, c) =>
        c.copy(mitie = v)
      } text (s"Use MITIE (default ${defaultCliOption.mitie})")
      opt[Int]('n', "numWorkers") action { (v, c) =>
        c.copy(numWorkers = v)
      } text (s"numWorkers (default ${defaultCliOption.numWorkers} the number of CPUs)")
      help("help") text ("prints this usage text")
    }
    
    for (c <- parser.parse(args, defaultCliOption)) {
      import scala.concurrent.ExecutionContext.Implicits.global // for Impl parallel initialization
      
      val impl = new Impl(c)
      cliNer(impl)
    }
  }
  
}