package au.csiro.data61.dataFusion.search

import java.io.OutputStreamWriter

import scala.io.Source
import scala.language.postfixOps
import scala.util.Try
import scala.util.control.NonFatal

import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.MatchAllDocsQuery

import com.typesafe.scalalogging.Logger

import DataFusionLucene.{ F_CONTENT, F_JSON, F_TEXT, F_VAL, analyzer, docIndex, metaIndex, nerIndex }
import DataFusionLucene.DFSearching.{ Query, Stats, DocSearch, PosDocSearch, MetaSearch, NerSearch }
import LuceneUtil.{ Searcher, directory }
import Main.CliOption
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import resource.managed
import spray.json.{ pimpAny, pimpString }


object Search {
  private val log = Logger(getClass)
    
  def toMsg(e: Throwable): Option[String] = Option(e.getCause)
    .map(c => s"${e.getMessage}. Cause: ${c.getMessage}")
    .orElse(Option(e.getMessage))
    .orElse(Some(e.getClass.getName))
    
  object DocSearcher {
    import DocSearch._
    
    val searcher = new Searcher(directory(docIndex), toHit, toResult)
    
    def search(q: Query) = try {
      val qry = if (q.query.length == 0) new MatchAllDocsQuery else new QueryParser(F_CONTENT, analyzer).parse(q.query)
      searcher.search(qry, q.numHits)
    } catch {
      case NonFatal(e) => {
        log.error("DocSearcher error", e)
        DHits(Stats(0, 0.0f), List.empty, toMsg(e))
      }
    }
  }
  
  object PosDocSearcher {
    import PosDocSearch._
    
    val indexSearcher = DocSearcher.searcher.searcher // reuse above IndexSearcher
        
    /**
     * Building the queries programatically rather than with a QueryParser allows us to search for terms that would match
     * QueryParser keywords such as "and".
     */
    def search(slop: Int, posQuery: String, q: PosQuery) = 
      try {
        searchSpans(indexSearcher, slop, posQuery, q)
      } catch {
        case NonFatal(e) => {
          log.error("PosDocSearcher error", e)
          PHits(Stats(0, 0.0f), List.empty, toMsg(e), q.query, q.clnt_intrnl_id)
        }
      }
      
    def multiSearch(slop: Int, posQuery: String, qs: PosMultiQuery) =
      PMultiHits(qs.queries.map { q => PosDocSearcher.search(slop, posQuery, q) })
  }
  
  object MetaSearcher {
    import MetaSearch._
    
    val searcher = new Searcher(directory(metaIndex), toHit, toResult)
    
    def search(q: Query) = {
      try {
        val qry = if (q.query.length == 0) new MatchAllDocsQuery else new QueryParser(F_VAL, analyzer).parse(q.query)
        searcher.search(qry, q.numHits)
      } catch {
        case NonFatal(e) => {
          log.error("MetaSearcher error", e)
          MHits(Stats(0, 0.0f), List.empty, toMsg(e))
        }
      }
    }
  }
  
  object NerSearcher {
    import NerSearch._
    
    val searcher = new Searcher(directory(nerIndex), toHit, toResult)
    
    def search(q: Query) = {
      try {
        val qry = if (q.query.length == 0) new MatchAllDocsQuery else new QueryParser(F_TEXT, analyzer).parse(q.query)
        searcher.search(qry, q.numHits)
      } catch {
        case NonFatal(e) => {
          log.error("NerSearcher error", e)
          NHits(Stats(0, 0.0f), List.empty, toMsg(e))
        }
      }
    }
  }

  /**
   * CLI method to run bulk searches:
   * + way simpler than JSON web service + client (and no timeout issues)
   * + parallelism using doParallel
   */
  def cliPosDocSearch(c: CliOption): Unit = {
    import PosDocSearch.{ PosQuery, PHits }, PosDocSearch.JsonProtocol._
    
    for (w <- managed(new OutputStreamWriter(System.out, "UTF-8"))) {
      val in = Source.fromInputStream(System.in, "UTF-8").getLines.map(_.parseJson.convertTo[PosQuery])
      def work(q: PosQuery): PHits = PosDocSearcher.search(c.slop, c.posQuery, q)
      def out(h: PHits): Unit = {
        w.write(h.toJson.compactPrint)
        w.write('\n')
      }
      doParallel(in, work, out, PosQuery("done", 0L), PHits(Stats(0, 0), List.empty, None, "done", 0L), c.numWorkers)
    }
  }
  
  // TODO: is this needed?
  def cliExportDocIds(c: CliOption): Unit = {
    val ir = DocSearcher.searcher.searcher.getIndexReader
    for (id <- 1 until ir.maxDoc) {
      val json = ir.document(id).get(F_JSON)
      println(json)
    }
  }
  
}
