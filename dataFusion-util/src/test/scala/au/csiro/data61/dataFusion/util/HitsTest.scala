package au.csiro.data61.dataFusion.util

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ Doc, EMB_IDX_MAIN, Embedded, ExtRef, IdEmbIdx, LPosDoc, Ner, PHits, PosInfo, Stats, T_PERSON, T_ORGANIZATION }

class HitsTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  
  val id = 31L
  val extRef = ExtRef("Jane", List(123L))
  val score = 9.876f
  val typ = "PERSON"
  val path = "path"
  val content = "I saw SARAH ANNE JONES here!"
  
//    case class Embedded(content: Option[String], meta: Map[String, String], ner: List[Ner])
  val emb = Embedded(Some(content), Map.empty, List.empty)

  //    case class Doc(id: Long, content: Option[String], meta: Map[String, String], path: String, ner: List[Ner], embedded: List[Embedded])
  val doc = Doc(id, Some(content), Map.empty, path, List.empty, List(emb))
  
//    case class PosInfo(posStr: Int, posEnd: Int, offStr: Int, offEnd: Int)
  val pi = PosInfo(1, 4, doc.content.get.indexOf("SARAH"), doc.content.get.indexOf(" here!"))
  
  val expected = Ner(pi.posStr, pi.posEnd, pi.offStr, pi.offEnd, score, content.substring(pi.offStr, pi.offEnd), typ, "D61GAZ", Some(extRef))

  "augment" should "add hit to doc.ner" in {
//    case class LPosDoc(idEmbIdx: IdEmbIdx, posInfos: List[PosInfo])
    val lPosDoc = LPosDoc(IdEmbIdx(id, EMB_IDX_MAIN), List(pi))
    val hits = Seq(PHits(Stats(0, 0), List(lPosDoc), None, extRef, score, typ))
    
    val augment: Doc => Doc = Hits.augment(Hits.hitsMap(hits.iterator))
    val doc2 = augment(doc)
    log.debug(s"doc2 = $doc2")
    doc2.ner.size should be(1)
    doc2.ner(0) should be(expected)
    doc2.embedded.size should be(1)
    doc2.embedded(0).ner.size should be(0)
  }
  
  it should "add hit to doc.embedded.ner" in {
//    case class LPosDoc(idEmbIdx: IdEmbIdx, posInfos: List[PosInfo])
    val lPosDoc = LPosDoc(IdEmbIdx(id, 0), List(pi))
    val hits = Seq(PHits(Stats(0, 0), List(lPosDoc), None, extRef, 9.876f, "PERSON"))
    
    val augment: Doc => Doc = Hits.augment(Hits.hitsMap(hits.iterator))
    val doc2 = augment(doc)
    log.debug(s"doc2 = $doc2")
    doc2.ner.size should be(0)
    doc2.embedded.size should be(1)
    doc2.embedded(0).ner.size should be(1)
    doc2.embedded(0).ner(0) should be(expected)
  }
  
  "termFreq" should "count terms" in {
    Hits.termFreq("Aaron H Aaron") should be(Map("aaron" -> 2, "h" -> 1))
    Hits.qTermFreq("Aaron H Aaron", T_PERSON) should be(Some(Map("aaron" -> 2, "h" -> 1)))
    Hits.qTermFreq("Aaron H Aaron", T_ORGANIZATION) should be(None)
    Hits.qTermFreq("Aaron H Bloggs", T_PERSON) should be(None)
  }
  
  "filterPer2" should "filter PERSON2 within PERSON" in {
    
  }
  
}