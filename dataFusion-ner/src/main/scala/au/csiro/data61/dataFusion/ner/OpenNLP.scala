package au.csiro.data61.dataFusion.ner

import java.io.InputStream
import java.util.Properties

import scala.collection.JavaConverters._

import com.typesafe.scalalogging.Logger

import edu.stanford.nlp.ling.CoreAnnotations.{ SentencesAnnotation, TokensAnnotation }
import edu.stanford.nlp.pipeline.Annotator.{ STANFORD_SSPLIT, STANFORD_TOKENIZE }
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import opennlp.tools.namefind.{ NameFinderME, TokenNameFinderModel }
import opennlp.tools.sentdetect.{ SentenceDetectorME, SentenceModel }
import opennlp.tools.tokenize.{ TokenizerME, TokenizerModel }
import resource.managed

import au.csiro.data61.dataFusion.common.Data.Ner

object OpenNLP {
   val log = Logger(getClass)
 
  def loadModel[M](path: String, ctor: InputStream => M) = {
    log.info(s"loading OpenNLP model $path")
    managed(getClass.getResourceAsStream(path)).map(ctor).tried.get
  }
    
  // *Model's are thread-safe
  object English {
    val sentence = loadModel("/opennlp-models-1.5/en-sent.bin", in => new SentenceModel(in))
    val tokenizer = loadModel("/opennlp-models-1.5/en-token.bin", in => new TokenizerModel(in))
    val ners = Seq("date", "location", "money", "organization", "percentage", "person", "time").map { typ =>
      loadModel(s"/opennlp-models-1.5/en-ner-${typ}.bin", in => new TokenNameFinderModel(in))
    }
  }
  
//  object Spanish {
//    /** Spanish sentence & tokenizer models used in training Spanish NameFinder models are not available,
//      * so use CoreNLP (just for this) and hope it is not too different!
//      */
//    val coreNLP = managed(getClass.getResourceAsStream("/StanfordCoreNLP-spanish.properties")).map { in =>
//        val p = new Properties
//        p.load(in)
//        p.setProperty("annotators", Seq(STANFORD_TOKENIZE, STANFORD_SSPLIT).mkString(", "))
//        CoreNLP.synchronized { new StanfordCoreNLP(p, true) } // synchronized else multi-threaded sbt test fails
//      }.tried.get
//      
//    val ners = Seq("location", "organization", "person", "misc").map { typ =>
//      loadModel(s"/opennlp-models-1.5/es-ner-${typ}.bin", in => new TokenNameFinderModel(in))
//    }
//  }
  
  /**
   * Not thread-safe 
   */
  class EnOpenNLP {
    // because *ME's are not thread-safe
    val sent = new SentenceDetectorME(English.sentence)
    val tok = new TokenizerME(English.tokenizer)
    val ners = English.ners.map(new NameFinderME(_))
    
    def ner(in: String): List[Ner] = {
      var tokenIdx = 0;
      val r = for {
        sentencePos <- sent.sentPosDetect(in)
        sentence = in.substring(sentencePos.getStart, sentencePos.getEnd)
        pos = tok.tokenizePos(sentence)
        tIdx = tokenIdx
        _ = tokenIdx += pos.size // start of next sentence
        tokens = pos.map(s => sentence.substring(s.getStart, s.getEnd))
        ner <- ners
        s <- ner.find(tokens)
        start = sentencePos.getStart + pos(s.getStart).getStart
        end = sentencePos.getStart + pos(s.getEnd - 1).getEnd
      } yield Ner(tIdx + s.getStart, tIdx + s.getEnd, start, end, s.getProb, in.substring(start, end), s.getType.toUpperCase, "OpenNLP")
      
      ners.foreach(_.clearAdaptiveData)
      r.toList
    }
  }
  val enOpenNLP = new ThreadLocal[EnOpenNLP] {
    override protected def initialValue = new EnOpenNLP
  }
  
      
  /**
   * Not thread-safe 
   */
//  class EsOpenNLP {
//    // because *ME are not thread-safe
//    val ners = Spanish.ners.map(new NameFinderME(_))
//    
//    def ner(in: String): List[Ner] = {
//      var tokenIdx = 0;
//      val r = for {
//        sentence <- Spanish.coreNLP.process(in).get(classOf[SentencesAnnotation]).asScala
//        tokens = sentence.get(classOf[TokensAnnotation]).asScala.toArray
//        tIdx = tokenIdx
//        _ = tokenIdx += tokens.size // token index of start of next sentence
//        ner <- ners
//        s <- ner.find(tokens.map(_.originalText))
//        start = tokens(s.getStart).beginPosition
//        end = tokens(s.getEnd - 1).endPosition
//      } yield Ner(tIdx + s.getStart, tIdx + s.getEnd, start, end, s.getProb, in.substring(start, end), s.getType.toUpperCase, "OpenNLP")
//      
//      ners.foreach(_.clearAdaptiveData)
//      r.toList
//    }
//  }
//  val esOpenNLP = new ThreadLocal[EsOpenNLP] {
//    override protected def initialValue = new EsOpenNLP
//  }
  
  /** thread-safe */
//  def ner(lang: String, in: String): List[Ner] =
//    lang match {
//      case "es" => {
//        // log.debug("Spanish")
//        esOpenNLP.get.ner(in)
//      }
//      case _ => {
//        // log.debug("English")
//        enOpenNLP.get.ner(in)
//      }
//    }
  def ner(lang: String, in: String): List[Ner] = enOpenNLP.get.ner(in)
}
