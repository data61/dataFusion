package au.csiro.data61.dataFusion.ner

import org.scalatest.{ Finders, FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger
import Split._

class SplitTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  val wrdLike = Seq("word", "Word", "zzza")
  val nonWrdLike = Seq("", "an", "zebra.", "sky", "aeiou")
  
  "wordLike" should "detect tokens looking like words" in {
    for (w <- wrdLike) assert(wordLike(w))
  }
  
  it should "reject words < 3 chars, with non alpha chars, with no vowels or no consonants" in {
    for (w <- nonWrdLike) assert(!wordLike(w))
  }
  
  "containsWordLike" should "detect lines containing a wordLike" in {
    for {
      i <- 0 to nonWrdLike.size // index where we'll insert the wrdLike
      w <- wrdLike
    } {
      val line = (nonWrdLike.take(i) ++ Seq(w) ++ nonWrdLike.drop(i)).mkString(" ")
      assert(containsWordLike(line))
    }
  }
  
  it should  "reject lines with no wordLike" in {
    for (line <- Seq("", nonWrdLike.mkString(" "))) assert(!containsWordLike(line))
  }
  
  val longText = """
Fiction House apparently made the decision to launch Planet Stories
so quickly that there was little time for Reiss to obtain new stories,
so he worked with Julius Schwartz and other authors' agents to fill the
first issue. The results were unremarkable, but Reiss was energetic, and
was able to improve the quality of fiction in succeeding issues, though
he occasionally apologized to the readers for printing weak material.
The magazine was exclusively focused on interplanetary adventures,
often taking place in primitive societies that would now be regarded as
"sword and sorcery" settings, and was aimed at a young readership; the
result was a mixture of what became known as space opera and planetary
romances—melodramatic tales of action and adventure on alien planets
and in interplanetary space. Planet relied on a few authors to
provide the bulk of its fiction in the early years, with Nelson Bond
providing eight lead stories, some of them novels. Fourteen more were
written by Ray Cummings and Ross Rocklynne; and Leigh Brackett was also
a regular contributor, with seventeen stories in total published over
the lifetime of the magazine.

The letter column in Planet was titled "The Vizigraph"; it was very
active, with long letters from an engaged readership. It often printed
letters from established writers, and from fans who would go on to become
well known professionally: Damon Knight's letters are described by sf
historian Mike Ashley as "legendary"; and Robert Silverberg commented
in a letter in the Summer 1950 issue that Ray Bradbury "certainly gets
some original ideas, if not good ones". The editors put a good
deal of effort into keeping the letter column friendly and lively;
contemporary writer and editor Robert Lowndes recalls that "Reiss was
sincere and urbane; Wilbur [Peacock] enjoyed taking his coat off and
being one of the crowd"."""
  
  "splitParagraphs" should "split paragraphs" in {
    val paras = splitParagraphs(longText.split("\n"), 3, 200).toList
    // log.debug(s"paras = $paras")
    paras.map(x => (x._1, x._2)) should be(List((0, 18), (18, 30)))
  }

  it should "further split long paragraphs" in {
    val paras = splitParagraphs(longText.split("\n"), 3, 8).toList
    // log.debug(s"paras = $paras")
    paras.map(x => (x._1, x._2)) should be(List((0, 8), (8, 16), (16, 18), (18, 26), (26, 30)))
  }
}