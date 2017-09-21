package au.csiro.data61.dataFusion.ner

import org.scalatest.{ Finders, FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.Ner
import CoreNLP._

class CoreNLPTest extends FlatSpec with Matchers {
  val log = Logger(getClass)

  val en ="en"
  val enTxt = """The Clinton Engineer Works was the site of the Manhattan Project's World War II production facilities that provided the enriched uranium used in the bombing of Hiroshima in August 1945.

Its X-10 Graphite Reactor produced the first samples of plutonium from a reactor.

Located just south of the town of Clinton, Tennessee, it included the production facilities of the K-25, Y-12 and S-50 projects, various utilities, and the township of Oak Ridge.

The Manhattan District Engineer, Kenneth Nichols, moved the Manhattan District headquarters there from Manhattan in August 1943.
"""

//  val es = "es"
//  val esTxt = """Cristóbal Colón, Cristoforo Colombo en italiano o Christophorus Columbus en latín (Génova,n. 1 1 2 c. 1436-14513 -Valladolid, 20 de mayo de 1506), fue un navegante, cartógrafo, almirante, virrey y gobernador general de las Indias Occidentales al servicio de la Corona de Castilla.
//
//Es famoso por haber realizado el descubrimiento de América, el 12 de octubre de 1492, al llegar a la isla de Guanahani, actualmente en las Bahamas.
//
//Efectuó cuatro viajes a las Indias —denominación del continente americano hasta la publicación del Planisferio de Martín Waldseemüller en 1507— y aunque posiblemente no fue el primer explorador europeo de América, se le considera el descubridor de un nuevo continente —por eso llamado el Nuevo Mundo— para Europa, al ser el primero que trazó una ruta de ida y vuelta a través del océano Atlántico y dio a conocer la noticia.
//
//Este hecho impulsó decisivamente la expansión mundial de la civilización europea, y la conquista y colonización por varias de sus potencias del continente americano.
//"""
  
  "CoreNLP NER" should "get English entities" in {
    val ners = nerSplitParagraphs(en, enTxt, 1, 1) // split into small chunks
    log.debug(s"ners = ${ners}")
    assert(ners.contains(Ner(12, 15, 67, 79, 1.0, "World War II", "MISC", "CoreNLP")))
    assert(ners.contains(Ner(98, 100, 566, 577, 1.0, "August 1943", "DATE", "CoreNLP")))
  }
  
  it should "handle no space between digits and mutiplier" in {
    for (mult <- Seq("hundred", "thousand", "million", "billion", "trillion")) {
      val text = "Henry bought Sally a new car for $3.75" + mult + " for her birthday."
      val ners = ner("en", text)
      log.debug(s"text = $text, ners = ${ners}")
      assert(ners.exists(_.typ == "MONEY"))
    }
  }
  
//  it should "get Spanish entities" in {
//    val ners = nerSplit(es, esTxt, 1) // split into small chunks
//    log.debug(s"ners = ${ners}")
//    assert(ners.contains(Ner(0, 2, 0, 15, 1.0, "Cristóbal Colón", "PERSON", "CoreNLP")))
//    assert(ners.contains(Ner(141, 142, 737, 743, 1.0, "Europa", "LOCATION", "CoreNLP")))
//  }
//  
//  it should "get Spanish entities in mutiple threads" in {
//    val expected = nerSplit(es, esTxt, 1) // split into small chunks
//    
//    val r = new Runnable {
//      override def run = {
//        val ners = ner(es, esTxt) // would split to 100 lines, but text is smaller than that, so no split
//        ners should be(expected)
//      }
//    }
//    val threads = Iterator.range(0, 8).map { _ => 
//      val t = new Thread(r)
//      t.start
//      t
//    }.toList
//    threads.foreach(_.join)
//  }
}