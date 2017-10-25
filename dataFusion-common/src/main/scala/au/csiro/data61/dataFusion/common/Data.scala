package au.csiro.data61.dataFusion.common

import spray.json.DefaultJsonProtocol._

object Data {
  val T_PERSON = "PERSON"
  val T_PERSON2 = "PERSON2"           // PHits.typ for a search on just family & first given names (not using other)
  val T_ORGANIZATION = "ORGANIZATION" // Z is consistent with NER implementations
  
  val GAZ = "D61GAZ"     // Ner.impl for search hits
  val EMAIL = "D61EMAIL" // Ner.impl for names parsed from email headers
      
  /** pos{Str,End} are token indices
   *  off{Str,End} are character offsets
   *  {pos,off}Str is included, {pos,off}End is excluded (first token/char not included)
   */
  case class ExtRef(name: String,  ids: List[Long])
  case class Ner(posStr: Int, posEnd: Int, offStr: Int, offEnd: Int, score: Double, text: String, typ: String, impl: String, extRef: Option[ExtRef])

  /** metadata key for language code e.g. "en" or "es" */
  val META_LANG_CODE = "language-code"
  val META_LANG_PROB = "language-prob"
  val META_EN_SCORE = "english-score"
  
  case class Embedded(content: Option[String], meta: Map[String, String], ner: List[Ner])
  case class Doc(id: Long, content: Option[String], meta: Map[String, String], path: String, ner: List[Ner], embedded: List[Embedded])

  case class Node(nodeId: Int, extRef: ExtRef, typ: String)
  case class Edge(source: Int, target: Int, distance: Float, typ: String)  
  case class ClientEdgeCount(clntIntrnlId: Long, numEdges: Int)

  val EMB_IDX_MAIN = -1 // a searchable value for embIdx to represent main content - not embedded
  case class IdEmbIdx(id: Long, embIdx: Int)

  case class Stats(totalHits: Int, elapsedSecs: Float)
  case class PosInfo(posStr: Int, posEnd: Int, offStr: Int, offEnd: Int)
  case class LPosDoc(idEmbIdx: IdEmbIdx, posInfos: List[PosInfo])
  case class PHits(stats: Stats, hits: List[LPosDoc], error: Option[String], extRef: ExtRef, score: Float, typ: String)
  
  object JsonProtocol {
    implicit val extRefFormat = jsonFormat2(ExtRef)
    implicit val nerFormat = jsonFormat9(Ner)
    implicit val embeddedFormat = jsonFormat3(Embedded)
    implicit val docFormat = jsonFormat6(Doc)
    implicit val nodeFormat = jsonFormat3(Node)
    implicit val edgeFormat = jsonFormat4(Edge)
    implicit val clientEdgeCountFormat = jsonFormat2(ClientEdgeCount)
    implicit val statsCodec = jsonFormat2(Stats)
    implicit val idEmbIdxCodec = jsonFormat2(IdEmbIdx)
    implicit val posInfoCodec = jsonFormat4(PosInfo)
    implicit val lposDocCodec = jsonFormat2(LPosDoc)
    implicit val pHitsCodec = jsonFormat6(PHits)
  }
}