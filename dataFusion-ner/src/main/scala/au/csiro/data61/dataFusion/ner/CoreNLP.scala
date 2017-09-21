package au.csiro.data61.dataFusion.ner

import java.util.Properties

import scala.collection.JavaConverters.{ asScalaBufferConverter, propertiesAsScalaMapConverter }
import scala.collection.mutable.ListBuffer

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.Ner
import edu.stanford.nlp.ling.CoreAnnotations.{ SentencesAnnotation, TokensAnnotation }
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.pipeline.Annotator.{ STANFORD_LEMMA, STANFORD_NER, STANFORD_POS, STANFORD_SSPLIT, STANFORD_TOKENIZE }
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import resource.managed

object CoreNLP {
  val log = Logger(getClass)

  object English {
    // use hard-coded default properties for English models
    val nlp = {
      val p = new Properties
      p.setProperty("annotators", Seq(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_LEMMA, STANFORD_NER).mkString(", "))
      val nlp = CoreNLP.synchronized { new StanfordCoreNLP(p, true) } // synchronized else multi-threaded sbt test fails
      log.debug(s"CoreNLP English pipeline initialized with properties: ${p.asScala}")
      nlp
    }
  }
  
//  object Spanish {
//    // use Spanish models (and no lemma annotator)
//    val nlp = managed(getClass.getResourceAsStream("/StanfordCoreNLP-spanish.properties")).map { in =>
//        val p = new Properties
//        p.load(in)
//        p.setProperty("annotators", Seq(STANFORD_TOKENIZE, STANFORD_SSPLIT, STANFORD_POS, STANFORD_NER).mkString(", "))
//        val nlp = CoreNLP.synchronized { new StanfordCoreNLP(p, true) }
//        log.debug(s"CoreNLP Spanish pipeline initialized with properties: ${p.asScala}")
//        nlp
//      }.tried.get
//  }
  
//  def nlp(lang: String) = lang match {
//    case "es" => {
//      // log.debug("Spanish")
//      Spanish.nlp
//    }
//    case _ => {
//      // log.debug("English")
//      English.nlp
//    }
//  }
  def nlp(lang: String) = English.nlp
    
  // convert CoreNLP's Spanish NER class names to the equivalent English class names (leaving English and unknown class names unchanged)
  val fixNerClass = Map("PERS" -> "PERSON", "ORG" -> "ORGANIZATION", "LUG" -> "LOCATION", "OTROS" -> "MISC") withDefault(identity)
  
  case class IdxTok(
    /** word index of start of sentence */
    idx: Int,
    /** word annotation, tok.index is 1 based word index into sentence */
    tok: CoreLabel
  ) {
    def toNer(olast: Option[IdxTok], text: String) = {
      val (posEnd, offEnd) = olast.map { l => (l.idx + l.tok.index, l.tok.endPosition) }.getOrElse((idx + tok.index, tok.endPosition))
      Ner(
        idx + tok.index - 1, 
        posEnd,
        tok.beginPosition,
        offEnd,
        1.0,
        text.substring(tok.beginPosition, offEnd),
        fixNerClass(tok.ner),
        "CoreNLP"
      )
    }
  }
  
  /**
   * returns (Ner's, posEnd) where: posEnd = index of last token + 1
   */
  def nersPosEndOffEnd(lang: String, in: String): (List[Ner], Int) = {
    val NOT_NER = "O" // non-ners including the sentence boundary "." have this value for CoreLabel.ner
  
    // the "get(classOf[" code to pull apart the Annotation object is based on: edu.stanford.nlp.pipeline.XMLOutputter
    var tokenIdx = 0
    val tokens = for {
      sentence <- nlp(lang).process(in).get(classOf[SentencesAnnotation]).asScala
      tokens = sentence.get(classOf[TokensAnnotation])
      idx = tokenIdx
      _ = tokenIdx += tokens.size // start of next sentence
      token <- tokens.asScala
    } yield IdxTok(idx, token)
    
    // filter named entities and merge e.g. LOCATION "New", LOCATION "York" into LOCATION "New York"
    tokens += IdxTok(-1, new CoreLabel) // append a dummy
    val ners = tokens.foldLeft((new ListBuffer[Ner], None: Option[IdxTok], None: Option[IdxTok])) {
        case ((lst, None, _), idxTok) => (lst, Some(idxTok), None)
        case ((lst, optFirst @ Some(first), optLast), idxTok) => {
          if (first.tok.ner == idxTok.tok.ner) // same NE type (may be NOT_NER) so update optLast
            (lst, optFirst, Some(idxTok)) 
          else // else if its really an NE (not NOT_NER) append to list and continue with new NE type
            (if (first.tok.ner != NOT_NER) lst += first.toNer(optLast, in) else lst, Some(idxTok), None)
        }
      }._1.toList
    
    (ners, tokenIdx)
  }
  
  def nerSplitParagraphs(lang: String, in: String, splitmin: Int, splitmax: Int): List[Ner] = {
    Split.splitParagraphs(in.split("\n"), splitmin, splitmax).foldLeft((List.empty[Ner], 0, 0)) { case ((l, pos, off), (lineStr, lineEnd, lines)) =>
      val (ners, nextPos) = nersPosEndOffEnd(lang, lines)
      log.debug(s"nerSplit: lineStr = $lineStr, lineEnd = $lineEnd, pos = $pos, off = $off, nextPos = $nextPos")
      log.debug(s"nerSplit: lines = $lines")
      log.debug(s"nerSplit: ners = $ners")
      val ners2 = ners.map(n => n.copy(posStr = n.posStr + pos, posEnd = n.posEnd + pos, offStr = n.offStr + off, offEnd = n.offEnd + off))
      (l ++ ners2, pos + nextPos, off + lines.size)
    }._1.toList
  }
  
  /**
   * Processing born-digital company reports (same for scanned):
   * 
   * WARN  edu.stanford.nlp.ie.NumberNormalizer - java.lang.NumberFormatException:
   * Bad number put into wordToNumber.  Word is: "150.9million", originally part of "150.9million", piece # 0
   * edu.stanford.nlp.ie.NumberNormalizer.wordToNumber(NumberNormalizer.java:294)
   *
   * Thrown @ wordToNumber(NumberNormalizer.java:294)
	 * caught and logged @ findNumbers(NumberNormalizer.java:646)
	 * so they don’t mess up finding other NEs in the same text.
   * 
   * 13 cases with million, 1 case with billion. Now fixed in modified copy of edu.stanford.nlp.ie.NumberNormalizer.
   * 
   * Time processing 300kB of tab separated values data (from metadata of company reports downloaded from the internet).
   * # flatten all meta data into lines of tab separated text: "${key}\t${value}"
   * jq -r '(.meta | to_entries) + ([ .embedded[].meta | to_entries ] | flatten) | .[] | (.key + "\t" + .value)' tika-reports.json  > meta-reports.tsv
   * head -8000 meta-reports.tsv > tika-300k.json
   * ls 300k.txt | dfus tika --output tika-300k.json
   * time dfus -m 6 ner --opennlp false --mitie false --preprocess false --output ner-300k.json --numWorkers 1  < tika-300k.json
   * 
   * started			models loaded	work complete		
   * 11:40:34.02	11:41:13.81		11:42:01.66			50 – 200 lines
	 * 							39.7869999999	47.857000000003	elapsed secs
	 * 	
	 * 11:52:00.49	11:52:33.94		11:53:21.07			fixed skipping loading opennlp & mitie
	 *							33.4499999999	47.130000000004	elapsed secs	
	 * 
	 * 11:57:44.85	11:58:18.01		11:59:02.00			20 – 60 lines
	 *							33.1590000000	43.99799999999  elasped secs	bit quicker but not dramatically so
   */
  def ner(lang: String, in: String): List[Ner] = nerSplitParagraphs(lang, in, 20, 60) // process 20 - 60 lines at a time
}
