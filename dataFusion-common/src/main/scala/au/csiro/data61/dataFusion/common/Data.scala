package au.csiro.data61.dataFusion.common

import spray.json.DefaultJsonProtocol._

object Data {
  /** pos{Str,End} are token indices
   *  off{Str,End} are character offsets
   *  {pos,off}Str is included, {pos,off}End is excluded (first token/char not included)
   */
  case class Ner(posStr: Int, posEnd: Int, offStr: Int, offEnd: Int, score: Double, text: String, typ: String, impl: String, extRefId: Option[Long])

  /** metadata key for language code e.g. "en" or "es" */
  val META_LANG_CODE = "language-code"
  val META_LANG_PROB = "language-prob"
  val META_EN_SCORE = "english-score"
  
  case class Embedded(content: Option[String], meta: Map[String, String], ner: List[Ner])
  case class Doc(id: Long, content: Option[String], meta: Map[String, String], path: String, ner: List[Ner], embedded: List[Embedded])

  case class ClientEdgeCount(clntIntrnlId: Long, numEdges: Int)

  val EMB_IDX_MAIN = -1 // a searchable value for embIdx to represent main content - not embedded
  case class IdEmbIdx(id: Long, embIdx: Int)

  case class Stats(totalHits: Int, elapsedSecs: Float)
  case class PosInfo(posStr: Int, posEnd: Int, offStr: Int, offEnd: Int)
  case class LPosDoc(idEmbIdx: IdEmbIdx, posInfos: List[PosInfo])
  case class PHits(stats: Stats, hits: List[LPosDoc], error: Option[String], query: String, extRefId: Long, score: Float, typ: String)
  
  object JsonProtocol {
    implicit val nerFormat = jsonFormat9(Ner)
    implicit val embeddedFormat = jsonFormat3(Embedded)
    implicit val docFormat = jsonFormat6(Doc)
    implicit val clientEdgeCountFormat = jsonFormat2(ClientEdgeCount)
    implicit val statsCodec = jsonFormat2(Stats)
    implicit val idEmbIdxCodec = jsonFormat2(IdEmbIdx)
    implicit val posInfoCodec = jsonFormat4(PosInfo)
    implicit val lposDocCodec = jsonFormat2(LPosDoc)
    implicit val pHitsCodec = jsonFormat7(PHits)
  }
}