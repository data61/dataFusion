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
    
    val nerCmp = new Comparator[Ner] {
      override def compare(a: Ner, b:Ner) = {
        val x = a.offEnd - b.offEnd
        if (x != 0) x else a.offStr - b.offStr
      }
    }

    def filterPer2(ners: Seq[Ner]): Seq[Ner] = {
      val per = {
        val a = ners.view.filter(n => n.impl == GAZ && n.typ == T_PERSON).toArray
        Arrays.sort(a, nerCmp)
        a
      }
      
      def pred(n: Ner): Boolean = n.typ != T_PERSON2 || {
        val i = Arrays.binarySearch(per, n, nerCmp)
        if (i >= 0) {
          false // exact match found
        } else {
          val j  = -i - 1 // insertion point, 1st item > n
          // compare items while per(j).offStr <= n.offStr && per(j).offEnd >= n.offEnd
          // if any such item false else true
          log.debug(s"filterPer2.pred: insertion point j = $j, per.length = ${per.length}, n = $n}")
          if (j < per.length) log.debug(s"filterPer2.pred: per(j) = ${per(j)}")
          j < per.length && per(j).offStr <= n.offStr
        }
      }
      
      ners filter pred
    }
    
    def newNers(content: Option[String], idEmbIdx: IdEmbIdx): Seq[Ner] = filterPer2(searchNers(content, idEmbIdx))
    
    val ner = d.ner ++ newNers(d.content, IdEmbIdx(d.id, EMB_IDX_MAIN))
    val embedded = d.embedded.zipWithIndex.map { case (e, embIdx) =>
      val ner = e.ner ++ newNers(e.content, IdEmbIdx(d.id, embIdx))
      e.copy(ner = ner)
    }
    d.copy(ner = ner, embedded = embedded)
  }

}