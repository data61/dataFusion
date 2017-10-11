package au.csiro.data61.dataFusion.search

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.RAMDirectory
import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import DataFusionLucene.DFIndexing.{ ldoc2doc, mkIndexer }
import DataFusionLucene.DFSearching.PosDocSearch.{ PosQuery, searchSpans, T_ORGANIZATION, T_PERSON }
import DataFusionLucene._
import LuceneUtil.tokenIter

class DataFusionLuceneTest extends FlatSpec with Matchers {
  val log = Logger(getClass)

  "SynonymAnalyzer" should "work" in {
    // depends on mapping: limited => ltd in synonyms.txt
    tokenIter(synonymAnalyzer, F_CONTENT, "AA AA Pty. Limited").mkString(" ") should be ("aa aa pty ltd")
  }
  
  val doc1 = "doc1: Sarah Jones\nAA AA Pty. Limited"
  val doc2 = "doc2: John Jones\nMs. AA\nMr. AA BB AA"

  def mkTestSearcher = {
    val dir = new RAMDirectory
    val xer = mkIndexer(dir)
    for {
      (content, idx) <- Seq(doc1, doc2).zipWithIndex
    } xer.addDocument(LDoc(IdEmbIdx(idx, -1), content, "path"))
    xer.close
    new IndexSearcher(DirectoryReader.open(dir))
  }
  
  "SpanQuery" should "provide positions" in {
    val searcher = mkTestSearcher
    log.debug(s"numDocs = ${searcher.getIndexReader.numDocs}")
    
    {
      val q = PosQuery("AA AA Proprietary Ltd.", T_ORGANIZATION, 1L)
      val x = searchSpans(searcher, 0, q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      val pi = x.hits.head.posInfos.head
      doc1.substring(pi.offStr, pi.offEnd) should be ("AA AA Pty. Limited")
    }
    
    {
      val q = PosQuery("Jones Sarah", T_PERSON, 2L)
      val x = searchSpans(searcher, 0, q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.score should be(2.1760912f)
      x.hits.head.posInfos.size should be(1)
      
      val pi = x.hits.head.posInfos.head
      doc1.substring(pi.offStr, pi.offEnd) should be ("Sarah Jones") 
    }
    
    {
      val q = PosQuery("AA AA", T_PERSON, 1L)
      val x = searchSpans(searcher, 0, q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      val pi = x.hits.head.posInfos.head
      doc1.substring(pi.offStr, pi.offEnd) should be ("AA AA")
    }
    
    {
      val q = PosQuery("AA AA BB", T_PERSON, 1L)
      val x = searchSpans(searcher, 0, q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      val pi = x.hits.head.posInfos.head
      doc2.substring(pi.offStr, pi.offEnd) should be ("AA BB AA")
    }

    
    {
      val q = PosQuery("AA AA CC", T_PERSON, 1L)
      val x = searchSpans(searcher, 0, q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(0)
    }
    
    {
      // TODO: this is known to fail, single term search is not working
      val q = PosQuery("John", T_PERSON, 1L)
      val x = searchSpans(searcher, 0, q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      val pi = x.hits.head.posInfos.head
      doc2.substring(pi.offStr, pi.offEnd) should be ("JOHN")
    }
  }

}