package au.csiro.data61.dataFusion.search

import scala.collection.mutable.ListBuffer

import org.apache.lucene.index.{ DirectoryReader, Term }
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.spans.{ SpanNearQuery, SpanTermQuery }
import org.apache.lucene.store.RAMDirectory
import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import LuceneUtil.{ directory, tokenIter }
import DataFusionLucene._
import DataFusionLucene.DFIndexing._
import DataFusionLucene.DFSearching.PosDocSearch.{ PosQuery, searchSpans }
import org.apache.lucene.document.Document

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
      val queryTerms = tokenIter(analyzer, F_CONTENT, q.query).toList
      log.debug(s"SpanQuery: queryTerms = $queryTerms")
      val qs = queryTerms.map(t => new SpanTermQuery(new Term(F_CONTENT, t)))
      val lq = new SpanNearQuery(qs.toArray, 0, true) // ordered
      val x = searchSpans(searcher, lq, q, qs.size)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      x.hits.head.posInfos.head.text should be ("AA AA")
    }
    
    {
      val q = PosQuery("Jones Sarah", 2L)
      val queryTerms = tokenIter(analyzer, F_CONTENT, q.query).toList
      log.debug(s"SpanQuery: queryTerms = $queryTerms")
      val qs = queryTerms.map(t => new SpanTermQuery(new Term(F_CONTENT, t)))
      val lq = new SpanNearQuery(qs.toArray, 0, false) // not ordered
      val x = searchSpans(searcher, lq, q, qs.size)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      x.hits.head.posInfos.head.text should be ("Sarah Jones") 
    }
    
    {
      val q = PosQuery("AA AA", 1L)
      val queryTerms = tokenIter(analyzer, F_CONTENT, q.query).toList
      log.debug(s"SpanQuery: queryTerms = $queryTerms")
      val qs = queryTerms.map(t => new SpanTermQuery(new Term(F_CONTENT, t)))
      val lq = new SpanNearQuery(qs.toArray, 0, false) // not ordered
      val x = searchSpans(searcher, lq, q, qs.size)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      x.hits.head.posInfos.head.text should be ("AA AA")
    }
    
    {
      val q = PosQuery("AA AA BB", 1L)
      val queryTerms = tokenIter(analyzer, F_CONTENT, q.query).toList
      log.debug(s"SpanQuery: queryTerms = $queryTerms")
      val qs = queryTerms.map(t => new SpanTermQuery(new Term(F_CONTENT, t)))
      val lq = new SpanNearQuery(qs.toArray, 0, false) // not ordered
      val x = searchSpans(searcher, lq, q, qs.size)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(1)
      x.hits.size should be(1)
      x.hits.head.posInfos.size should be(1)
      x.hits.head.posInfos.head.text should be ("AA BB AA")
    }

    
    {
      val q = PosQuery("AA AA CC", 1L)
      val queryTerms = tokenIter(analyzer, F_CONTENT, q.query).toList
      log.debug(s"SpanQuery: queryTerms = $queryTerms")
      val qs = queryTerms.map(t => new SpanTermQuery(new Term(F_CONTENT, t)))
      val lq = new SpanNearQuery(qs.toArray, 0, false) // not ordered
      val x = searchSpans(searcher, lq, q, qs.size)
      log.debug(s"SpanQuery: x = $x")
      x.stats.totalHits should be(0)
    }
  }

}