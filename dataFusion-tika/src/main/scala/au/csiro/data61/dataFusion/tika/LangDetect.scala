package au.csiro.data61.dataFusion.tika

import com.optimaize.langdetect.{ LanguageDetector, LanguageDetectorBuilder }
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.profiles.LanguageProfileReader
import com.optimaize.langdetect.text.CommonTextObjectFactories

object LangDetect {
  case class Lang(lang: String, prob: Float)
  
  val languageProfiles = new LanguageProfileReader().readAllBuiltIn
  val languageDetector: LanguageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard).withProfiles(languageProfiles).build
  val textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText
  
  def headOption[T](jl: java.util.List[T]): Option[T] = if (jl.isEmpty) None else Some(jl.get(0))

  def lang(text: String): Option[Lang] = {
    headOption(languageDetector.getProbabilities(textObjectFactory.forText(text)))
      .map(l => Lang(l.getLocale.getLanguage, l.getProbability.toFloat))
  }
}
  
