package au.csiro.data61.dataFusion.tika

import java.io.{ FilterInputStream, InputStream }
import scala.util.Failure
import org.scalatest.{ FlatSpec, Matchers }
import com.typesafe.scalalogging.Logger

class TikaTest extends FlatSpec with Matchers {
  private val log = Logger(getClass)
  
  "Tika" should "extract 1 page of PDF" in {
    val path = "/exampleData/PDF002.pdf" // born digital, has logo image with no text
    val docIn = TikaUtil.tika(getClass.getResourceAsStream(path), path, 0L).get // assume no error
    // log.debug(s"docIn = ${docIn}")
    docIn.content.map(_.size).getOrElse(0) > 100 should be(true) // born digital text
    docIn.embedded.size should be(1) // has 1 embedded doc - the logo
    docIn.embedded(0).content.isEmpty should be(true) // for which we get no text
  }
  
  "Tika" should "extract 5 pages of PDF" in {
    val path = "/exampleData/PDF003.pdf" // scanned doc
    val docIn = TikaUtil.tika(getClass.getResourceAsStream(path), path, 0L).get
    // log.debug(s"docIn = ${docIn}")
    docIn.content.map(_.size).getOrElse(0) > 100 should be(true) // text OCR by scanner
    docIn.embedded.size should be(5) // 5 embedded page images
    docIn.embedded.foreach(_.content.map(_.size).getOrElse(0) > 100 should be(true)) // tesseract got text from each page
  }
  
  "Tika" should "extract from good Excel" in {
    val path = "/exampleData/xls001.xls"
    val d = TikaUtil.tika(getClass.getResourceAsStream(path), path, 0L).get
    // log.debug(s"d = $d")
    d.content.get.contains("Principality of Liechtenstein") should be(true)
    d.meta.get("Content-Type") should be(Some("application/vnd.ms-excel"))
  }
    
  "Tika" should "convert good Excel to opendocument.spreadsheet (only when explicitly asked to) and extract" in {
    val path = "/exampleData/xls001.xls"
    val d = TikaUtil.convertAndParseDoc(getClass.getResourceAsStream(path), path, 0L).get
    // log.debug(s"d = $d")
    d.content.get.contains("Principality of Liechtenstein") should be(true)
    d.meta.get("Content-Type") should be(Some("application/vnd.oasis.opendocument.spreadsheet"))
  }
    
  "Tika" should "convert bad Excel to opendocument.spreadsheet (when not explicitly asked to) and extract" in {
    // test Excel file is attachment from: https://bz.apache.org/bugzilla/show_bug.cgi?id=57104
    val path = "/exampleData/data-prob-2-12.XLS"
    val d = TikaUtil.tika(getClass.getResourceAsStream(path), path, 0L).get
    // log.debug(s"d = $d")
    d.content.get.contains("562.03") should be(true)
    d.meta.get("Content-Type") should be(Some("application/vnd.oasis.opendocument.spreadsheet"))
  }
  
  /**
   * Truncate `src` after reading `take` bytes.
   */
  class TakeInputStream(src: InputStream, take: Int) extends FilterInputStream(src) {
    private var n = 0

    override def read: Int = {
      if (n < take) {
        n += 1
        src.read
      } else -1
    }
    
    override def read(buf: Array[Byte], off: Int, len: Int): Int = {
      val c = Math.min(take - n, len)
      if (c > 0) {
        val k = src.read(buf, off, c)
        n += k
        k
      } else -1
    }
    
    override def skip(len: Long): Long = {
      n += len.toInt
      src.skip(len)
    }
  }
  
  "Tika" should "complain on bad input" in {
    val path = "/exampleData/PDF002.pdf" // born digital, has logo image with no text
    TikaUtil.tika(new TakeInputStream(getClass.getResourceAsStream(path), 90000), path, 0L) match {
      case Failure(e) =>
        log.debug(s"caught expected exception: ${e.getClass}: ${e.getMessage}")
      case _ => 
        fail("tika was expected to fail but did not")
    }
  }
  
//    "Tika" should "extract from bodgy PDF" in {
//      val path = "/home/bac003/sw/dataFusion/coReports/reports/RHC-Annual-Report-2016.pdf" // born digital - has text in images too
//      val docIn = TikaUtil.tika(new java.io.FileInputStream(path), path).get
//      log.debug(s"docIn = ${docIn}")
//      docIn.content.map(_.size).getOrElse(0) > 100 should be(true)
//    }
  
//    "no bodge" should "clean text" in {
//      // this text was extracted by tika from RHC-Annual-Report-2016.pdf
//      val t = "Closed Group\n\n23. Parent Entity  \n Information\n\n24. Material \n Partly-Owned  \n Subsidiaries\n\nANNUAL REPORT 2016 37\n\n\n\n\n\n\n"
//      for (c <- t) log.debug(f"char: c = ${c} is ${(c.toInt)}") // bodgy chars are between 61000 - 62000 decimal
//      val t2 = t.filter(_.toInt <= 256)
//      log.debug(s"filtered: $t2")
//    }

}