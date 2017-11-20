package au.csiro.data61.dataFusion.util

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, GAZ, Ner, T_PERSON, T_PERSON2 }

/** create AGE Ner's from GAZ PERSON{,2} Ners followed by a parenthesized number 18-99 */
object Age {
  private val log = Logger(getClass)

  val ageRe1 = """\s*\((\d{2})\)(?!\s*\d)""".r    // look for " (dd)" after a name not followed by further digits (a phone number)
  val ageRe2 = """(.{0,50}\baged?) (\d{2})\b""".r // look for "aged dd" within 50 chars after a name (to allow for a title in beween)
  def wordCount(s: String) = s.split("\\s+").length
  def find(s: String) = ageRe1.findPrefixMatchOf(s).map((_, 1, 0)).orElse(ageRe2.findPrefixMatchOf(s).map(m => (m, 2, wordCount(m.group(1)))))
  
  def toNer(content: String, ner: List[Ner]): Iterator[Ner] = {
    val it = for {
      n <- ner.sortBy(_.offStr).iterator if n.impl == GAZ && (n.typ == T_PERSON || n.typ == T_PERSON2)
      (m, grp, posOffset) <- find(content.substring(n.offEnd)) if m.group(grp).toInt >= 18  // must be adult
    } yield Ner(n.posEnd + posOffset, n.posEnd + posOffset + 1, n.offEnd + m.start(grp), n.offEnd + m.end(grp), 1.0, m.group(grp), "AGE", "D61AGE", n.extRef)
    
    // if we have two close PERSONs followed by an AGE the above could associate the same AGE with both of them
    // with Ners sorted as above, we only want the last one
    val dummy = Ner(0, 0, 0, 0, 0.0, "text", "typ", "impl", None)
    (it ++ Iterator.single(dummy)).sliding(2).flatMap {
      case Seq(n1, n2) if n1.offStr != n2.offStr => Iterator.single(n1)
      case _ => Iterator.empty
    }
  }
  
  val augment: Doc => Doc = { d =>
    val ner = d.ner ++ d.content.toList.flatMap(toNer(_, d.ner))
    val embedded = d.embedded.map { e =>
      val ner = e.ner ++ e.content.toList.flatMap(toNer(_, e.ner))
      e.copy(ner = ner)
    }
    d.copy(ner = ner, embedded = embedded)
  }
}
