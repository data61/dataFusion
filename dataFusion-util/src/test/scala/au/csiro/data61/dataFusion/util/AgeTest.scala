package au.csiro.data61.dataFusion.util

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data._
  
class AgeTest extends FlatSpec with Matchers {
  private val log = Logger(getClass)

    val content = """
Some junk before
the headers
From: Ardern Jacinda (37)

To: Bloggs Frederick (Akaroa); Smith
"""
  val extRef = Some(ExtRef("Jacinda Ardern", List(201, 202)))
  val n = Ner(7, 9, 36, 50, 1.0, "Jacinda Ardern", T_PERSON2, GAZ , extRef)

  "Age.toNer" should "find age" in {
    // log.debug(s"ner text = ${content.substring(n.offStr, n.offEnd)}")
    val d = Doc(1, Some(content), Map.empty, "path", List(n), List.empty)
    val d2 = Age.augment(d)
    log.debug(s"d2 = $d2")
    val expected = Ner(n.posEnd, n.posEnd + 1, n.offEnd + 2, n.offEnd + 4, 1.0, "37", "AGE", "D61AGE", extRef)
    assert(d2.ner.contains(expected))
  }
  
  it should "not find age after other text" in {
     val d = Doc(1, Some(content.replaceFirst("\\(37\\)", "aged (37)")), Map.empty, "path", List(n), List.empty)
     log.debug(s"d.content = ${d.content}")
     val d2 = Age.augment(d)
     d2 should be(d)
  }

}