package au.csiro.data61.dataFusion.ner

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.Ner

import edu.mit.ll.mitie.global

import MITIE._

/**
 * To run in Eclipse:
 * Build Path > Configure Build Path > Source > dataFusion-ner/src/main/scala > Native library location
 * add MITIE-native.
 * 
 * TODO: fails: Run as > ScalaTest - File
 * Launch configuration MITIETest.scala references non-existing project ner.
 * Whereas: Run as > ScalaTest - Suite
 * works fine.
 * 
 * To run in sbt:
 * export LD_LIBRARY_PATH=/full-path/dataFusion-ner/MITIE-native
 * sbt -J-Xmx2G test
 */
class MITIETest extends FlatSpec with Matchers {
  val log = Logger(getClass)

  val en = "en"
  val enTxt = """The Clinton Engineer Works was the site of the Manhattan Project's World War II production facilities that provided the enriched uranium used in the bombing of Hiroshima in August 1945. Its X-10 Graphite Reactor produced the first samples of plutonium from a reactor. Located just south of the town of Clinton, Tennessee, it included the production facilities of the K-25, Y-12 and S-50 projects, various utilities, and the township of Oak Ridge. The Manhattan District Engineer, Kenneth Nichols, moved the Manhattan District headquarters there from Manhattan in August 1943. """
  
  val es = "es"
  val esTxt = """Cristóbal Colón, Cristoforo Colombo en italiano o Christophorus Columbus en latín (Génova,n. 1 1 2 c. 1436-14513 -Valladolid, 20 de mayo de 1506), fue un navegante, cartógrafo, almirante, virrey y gobernador general de las Indias Occidentales al servicio de la Corona de Castilla. Es famoso por haber realizado el descubrimiento de América, el 12 de octubre de 1492, al llegar a la isla de Guanahani, actualmente en las Bahamas.
Efectuó cuatro viajes a las Indias —denominación del continente americano hasta la publicación del Planisferio de Martín Waldseemüller en 1507— y aunque posiblemente no fue el primer explorador europeo de América, se le considera el descubridor de un nuevo continente —por eso llamado el Nuevo Mundo— para Europa, al ser el primero que trazó una ruta de ida y vuelta a través del océano Atlántico y dio a conocer la noticia. Este hecho impulsó decisivamente la expansión mundial de la civilización europea, y la conquista y colonización por varias de sus potencias del continente americano."""
  
  "javaOffset" should "get offsets" in {
    // these 5 chars are encoded:
    // - in UTF-8 as byte sequences of length 1, 2, 3, 4 & 1 respectively
    // - in a Java String (UTF-16) as char sequences of length 1, 1, 1, 2 & 1 respectively
    //   where the 1 length sequences are regular characters, but the 2 length sequences consist of a high-surrogate and a low-surrogate
    //   also referred to as a surrogate pair.
    val str = "aü你𩸽b" // UTF-16 offsets: 0, 1, 2, 3, 5; length 6
    str.length should be(6)
    val utf8Str = str.getBytes(utf8) // UTF-8 offsets: 0, 1, 3, 6, 10; length 11
    utf8Str.length should be(11)
    for ((off16, off8) <- Seq(0, 1, 2, 3, 5) zip Seq(0, 1, 3, 6, 10)) {
      log.debug(s"off16 = $off16, off8 = $off8")
      javaOffset(utf8Str, 0, off8) should be(off16)
    }
  }
  
//  it should "be timed on short strings" in {
//    val words = esTxt.split(" ").map(w => (w, w.getBytes(utf8))) // pairs of UTF-16, UTF-8 words
//    for (fn <- Seq(javaOffset _, javaOffset2 _)) {
//      val t = Timer()
//      t.start
//      for {
//        _ <- 0 until 100000
//        (w16, w8) <- words
//      } {
//        fn(w8, 0, w8.length) should be(w16.length)
//      }
//      t.stop
//      log.info(s"elapsed runtime = ${t.elapsedSecs} secs")
//    }
//  }
//  
//  it should "be timed on a long string" in {
//    val words = Seq((esTxt, esTxt.getBytes(utf8))) // pairs of UTF-16, UTF-8 text
//    for (fn <- Seq(javaOffset _, javaOffset2 _)) {
//      val t = Timer()
//      t.start
//      for {
//        _ <- 0 until 1000000
//        (w16, w8) <- words
//      } {
//        fn(w8, 0, w8.length) should be(w16.length)
//      }
//      t.stop
//      log.info(s"elapsed runtime = ${t.elapsedSecs} secs")
//    }
//  }
  
  "MITIE" should "get English entities" in {
    val ners = ner(en, enTxt)
    log.debug(s"ners = $ners")
    // log.debug(s"Offset conversion times: en = ${English.nlp.t.elapsedSecs} secs")
    assert(ners.map(_.copy(score = 1.0)).contains(Ner(76, 78, 436, 445, 1.0, "Oak Ridge", "LOCATION", "MITIE")))
  }
  
//  it should "get Spanish entities" in {
//    val ners = ner(es, esTxt)
//    log.debug(s"ners = ${ners}")
//    // log.debug(s"Offset conversion times: es = ${Spanish.nlp.t.elapsedSecs} secs")
//    assert(ners.map(_.copy(score = 1.0)).contains(Ner(0, 2, 0, 15, 1.0, "Cristóbal Colón", "PERSON", "MITIE")))
//  }
  
  it should "get English entities in mutiple threads" in {
    val expected = ner(en, enTxt)
    
    val r = new Runnable {
      override def run = {
      for (_ <- 0 until 8) {
          val ners = ner(en, enTxt)
          ners should be(expected)
        }
      }
    }
    
    // sequential
    // for (i <- 0 until 8) r.run
    
    // parallel
    // With 20 threads test often passes even with a multi-threading bug present, failure appears quite consistent with 50 threads.
    // Using a ThreadLocal to prevent multiple threads accessing the same NamedEntityExtractor requires a separate model per thread
    // and we no longer have enough memory to run 50 threads, so run 8 threads with each thread repeating the test 8 times.
    // While running the test system memory usage (not just Java heap because we've got C++ allocated memory) goes from ~3.2G to ~5.8G,
    // so we need about 325MB per thread for MITIE.
    val threads = for (_ <- 0 until 8) yield {
      val t = new Thread(r)
      t.start
      t
    }
    threads.foreach(_.join)
    // log.debug(s"Offset conversion times: en = ${English.nlp.t.elapsedSecs} secs, es = ${Spanish.nlp.t.elapsedSecs} secs")
  }
}