package au.csiro.data61.dataFusion.util

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, GAZ, Ner, T_PERSON, T_PERSON2 }

/** create AGE Ner's from GAZ PERSON{,2} Ners followed by a parenthesized number 18-99 */
object Age {
  private val log = Logger(getClass)

  val ageRe = """\s*\((\d{2})\)""".r // look for " (age)" after a name
  
  def toNer(content: String, ner: List[Ner]): Iterator[Ner] = {
    for {
      n <- ner.iterator if n.impl == GAZ && (n.typ == T_PERSON || n.typ == T_PERSON2)
      m <- ageRe.findPrefixMatchOf(content.substring(n.offEnd)) if m.group(1).toInt >= 18
    } yield Ner(n.posEnd, n.posEnd + 1, n.offEnd + m.start(1), n.offEnd + m.end(1), 1.0, m.group(1), "AGE", "D61AGE", n.extRef)
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
