package au.csiro.data61.dataFusion.util

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, GAZ, Ner, T_PERSON, T_PERSON2 }

/** create AGE Ner's from GAZ PERSON{,2} Ners followed by a parenthesized number 18-99 */
object Age {
  private val log = Logger(getClass)

  val ageRe1 = """\s*\((\d{2})\)(?!\s*\d)""".r // look for " (dd)" after a name not followed by further digits (a phone number)
  val ageRe2 = """(.{0,20}\baged?) (\d{2})\b""".r // look for "aged dd" soon after a name
  def wordCount(s: String) = s.split("\\s+").length
  def find(s: String) = ageRe1.findPrefixMatchOf(s).map((_, 1, 0)).orElse(ageRe2.findPrefixMatchOf(s).map(m => (m, 2, wordCount(m.group(1)))))
  
  def toNer(content: String, ner: List[Ner]): Iterator[Ner] = {
    for {
      n <- ner.iterator if n.impl == GAZ && (n.typ == T_PERSON || n.typ == T_PERSON2)
      (m, grp, posOffset) <- find(content.substring(n.offEnd)) if m.group(grp).toInt >= 18
    } yield Ner(n.posEnd + posOffset, n.posEnd + posOffset + 1, n.offEnd + m.start(grp), n.offEnd + m.end(grp), 1.0, m.group(grp), "AGE", "D61AGE", n.extRef)
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
