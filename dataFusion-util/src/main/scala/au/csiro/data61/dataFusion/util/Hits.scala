package au.csiro.data61.dataFusion.util

import java.io.InputStream

import scala.io.Source

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data._
import au.csiro.data61.dataFusion.common.Data.{ LPosDoc, Ner, PHits, PosInfo }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.pHitsCodec
import spray.json.pimpString
import scala.annotation.tailrec

import au.csiro.data61.dataFusion.search.DataFusionLucene._
import au.csiro.data61.dataFusion.search.LuceneUtil._
import java.util.Comparator
import java.util.Arrays

/**
 * In dataFusion-search the phrase search for PERSON|PERSON2, with terms in any order, can make some incorrect matches.
 * This happens when the query contains repeated tokens e.g. "Aaron H Aaron" in which case text "H H Aaron" will match.
 * This is corrected here by checking that matches have the same term frequencies as the query.
 * Unfortunately this check cannot be done at search time in dataFusion-search because fetching the text at that point would
 * negatively impact performance (not an issue here because we already have the text).
 * 
 * A dependency on dataFusion-search has been added so that we can use the same term tokenization as in the search.
 */
object Hits {
  private val log = Logger(getClass)
  
  def hitIter(hIn: InputStream): Iterator[PHits] = Source.fromInputStream(hIn, "UTF-8").getLines.map(_.parseJson.convertTo[PHits])
  
  /** idEmbIdx -> extRefId, score, typ, lposdoc */
  type HitsMap = Map[IdEmbIdx, Seq[(ExtRef, Float, String, LPosDoc)]]
  
  def hitsMap(iter: Iterator[PHits]): HitsMap =
    iter.flatMap { x =>
      x.hits.map(lposdoc => (x.extRef, x.score, x.typ, lposdoc))
    }.toSeq.groupBy(_._4.idEmbIdx)    
  
  def termFreq(t: String) = tokenIter(analyzer, F_CONTENT, t).toList.groupBy(identity).map { case (t, lst) => (t, lst.size) }
    
  /**
   * @return Some(termFreq) if the hits need to be checked against this query term freq (a PERSON|PERSON2 search with repeated terms) 
   */
  def qTermFreq(t: String, typ: String) =
    if (typ == T_ORGANIZATION) None // not needed for "terms in order" search
    else {
      val tf = termFreq(t)
      if (tf.values.exists(_ > 1)) Some(tf)
      else None // not needed if no duplicate terms
    }

  def toNer(text: String, pi: PosInfo, extRef: ExtRef, score: Double, typ: String) = 
    Ner(pi.posStr, pi.posEnd, pi.offStr, pi.offEnd, score, text, typ, GAZ, Some(extRef))
      
/**
 * find if any PERSON NER overlaps with a PERSON2
 * A----------B        n1 = PERSON
 *       C--------D    n2 = PERSON2
 * no overlap = D < A or B < C
 * overlap = D >= A and B >= C
 * A & C are offStr; B & D are offEnd - 1 because offEnd is exclusive (1 past the end)
 * So overlap = n2.offEnd - 1 >= n1.offStr && n1.offEnd - 1 >= n2.offStr
 *            = n2.offEnd > n1.offStr && n1.offEnd > n2.offStr
 *            
 * Sort n1 = PERSON on offEnd asc
 * Binary search to find first n1: n1.offEnd > n2.offStr (the bit after &&)
 * Scan n1's until n2.offEnd < n1.offStr for overlap
 */
  val nerCmp = new Comparator[Ner] {
    override def compare(a: Ner, b:Ner) = a.offEnd - b.offEnd
  }
  
  def filterPer2(ners: Seq[Ner]): Seq[Ner] = {
    val per = {
      val a = ners.view.filter(n => n.impl == GAZ && n.typ == T_PERSON).toArray
      Arrays.sort(a, nerCmp)
      log.debug(s"filterPer2.per: ${a.toList}")
      a
    }
    
    def pred(n: Ner): Boolean = n.typ != T_PERSON2 || {
      val i = Arrays.binarySearch(per, n.copy(offEnd = n.offStr + 1), nerCmp) // find 1st per(j).offEnd > n.offStr (>= n.offStr + 1)
      val j = if (i >= 0) i else -(i + 1)
      val overlaps = j < per.length && per(j).offStr < n.offEnd // assume per(j)'s don't overlap so no need to scan
      log.debug(s"i = $i, j = $j, overlaps = $overlaps, n = $n")
      !overlaps
    }
    
    ners filter pred
  }
    
  def augment(hs: HitsMap): Doc => Doc = { d =>
    
    def searchNers(content: Option[String], idEmbIdx: IdEmbIdx): Seq[Ner] = for {
      c <- content.toSeq
      hits <- hs.get(idEmbIdx).toSeq
      (extRefId, score, typ, lposdoc) <- hits
      qtf = qTermFreq(extRefId.name, typ) // query: term -> freq but only if it needs to be checked
      pi <- lposdoc.posInfos
      text = c.substring(pi.offStr, pi.offEnd)
      ok <- qtf.map(_ == termFreq(text)).orElse(Some(true)) if ok // skip if there's a term freq mismatch
    } yield toNer(text, pi, extRefId, score, typ)
    
    def newNers(content: Option[String], idEmbIdx: IdEmbIdx): Seq[Ner] = filterPer2(searchNers(content, idEmbIdx))
    
    val ner = d.ner ++ newNers(d.content, IdEmbIdx(d.id, EMB_IDX_MAIN))
    val embedded = d.embedded.zipWithIndex.map { case (e, embIdx) =>
      val ner = e.ner ++ newNers(e.content, IdEmbIdx(d.id, embIdx))
      e.copy(ner = ner)
    }
    d.copy(ner = ner, embedded = embedded)
  }

}