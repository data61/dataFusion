package au.csiro.data61.dataFusion.common

import spray.json.DefaultJsonProtocol._

object Data {
  case class Text(text: String)
  
  /** pos{Str,End} are token indices
   *  off{Str,End} are character offsets
   *  {pos,off}Str is included, {pos,off}End is excluded (first token/char not included)
   */
  case class Ner(posStr: Int, posEnd: Int, offStr: Int, offEnd: Int, score: Double, text: String, typ: String, impl: String)

  /** metadata key for language code e.g. "en" or "es" */
  val META_LANG_CODE = "language-code"
  val META_LANG_PROB = "language-prob"
  val META_EN_SCORE = "english-score"
  
  case class Embedded(content: Option[String], meta: Map[String, String], ner: List[Ner])
  case class Doc(id: Long, content: Option[String], meta: Map[String, String], path: String, ner: List[Ner], embedded: List[Embedded])

  case class ClientEdgeCount(clntIntrnlId: Long, numEdges: Int)

  object JsonProtocol {
    implicit val textFormat = jsonFormat1(Text)
    implicit val nerFormat = jsonFormat8(Ner)
    implicit val embeddedFormat = jsonFormat3(Embedded)
    implicit val docFormat = jsonFormat6(Doc)
    implicit val clientEdgeCountFormat = jsonFormat2(ClientEdgeCount)
  }
}