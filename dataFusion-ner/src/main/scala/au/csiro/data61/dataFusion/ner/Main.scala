package au.csiro.data61.dataFusion.ner

import java.io.OutputStreamWriter

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
import au.csiro.data61.dataFusion.common.Timer
import resource.managed
import spray.json.{ pimpAny, pimpString }
import spray.json.DefaultJsonProtocol._
import scala.util.Try

object Main {
  val log = Logger(getClass)
  
  case class CliOption(all: Boolean, corenlp: Boolean, opennlp: Boolean, mitie: Boolean, preprocess: Boolean, cliNer: Boolean, continueFrom: List[String], exclude: Option[String], blackList: List[String], numWorkers: Int)

  case class Docs(docOuts: List[Doc])
  
  object JsonProtocol {
    implicit val docsCodec = jsonFormat1(Docs)
  }
  import JsonProtocol._
  
  class Impl(val cliOption: CliOption)(implicit ec: ExecutionContext)  {
  
    // parallel initialization
    def tasksIf[A](p: Boolean, t: Seq[Future[A]]) = if (p) t else Seq.empty
    val parallelInit = Future.sequence(
      tasksIf(cliOption.all || cliOption.corenlp, Seq(Future { CoreNLP.English }, Future { CoreNLP.Spanish })) ++
      tasksIf(cliOption.all || cliOption.opennlp, Seq(Future { OpenNLP.English }, Future { OpenNLP.Spanish })) ++
      tasksIf(cliOption.all || cliOption.mitie,   Seq(Future { MITIE.English }, Future { MITIE.Spanish }))
    )
    Await.result(parallelInit, 5 minutes)
    log.info("NLP models loaded")
        
    type nerT = (String, String) => List[Ner] // (lang, content)
    def nerIf(p: Boolean, n: nerT) = if (p) List(n) else List.empty
    val ners = nerIf(cliOption.all || cliOption.corenlp, CoreNLP.ner) ++
      nerIf(cliOption.all || cliOption.opennlp, OpenNLP.ner) ++
      nerIf(cliOption.all || cliOption.mitie, MITIE.ner)
      // ++ List(forever _)
    
    def ordering(a: Ner, b: Ner) = a.posStr < b.posStr || a.posStr == b.posStr && a.posEnd < b.posEnd
        
    // with input with no "."s, CoreNLP is not terminating and consuming 100% CPU!
    val preprocess: String => String =
      if (cliOption.preprocess) _.replaceAll("\n\n+", "\n.\n")
      else identity
   
    def ner(lang: String, in: String): List[Ner] = {
      val in2 = preprocess(in)
      ners.flatMap(_(lang, in2)).sortWith(ordering)
    }
    
    // all we do with this is compare it to "es" for Spanish processing else English
    def getLang(m: Map[String, String]) = m.get(META_LANG_CODE).getOrElse("en")
    
   def langNer(d: Doc): Doc = {
      val lang = getLang(d.meta)
      val ners = d.content.map(c => ner(lang, c)).getOrElse(List.empty)
      val embedded = d.embedded.map { e =>
        val lang = getLang(e.meta)
        val ners = e.content.map(c => ner(lang, c)).getOrElse(List.empty)
        Embedded(e.content, e.meta, ners)
      }
      Doc(d.id, d.content, d.meta, d.path, ners, embedded)
    }
  }
  
  def cliNer(impl: Impl) = {
    // implicit val utf8 = Codec.UTF8
    
    val prevDone = {
      val t = Timer()
      
      val blackList = for {
        bFile <- impl.cliOption.blackList
        path <- Source.fromFile(bFile).getLines
      } yield path
      log.info(s"Loaded ${blackList.size} black listed paths in ${t.elapsedSecs} sec")
      
      val prev = for {
        f <- impl.cliOption.continueFrom.iterator
        json <- Source.fromFile(f).getLines
        d = json.parseJson.convertTo[Doc]
      } yield d.path
      val set = (blackList.toIterator ++ prev).toSet
      log.info(s"Loaded ${set.size} black listed and previously processed paths in ${t.elapsedSecs} sec")
      set
    }

    val processPred: String => Boolean =
      if (impl.cliOption.exclude.isDefined) {
        val re = impl.cliOption.exclude.get.r.unanchored
        s => re.findFirstIn(s).isEmpty && !prevDone.contains(s)
      } else {
        s => !prevDone.contains(s)
      }
        
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
      
    for (w <- managed(new OutputStreamWriter(System.out, "UTF-8"))) {
      
      var cntIn = 0
      var cntProc = 0
      val in = Source.fromInputStream(System.in, "UTF-8").getLines.flatMap { json =>
        if (cntIn % 1000 == 0) log.info(s"NerCli.run() in: read $cntIn, queued for processing $cntProc")
        try {
          val d = json.parseJson.convertTo[Doc]
          cntIn += 1
          if (processPred(d.path)) {
            cntProc += 1
            Iterator.single(d)
          } else Iterator.empty 
        } catch { case NonFatal(e) =>
          log.error(s"Couldn't decode json as Doc: json = $json", e)
          Iterator.empty
        }
      }
      
      def work(d: Doc): Try[Doc] =  {
        inProgress += d.path -> System.currentTimeMillis
        val o = Try(impl.langNer(d))
        inProgress.remove(d.path)
        o
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
  
  def main(args: Array[String]): Unit = {
    val defaultCliOption = CliOption(true, false, false, false, false, false, List.empty, None, List.empty, Runtime.getRuntime.availableProcessors)
    
    val parser = new scopt.OptionParser[CliOption]("dataFusion-ner") {
      head("dataFusion-ner", "0.x")
      note("Named Entity Recognition CLI.\nIf none of corenlp, opennlp, mitie are specified then all are used, otherwise only those specified.")
      opt[Unit]('c', "corenlp") action { (_, c) =>
        c.copy(all = false, corenlp = true)
      } text (s"Use CoreNLP (default ${defaultCliOption.corenlp})")
      opt[Unit]('o', "opennlp") action { (_, c) =>
        c.copy(all = false, opennlp = true)
      } text (s"Use OpenNLP (default ${defaultCliOption.opennlp})")
      opt[Unit]('m', "mitie") action { (_, c) =>
        c.copy(all = false, mitie = true)
      } text (s"Use MITIE (default ${defaultCliOption.mitie})")
      opt[Unit]('p', "preprocess") action { (_, c) =>
        c.copy(preprocess = true)
      } text (s"Preprocess text by adding `.` between consecutive new lines (default ${defaultCliOption.preprocess})")
      opt[String]('f', "continueFrom").optional().unbounded() action { (v, c) =>
        c.copy(continueFrom = v :: c.continueFrom)
      } text (s"Path to output from previous cliNer run, to carry on where it left off, option can be repeated (default ${defaultCliOption.continueFrom})")
      opt[String]('x', "exclude") action { (v, c) =>
        c.copy(exclude = Some(v))
      } text (s"regex to match paths which are not to be processed (default ${defaultCliOption.exclude})")
      opt[String]('b', "blackList").optional().unbounded() action { (v, c) =>
        c.copy(blackList = v :: c.blackList)
      } text (s"File containing paths which are not to be processed, one per line, option can be repeated (default ${defaultCliOption.blackList})")
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