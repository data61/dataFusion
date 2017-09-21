package au.csiro.data61.dataFusion.ner

import com.typesafe.scalalogging.Logger

object Split {
  private val log = Logger(getClass)

  val allAlpha = """\p{Alpha}{3,}""".r
  val hasVowel = """(?i)[aeiou]""".r.unanchored
  val hasConsonant = """(?i)[a-z&&[^aeiouy]]""".r.unanchored
  
  def wordLike(word: String) =
    (for {
      a <- allAlpha.unapplySeq(word)
      b <- hasVowel.unapplySeq(word)
      c <- hasConsonant.unapplySeq(word)
    } yield (a, b, c)).isDefined

  def containsWordLike(line: String) = line.split(" +") exists wordLike
    
  /**
   * CoreNLP doesn't terminate on long input, so split on lines where containsWordLike is false
   * (that includes blank lines so it splits on paragraphs),
   * but don't split again for the next splitmin lines.
   * Paragraphs longer than splitmax are split in segments of splitmax lines, without considering sentence breaks.
   * This is a bit drastic, but less so than non-terminating processing.
   * This handles spreadsheet data with potentially very long "paragraphs".
   */
  def splitParagraphs(lines: IndexedSeq[String], splitmin: Int, splitmax: Int): Iterator[(Int, Int, String)] = {
    val splits = (for ((l, i) <- lines.zipWithIndex if !containsWordLike(l)) yield i).toList :+ lines.size
    log.debug(s"main: splits = $splits")
    val splitsFiltered = (splits.foldLeft((splitmin, 0, List(0))){ case ((maxi, prev, result), x) => 
      log.debug(s"splitParagraphs: x = $x, maxi = $maxi")
      if (x <= maxi) {
        (maxi, x, result) // drop values within range of splitmin
      } else {
        val z = ((prev + splitmax) until x by splitmax).toList :+ x // if bigger than splitmax then split every splitmax
        (x + splitmin, x, z.reverse ++ result)
      }
    })._3.reverse
    log.debug(s"splitParagraphs: splitsFiltered = $splitsFiltered")
    if (splitsFiltered.size < 2) {
      Iterator.single((0, lines.size, lines.mkString("", "\n", "\n")))
    } else {
      splitsFiltered.sliding(2).flatMap {
        case a :: b :: Nil if a < b => List((a, b, lines.slice(a, b).mkString("", "\n", "\n"))) // include trailing \n so concatenating splits gives original String 
        case _ => List.empty
      }
    }
  }
    
//  def main(args: Array[String]): Unit = {
//    implicit val utf8 = io.Codec.UTF8
//    val lines = io.Source.fromFile("/home/bac003/sw/submissions/data/submissions/sub-043-part-1.txt").getLines.take(100).toIndexedSeq
//    val splits = split(lines, 5).toList
//    log.debug(s"main: splits = $splits")
//  }
}