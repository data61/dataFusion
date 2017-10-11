package au.csiro.data61.dataFusion.ner

import java.io.{ File, FileNotFoundException }
import java.nio.charset.Charset

import scala.language.{ implicitConversions, reflectiveCalls }

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import edu.mit.ll.mitie.{ NamedEntityExtractor, global }

import au.csiro.data61.dataFusion.common.Data.Ner
import java.nio.charset.Charset
import au.csiro.data61.dataFusion.common.Timer

/**
 * MITIE https://github.com/mit-nlp/MITIE
 * is C++ code that can be compiled to use optimised BLAS implementations (or use its own).
 * The Java wrapper is not available in maven repos so this entails sbt unmanaged jar in lib
 * and loading a native shared library.
 */
object MITIE {
  val log = Logger(getClass)
  val conf = ConfigFactory.load
  val utf8 = Charset.forName("UTF-8")
  
  log.info(s"loading MITIE native library")
  System.loadLibrary("javamitie")
  
  // the mitie.*Vector classes declare no common interface, so we resort to a "structural type" (i.e. duck type)
  implicit def toIter[T](v: { def get(i: Int): T; def size(): Long }) = new Iterator[T] {
    var i = 0
    override def hasNext = i < v.size
    override def next = {
      val n = v.get(i)
      i += 1
      n
    }
  }
  
//  // http://stackoverflow.com/questions/15038616/how-to-convert-between-character-and-byte-position-in-objective-c-c-c
//  // map a UTF-8 byte to it's width in UTF-16 ints:
//  // - leading byte of 1-3 byte UTF-8 chars -> 1 (these chars map to 1 UTF-16 int)
//  // - leading byte of 4 byte UTF-8 chars -> 2 (these chars map to 2 UTF-16 ints)
//  // - extension bytes -> 0
//  val utf16width = (0 until 256 map {
//    case i if Seq(0 to 0x7f, 0xc0 to 0xdf, 0xe0 to 0xef).exists(_.contains(i)) => 1
//    case i if (0xf0 to 0xf7).contains(i) => 2
//    case _ => 0
//  }).toArray
//  def javaOffset2(utf8Str: Array[Byte], from: Int, until: Int) = {
//    var x = 0
//    for (i <- from until until) {
//      x += utf16width(utf8Str(i) & 0xff) // prevent byte to int sign extension
//    }
//    x
//  }

  // Timing of this impl compared to above:
  // short strings: javaOffset 5.2 secs; javaOffset2 7.3 secs
  // long strings: javaOffset 2.891 secs; javaOffset2 2.892 secs
  // so there is no justification for the complexity of javaOffset2
  def javaOffset(utf8Str: Array[Byte], from: Int, until: Int) = new String(utf8Str, from, until - from, utf8).length
  
  class Nlp(path: String) {
    if (!new File(path).canRead) throw new FileNotFoundException(s"Can't read $path")
    log.info(s"loading MITIE model $path")
    
    // multi-threading test shows that NamedEntityExtractor is not thread-safe
    // MITIE comes with a generic non-thread-safe warning in: https://github.com/mit-nlp/MITIE/blob/master/mitielib/include/mitie.h
    val neExtractor = new ThreadLocal[NamedEntityExtractor] {
      override protected def initialValue = new NamedEntityExtractor(path)
    }
    val neTypes = neExtractor.get.getPossibleNerTags.toIndexedSeq
    log.debug(s"Nlp: posible ner types from $path are $neTypes")
   
    // val t = Timer()
    
    def ner(in: String) = {
      
      val inUtf8 = in.getBytes(utf8) // MITIE's offsets are relative to this
      // we get the NERs in order of increasing offset, so we can calculate the offsets incrementally
      var o8 = 0
      var o16 = 0
      def toJavaOffset(utf8Off: Int) = {
        if (utf8Off < o8) {
          log.debug(s"resetting o8, o16: utf8Off = $utf8Off, o8 = $o8, o16 = $o16")
          o8 = 0
          o16 = 0
        }
        o16 += javaOffset(inUtf8, o8, utf8Off)
        o8 = utf8Off
        o16
      }
      
      val words = global.tokenizeWithOffsets(in) // multi-threading test appears to show that this is thread-safe 
      neExtractor.get.extractEntities(words).map { e =>
        val offStrUtf8 = words.get(e.getStart).getIndex.toInt
        val offStr = toJavaOffset(offStrUtf8)
        val end = words.get(e.getEnd - 1)
        val offEnd = toJavaOffset(end.getIndex.toInt) + end.getToken.length
        Ner(
          e.getStart, e.getEnd, offStr, offEnd,
          e.getScore, in.substring(offStr, offEnd),
          neTypes(e.getTag), "MITIE", None
        )
      }.toList
    }
  }
    
  object English {
    val nlp = new Nlp(conf.getString("mitie.englishNerModel"))
  }
  
//  object Spanish {
//    val nlp = new Nlp(conf.getString("mitie.spanishNerModel"))
//  }
      
//  def ner(lang: String, in: String): List[Ner] =
//    lang match {
//      case "es" => {
//        // log.debug("Spanish")
//        Spanish.nlp.ner(in)
//      }
//      case _ => {
//        // log.debug("English")
//        English.nlp.ner(in)
//      }
//    }
  def ner(lang: String, in: String): List[Ner] = English.nlp.ner(in)
}
