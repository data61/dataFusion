package au.csiro.data61.dataFusion.common

import java.io.{ BufferedWriter, File, FileOutputStream, OutputStreamWriter }
import com.typesafe.scalalogging.Logger

object Util {
  private val log = Logger(getClass)
  
  def bufWriter(f: File) = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))
  
  case class Feat(wordLike: Boolean, initCap: Boolean, endsDot: Boolean)

  // A metric for English text quality.
  // Near enough is good enough, no need to handle voweless works like "sky" or apostrophes.  
  
  val word = """\S+""".r
  val vowels = "AEIOUaeiou".toSet
  val upper = ('A' to 'Z').toSet
  val letter = upper ++ upper.map(Character.toLowerCase)
  val punct = ",;:'\"!@#$%^&*()-_+=/[]{}.".toSet

  def word2feat(w: String): Feat = {
    val numVowel = w.count(vowels contains _)
    val numLetter = w.count(letter contains _)
    val numUpper = w.count(upper contains _)
    val startsPunct = punct contains w.head 
    val endsPunct = punct contains w.last
    val endsDot = w.endsWith(".")
    val expectedLetters = w.length - (if (startsPunct) 1 else 0) - (if (endsPunct) 1 else 0)
    val initCap = numUpper == 1 && (startsPunct && w.length > 1 && Character.isUpperCase(w(1))|| Character.isUpperCase(w.head))
    val wordLike = w.length < 30 && numLetter == expectedLetters && (numUpper == 0 || initCap) && numVowel > 0
    // log.debug(s"word2feat: numVowel = $numVowel, numLetter = $numLetter, numUpper = $numUpper, startsPunct = $startsPunct, endsPunct = $endsPunct, endsDot = $endsDot, initCap = $initCap, length = ${w.length}, expectedLetters = $expectedLetters, wordLike = $wordLike")
    Feat(wordLike, initCap, endsDot)
  }

  def englishScore(text: String): Double = {
    val feats = word.findAllIn(text).map(word2feat).toSeq
    val numWords = feats.count(_.wordLike)
    val wordScore = numWords.toDouble / feats.size // ratio
    
    // unit test with text from wikipedia is getting a very low sentenceScore, so disabled for now
    val numSentence = feats.sliding(2).count {
      case Seq(a, b) => a.wordLike && a.endsDot && b.wordLike && b.initCap
      case _ => false
    }
    val x = numWords.toDouble / numSentence // avgSentenceLength
    // See http://hearle.nahoo.net/Academic/Maths/Sentence.html
    // try piece-wise linear score
    val sentenceScore = if (x < 10.0) 0.6 + 0.4 * x/10.0
      else if (x < 30.0) 1.0
      else if (x < 100.0) 1.0 - 0.8 * (x - 30.0)/70.0 
      else 0.2
    
    log.debug(s"englishScore: numSentence = $numSentence, numWords = $numWords, wordScore = $wordScore, sentenceScore = $sentenceScore")
    wordScore * sentenceScore
  }
}