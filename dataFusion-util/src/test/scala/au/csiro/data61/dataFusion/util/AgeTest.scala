package au.csiro.data61.dataFusion.util

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data._
import Age._
  
class AgeTest extends FlatSpec with Matchers {
  private val log = Logger(getClass)

  def mkNer(content: String, name: String, ids: List[Long]) = {
    val offStr = content.indexOf(name)
    assert(offStr != -1)
    val posStr = wordCount(content.substring(0, offStr))
    Ner(posStr, posStr + wordCount(name), offStr, offStr + name.length, 0.0, name, T_PERSON2, GAZ, Some(ExtRef(name, ids))) 
  }

  "Age.toNer" should "find parenthesized age" in {
    val c = "The newbie Jacinda Ardern (37) was selected."
    val n1 = mkNer(c, "Jacinda Ardern", List(101, 102))
    val d = Doc(1, Some(c), Map.empty, "path", List(n1), List.empty)
    val d2 = augment(d)
    log.debug(s"d2 = $d2")
    d2.ner.size should be(2) 
    val expected = Ner(n1.posEnd, n1.posEnd + 1, n1.offEnd + 2, n1.offEnd + 4, 1.0, "37", "AGE", "D61AGE", n1.extRef)
    assert(d2.ner.contains(expected))
  }
  
  it should "not find parenthesized age after other text" in {
    val c = "The newbie Jacinda Ardern blah (37) was selected."
    val n1 = mkNer(c, "Jacinda Ardern", List(101, 102))
    val d = Doc(1, Some(c), Map.empty, "path", List(n1), List.empty)
    val d2 = augment(d)
    log.debug(s"d2 = $d2")
    d2 should be(d)
  }

  it should "not find age in a phone number" in {
    val c = "The newbie Jacinda Ardern (65) 3214-3456 was selected."
    val n1 = mkNer(c, "Jacinda Ardern", List(101, 102))
    val d = Doc(1, Some(c), Map.empty, "path", List(n1), List.empty)
    val d2 = augment(d)
    log.debug(s"d2 = $d2")
    d2 should be(d)
  }
  
  it should "find age in 'name, aged dd'" in {
    val c = "The newbie Jacinda Ardern, aged 37, was selected."
    val n1 = mkNer(c, "Jacinda Ardern", List(101, 102))
    val d = Doc(1, Some(c), Map.empty, "path", List(n1), List.empty)
    val d2 = augment(d)
    log.debug(s"d2 = $d2")
    d2.ner.size should be(2) 
    val expected = Ner(n1.posEnd + 2, n1.posEnd + 3, n1.offEnd + 7, n1.offEnd + 9, 1.0, "37", "AGE", "D61AGE", n1.extRef)
    assert(d2.ner.contains(expected))
  }
  
  it should "find age within 50 chars after name" in {
    val c = "Jacinda Ardern, future PM, aged 37, was selected."
    val n1 = mkNer(c, "Jacinda Ardern", List(101, 102))
    val d = Doc(1, Some(c), Map.empty, "path", List(n1), List.empty)
    val d2 = augment(d)
    log.debug(s"d2 = $d2")
    d2.ner.size should be(2) 
    val expected = Ner(n1.posEnd + 4, n1.posEnd + 5, n1.offEnd + 18, n1.offEnd + 20, 1.0, "37", "AGE", "D61AGE", n1.extRef)
    assert(d2.ner.contains(expected))
  }
  
  it should "associate an age only with the last preceeding PERSON" in {
    val c = "Frederick Bloggs CEO and Jacinda Ardern, future PM, aged 37, were selected."
    val n1 = mkNer(c, "Jacinda Ardern", List(101, 102))
    val n2 = mkNer(c, "Frederick Bloggs", List(201, 202))
    val d = Doc(1, Some(c), Map.empty, "path", List(n1, n2), List.empty)
    val d2 = augment(d)
    log.debug(s"d2 = $d2")
    d2.ner.size should be(3) 
    val expected = Ner(n1.posEnd + 4, n1.posEnd + 5, n1.offEnd + 18, n1.offEnd + 20, 1.0, "37", "AGE", "D61AGE", n1.extRef)
    assert(d2.ner.contains(expected))
  }
}