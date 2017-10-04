package au.csiro.data61.dataFusion.tika

import java.io.{ BufferedWriter, File, FileInputStream, FileOutputStream, InputStream, OutputStreamWriter }
import java.net.URL

import scala.collection.concurrent.TrieMap
import scala.io.Source
import scala.language.postfixOps

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.Doc
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.docFormat
import au.csiro.data61.dataFusion.common.Data.META_EN_SCORE
import au.csiro.data61.dataFusion.common.Parallel.{ bufWriter, doParallel }
import resource.managed
import spray.json.{ pimpAny, pimpString }

object Main {
  private val log = Logger(getClass)
  
  /**
   * reprocess json, resetting englishScore in metadata or id.
   */
  def resetEnglishScoreId(cliOption: CliOption) = {
    for (w <- managed(bufWriter(cliOption.output))) {
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
          d.meta + (META_EN_SCORE -> TikaUtil.englishScore(c).toString)
        }.getOrElse(d.meta - META_EN_SCORE)
        
        val embedded = d.embedded.map { e =>
          val meta = e.content.map { c =>
            e.meta + (META_EN_SCORE -> TikaUtil.englishScore(c).toString)
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
  
  /**
   * if path starts with "http:" use HTTP GET else read local file.
   */
  def inputStream(path: String) : InputStream = {
    if (path startsWith "http:") new URL(path).openStream else new FileInputStream(path)
  }
  
  // modified from dataFusion-ner cliNer
  def cliTika(cliOption: CliOption) = {
    import scala.io.Source
    
    val tikaUtil = new TikaUtil(cliOption)
            
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
          logLongerThanMinute("cliTika: logThread")
          Thread.sleep(oneMinute)
        }
      }
    }
    logThread.setDaemon(true)
    logThread.start
      
    for (w <- managed(bufWriter(cliOption.output))) {
      
      var id = cliOption.startId
      val in = Source.fromInputStream(System.in, "UTF-8").getLines.map { path =>
        if (id % 1000 == 0) log.info(s"cliTika.run() in: next id $id")
        val x = (path, id)
        id += 1
        x
      }
      
      def work(pathIdx: (String, Long)): Doc = {
        val path = pathIdx._1
        inProgress += path -> System.currentTimeMillis
        try tikaUtil.tika(inputStream(path), path, pathIdx._2) // stream opened/closed in parseTextMeta
        finally inProgress.remove(path)
      }
      
      def out(d: Doc): Unit = { 
        w.write(d.toJson.compactPrint)
        w.write('\n')
      }
      
      doParallel(in, work, out, ("done", 0L), Doc(0, None, Map.empty, "done", List.empty, List.empty), cliOption.numWorkers)
      log.info(s"cliTika: complete, next id would be ${id}")
    }
    t0 = logDone
    // logThread.join // not necessary with daemon thread, can take up to 1 min to wake up
  }
  
  case class CliOption(output: File, startId: Long, numWorkers: Int, pdfOcrStrategy: String, pdfExtractInlineImages: Boolean, ocrImagePreprocess: Boolean, ocrImPreMaxTifSize: Long, ocrImageDeskew: Boolean, ocrTimeout: Int, ocrResize: Int, ocrPreserveInterwordSpacing: Boolean, resetEnglishScore: Boolean, resetId: Boolean)
  val defaultCliOption = CliOption(new File("tika.json"), 0L, Runtime.getRuntime.availableProcessors, "no_ocr", true, true, 5000000L, false, 300, 200, true, false, false)

  def initSystemProperties: Unit = {
    // https://pdfbox.apache.org/2.0/migration.html#pdf-rendering
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    System.setProperty("org.apache.pdfbox.rendering.UsePureJavaCMYKConversion", "true")

    // On CentOS had: java.lang.NoClassDefFoundError: Could not initialize class sun.awt.X11GraphicsEnvironment
    System.setProperty("java.awt.headless", "true")
  }
  
  def main(args: Array[String]): Unit = {
    initSystemProperties
    
    val parser = new scopt.OptionParser[CliOption]("dataFusion-tika") {
      head("dataFusion-tika", "0.x")
      note("Tika text and metadata extraction CLI. Stdin contains local file paths or http URL's, one per line.")
      opt[File]("output") action { (v, c) =>
        c.copy(output = v)
      } text (s"output JSON file (default ${defaultCliOption.output.getPath})")
      opt[Long]("startId") action { (v, c) =>
        c.copy(startId = v)
      } text (s"id's allocated incrementally starting with this value (default ${defaultCliOption.startId})")
      opt[Int]("numWorkers") action { (v, c) =>
        c.copy(numWorkers = v)
      } text (s"numWorkers (default ${defaultCliOption.numWorkers} the number of CPUs)")
      opt[String]("pdfOcrStrategy") action { (v, c) =>
        c.copy(pdfOcrStrategy = v)
      } text (s"pdfOcrStrategy = no_ocr|ocr_only|ocr_and_text (default ${defaultCliOption.pdfOcrStrategy}). no_ocr means use the text in the PDF, but still OCR embedded images. ocr_only means render the whole page (text and images) as an image and OCR that, otherwise ignoring the text in the PDF.")
      opt[Boolean]("pdfExtractInlineImages") action { (v, c) =>
        c.copy(pdfExtractInlineImages = v)
      } text (s"whether to extract and OCR inline images in PDF (default ${defaultCliOption.pdfExtractInlineImages})")
      opt[Boolean]("ocrImagePreprocess") action { (v, c) =>
        c.copy(ocrImagePreprocess = v)
      } text (s"whether to preprocess images with ImageMagik prior to OCR (default ${defaultCliOption.ocrImagePreprocess})")
      // ocrImPreMaxTifSize
      opt[Long]("ocrImPreMaxTifSize") action { (v, c) =>
        c.copy(ocrImPreMaxTifSize = v)
      } text (s"max TIF image size to preprocess (bytes, default ${defaultCliOption.ocrImPreMaxTifSize})")
      opt[Boolean]("ocrImageDeskew") action { (v, c) =>
        c.copy(ocrImageDeskew = v)
      } text (s"whether to determine image skew using rotation.py so ImageMagik can deskew (can be very slow, default ${defaultCliOption.ocrImageDeskew})")
      opt[Int]("ocrTimeout") action { (v, c) =>
        c.copy(ocrTimeout = v)
      } text (s"ocr timeout secs, (default ${defaultCliOption.ocrTimeout})")
      opt[Int]("ocrResize") action { (v, c) =>
        c.copy(ocrResize = v)
      } text (s"resize image to ocrResize% of original prior to OCR (too large can be very slow, default ${defaultCliOption.ocrResize})")
      opt[Boolean]("ocrPreserveInterwordSpacing") action { (v, c) =>
        c.copy(ocrPreserveInterwordSpacing = v)
      } text (s"whether OCR should preserve interword spacing (default ${defaultCliOption.ocrPreserveInterwordSpacing})")
      opt[Unit]("resetEnglishScore") action { (_, c) =>
        c.copy(resetEnglishScore = true)
      } text (s"reset englishScore in metdata (to reprocess after a change to the scoring, default ${defaultCliOption.resetEnglishScore})")
      opt[Unit]("resetId") action { (_, c) =>
        c.copy(resetId = true)
      } text (s"reset id (to reprocess after an incorrect startId, default ${defaultCliOption.resetId})")
      help("help") text ("prints this usage text")
    }
    
    for (c <- parser.parse(args, defaultCliOption)) {
      if (c.resetEnglishScore || c.resetId) resetEnglishScoreId(c) else cliTika(c)
    }
  }
  
}