package au.csiro.data61.dataFusion.search

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.RAMDirectory
import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import DataFusionLucene.DFIndexing.{ ldoc2doc, mkIndexer }
import DataFusionLucene.DFSearching.PosDocSearch.{ PosQuery, searchSpans }
import DataFusionLucene.LDoc

class DataFusionLuceneTest extends FlatSpec with Matchers {
  val log = Logger(getClass)

  def mkTestSearcher = {
    val dir = new RAMDirectory
    val xer = mkIndexer(dir)
    for {
      (content, idx) <- Seq("doc1: Sarah Jones\nMr. AA AA", "doc2: John Jones\nMs. AA\nMr. AA BB AA").zipWithIndex
    } xer.addDocument(LDoc(idx, -1, content, "path"))
    xer.close
    new IndexSearcher(DirectoryReader.open(dir))
  }
  
  "SpanQuery" should "provide positions" in {
    val searcher = mkTestSearcher
    log.debug(s"numDocs = ${searcher.getIndexReader.numDocs}")
    
    {
      val q = PosQuery("AA AA", 1L)
      val x = searchSpans(searcher, 0, "ord", q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      x.hits.head.posInfos.head.text should be ("AA AA")
    }
    
    {
      val q = PosQuery("Jones Sarah", 2L)
      val x = searchSpans(searcher, 0, "unord", q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      
      val pinf = x.hits.head.posInfos.head
      pinf.text should be ("Sarah Jones") 
      pinf.score should be(0.43184555f)
      pinf.text should be ("Sarah Jones") 
    }
    
    {
      val q = PosQuery("AA AA", 1L)
      val x = searchSpans(searcher, 0, "unord", q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      x.hits.head.posInfos.head.text should be ("AA AA")
    }
    
    {
      val q = PosQuery("AA AA BB", 1L)
      val x = searchSpans(searcher, 0, "unord", q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      x.hits.head.posInfos.head.text should be ("AA BB AA")
    }

    
    {
      val q = PosQuery("AA AA CC", 1L)
      val x = searchSpans(searcher, 0, "unord", q)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(0)
    }
  }

}