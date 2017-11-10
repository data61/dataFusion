package au.csiro.data61.dataFusion.util

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, Embedded, ExtRef, GAZ, Ner, T_PERSON }
import scala.collection.JavaConverters._


class ProximityTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  
  val offStr = 80
  val cli = Main.defaultCliOption
  val dist = (cli.decay/5).toInt
  val weight = Math.exp(-dist/cli.decay)
  
  // case class Ner(posStr: Int, posEnd: Int, offStr: Int, offEnd: Int, score: Double, text: String, typ: String, impl: String, extRefId: Option[List[Long]])
  val ner1 = Ner(0, 0, offStr, 0, 1.0, "text", T_PERSON, GAZ, Some(ExtRef("Fred", List(1, 3))))
  val ner2 = Ner(0, 0, offStr + dist, 0, 1.0, "text", T_PERSON, GAZ, Some(ExtRef("Jane", List(2, 4))))
  val ners = List(ner1, ner2)
  
  
  "Proximity" should "find close ners" in {
    // case class Doc(id: Long, content: Option[String], meta: Map[String, String], path: String, ner: List[Ner], embedded: List[Embedded])
    val doc = Doc(0, Some("text"), Map.empty, "path", ners, List.empty)
    val prox = new Proximity(cli, n => n.iterator)
    prox.accDoc(doc)
    for (x <- prox.nodeMap.values.asScala) log.info(s"$x")
    for (x <- prox.edgeMap.entrySet.asScala) log.info(s"$x")
    prox.nodeMap.size should be(2)
    prox.edgeMap.size should be(1)
    prox.edgeMap.get((0,1)) should be(Map("UNKNOWN" -> (weight, 1)))

    prox.accDoc(doc)
    for (x <- prox.nodeMap.values.asScala) log.info(s"$x")
    for (x <- prox.edgeMap.entrySet.asScala) log.info(s"$x")
    prox.nodeMap.size should be(2)
    prox.edgeMap.size should be(1)
    prox.edgeMap.get((0,1)) should be(Map("UNKNOWN" -> (2*weight, 2)))

    // case class Embedded(content: Option[String], meta: Map[String, String], ner: List[Ner])
    val emb = Embedded(Some("text"), Map.empty, ners)
    val doc2 = doc.copy(embedded = List(emb))
    prox.accDoc(doc2)
    for (x <- prox.nodeMap.values.asScala) log.info(s"$x")
    for (x <- prox.edgeMap.entrySet.asScala) log.info(s"$x")
    prox.nodeMap.size should be(2)
    prox.edgeMap.size should be(1)
    prox.edgeMap.get((0,1)) should be(Map("UNKNOWN" -> (4*weight, 4)))
  }
  
}