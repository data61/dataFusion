package au.csiro.data61.dataFusion.util

import java.util.Arrays
import java.util.regex.Pattern.{ CASE_INSENSITIVE, MULTILINE }
import java.util.regex.Pattern

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, EMAIL, ExtRef, GAZ, Ner, T_PERSON, T_PERSON2 }

// TODO: perhaps convert to scala rexex, although I don't think the code is more compact
object Email {
  private val log = Logger(getClass)

  def p(re: String) = Pattern.compile(re, MULTILINE | CASE_INSENSITIVE)
  
  val head = "(?:From|To|Cc|Bcc)"
  val name = s"(?!^${head})[A-Z'-]+"
  val location = "\\([^)]+\\)"
  val person = s"${name}(?:\\s+${name})*(?:\\s+${location})?"
  val personList = p(s"^(${head}):?\\s+(${person}(?:;\\s+${person})*)")
  val personDelim = p("\\s*;\\s+")
  
  val startHead = p("^From:?\\s")
  val endHead = p("^(?:Subject|Sent|Date|From):?\\s")
  
  val nonHeadLine = s"^(?!${head}:?\\s).*$$"
  val body = p(s"nonHeadLine{10}")
  
  /** Iterate over blocks of email headers returning the (str, end) character offsets of each block */ 
  def blockIter(text: String) = new Iterator[(Int, Int)] {
    var str = 0
    var end = 0
    val mStr = startHead.matcher(text)
    val mEnd = endHead.matcher(text)
    val mBody = body.matcher(text)
    
    override def hasNext = {
      val has = mStr.find(end)
      if (has) {
        str = mStr.start
        end = if (mEnd.find(str + 1)) mEnd.start
          else if (mBody.find(str + 1)) mBody.end
          else text.length
      }
      has
    }
    
    override def next = (str, end)
  }
  
  /** Within a block, iterate over each header (From, To, etc.) and it's list of people, returning:
   *  (Hdr e.g. "To", start offset of list of people, end offset of list of people)
   */
  def personListIter(text: String, str0: Int, end0: Int) = new Iterator[(String, Int, Int)] {
    var str = str0
    var end = str0
    val m = personList.matcher(text.substring(0, end0))
    
    override def hasNext = {
      val has = m.find(end)
      if (has) {
        str = m.start
        end = m.end
      }
      has
    }
    
    override def next = (m.group(1), m.start(2), m.end(2))
  }
  
  /** Within a list of people, iterate over each person, returning (start offset of person, end offset of person) */
  def personIter(text: String, str0: Int, end0: Int) = new Iterator[(Int, Int)] {
    var str = str0
    var end = str0
    var pos = str0
    val m = personDelim.matcher(text.substring(0, end0))
    
    override def hasNext = {
      str = pos
      if (m.find(pos)) {
        end = m.start
        pos = m.end
        true
      } else if (pos < end0) {
        end = end0
        pos = end0
        true
      } else {
        false
      }
    }
    
    override def next = (str, end)
  }
  
  /** create EMAIL Ner's from names in parsed email headers */
  def toNer(extRef: Int => Option[ExtRef])(text: String): Iterator[Ner] = {
    val space = "\\s+".r
    val wordOffsets = (Iterator.single(0) ++ space.findAllMatchIn(text).map(_.end)).toArray
    def toPos(off: Int) = {
      val x = Arrays.binarySearch(wordOffsets, off)
      if (x >= 0) x else -x - 1
    }
    
    for {
      (str, end) <- blockIter(text)
      (hdr, pplStr, pplEnd) <- personListIter(text, str, end)
      // ppl = text.substring(pplStr, pplEnd)
      // _ = log.debug(s"hdr = $hdr, pplStr = $pplStr, pplEnd = $pplEnd, ppl = $ppl")
      (offStr, offEnd) <- personIter(text, pplStr, pplEnd)
    } yield Ner(toPos(offStr), toPos(offEnd), offStr, offEnd, 1.0f, text.substring(offStr, offEnd), hdr.toUpperCase, EMAIL, extRef(offStr))
  }
  
  /** the ExtRef in the Email Ner is taken from a GAZ Ner of typ T_PERSON or T_PERSON2 starting at the same offset */
  def extRef(ner: List[Ner]): Int => Option[ExtRef] = {
    def m(p: Ner => Boolean) = ner.filter(p).groupBy(_.offStr)
    val per3map = m(n => n.impl == GAZ && n.typ == T_PERSON)
    val per2map = m(n => n.impl == GAZ && n.typ == T_PERSON2)
    offStr =>
      for {
        l <- per3map.get(offStr).orElse(per2map.get(offStr))
        n <- l.headOption
        e <- n.extRef
      } yield e
  }
  
  val augment: Doc => Doc = { d =>
    val ner = d.ner ++ d.content.toList.flatMap(toNer(extRef(d.ner)))
    val embedded = d.embedded.map { e =>
      val ner = e.ner ++ e.content.toList.flatMap(toNer(extRef(e.ner)))
      e.copy(ner = ner)
    }
    d.copy(ner = ner, embedded = embedded)
  }
}
