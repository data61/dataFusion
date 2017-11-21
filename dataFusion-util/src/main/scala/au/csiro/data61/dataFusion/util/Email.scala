package au.csiro.data61.dataFusion.util

import java.util.Arrays
import java.util.regex.Pattern.{ CASE_INSENSITIVE, MULTILINE }
import java.util.regex.Pattern

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, EMAIL, ExtRef, GAZ, Ner, T_PERSON, T_PERSON2 }
import scala.collection.mutable.ArrayBuffer

/**
 * Handle email messages printed from MS Outlook then scanned.
 * 
 * We're seeing headers missing the colon after From To etc. and other punctuation in the main .content (provided by the scanner's OCR),
 * however these are present in the embedded[].content (Tika's Tesseract OCR).
 * When matching on headers missing the colon we can't separate recipient's names due to missing semi-colons (or names from their
 * parenthesized locations due to the missing parentheses), so there's no point trying to handle the case of missing punctuation.
 * In most cases we'll get the same data with the punctuation from the embedded[].content.
 */
// could convert to scala regex, although I don't think the code would be any more compact
object Email {
  private val log = Logger(getClass)

  def p(re: String) = Pattern.compile(re, MULTILINE | CASE_INSENSITIVE)
  
  val name = "[A-Z'-]+,?"
  val location = "\\([^)]+\\)"
  val person = s"${name}(?:\\s+${name})*(?:\\s+${location})?"
  val from = p(s"^From:\\s+(${person})\\s*$$")
  val recipientList = p(s"^(To|Cc|Bcc):\\s+(${person}(?:;\\s+${person})*)$$")
  val personDelim = p("\\s*;\\s+")
  val endHead = p("^(?:Subject|Sent|Date):\\s.+$$")
  val space = "\\s+".r
  
  /** create EMAIL Ner's from names in parsed email headers */
  def toNer(extRef: Int => Option[ExtRef])(text: String): Iterator[Ner] = {
    // rough attempt at generating a pos (word offset) from an off (character offset)
    val wordOffsets = (Iterator.single(0) ++ space.findAllMatchIn(text).map(_.end)).toArray
    def toPos(off: Int) = {
      val x = Arrays.binarySearch(wordOffsets, off)
      if (x >= 0) x else -x - 1
    }
    
    val mFrom = from.matcher(text)
    val mRecip = recipientList.matcher(text)
    val mEnd = endHead.matcher(text)
    val mPerDelim = personDelim.matcher(text)
    
    val buf = new ArrayBuffer[Ner]
    def addNer(offStr: Int, offEnd: Int, typ: String): Unit = {
      buf += Ner(toPos(offStr), toPos(offEnd), offStr, offEnd, 1.0f, text.substring(offStr, offEnd), typ, EMAIL, extRef(offStr))
    }
    
    var p = 0
    while (p < text.length && mFrom.find(p)) {
      val bufSize = buf.size
      log.debug(s"toNer: FROM: ${mFrom.group(1)}")
      addNer(mFrom.start(1), mFrom.end(1), "FROM")
      p = mFrom.end + 1 // skip past the \n
      while (p < text.length && { mRecip.region(p, text.length); mRecip }.lookingAt) {
        log.debug(s"toNer: ${mRecip.group(1)}: ${mRecip.group(2)}")
        val typ = mRecip.group(1).toUpperCase
        mPerDelim.region(mRecip.start(2), mRecip.end(2))
        var pd = mRecip.start(2)
        while (pd < mRecip.end(2) && mPerDelim.find) {
          log.debug(s"toNer: ${typ}: ${text.substring(pd, mPerDelim.start)}")
          addNer(pd, mPerDelim.start, typ)
          pd = mPerDelim.end
        }
        if (pd < mRecip.end(2)) {
          log.debug(s"toNer: ${typ}: ${text.substring(pd, mRecip.end(2))}")
          addNer(pd, mRecip.end(2), typ)
        }
        p = mRecip.end + 1
      }
      if (p < text.length && { mEnd.region(p, text.length); mEnd }.lookingAt) {
        log.debug("toNer: got end of header")
        p = mEnd.end + 1
      } else {
        log.info(s"Not seeing email end header at offset $p. content: $text")
      }
      // if (buf.size < bufSize + 2) buf.remove(bufSize, buf.size - 1)
    } 
    buf.iterator
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
