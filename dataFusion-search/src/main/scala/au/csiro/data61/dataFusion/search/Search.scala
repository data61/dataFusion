package au.csiro.data61.dataFusion.search

import java.util.concurrent.atomic.AtomicInteger

import scala.io.Source
import scala.language.postfixOps
import scala.util.control.NonFatal

import org.apache.commons.io.input.BOMInputStream
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.MatchAllDocsQuery

import com.google.common.hash.BloomFilter
import com.typesafe.scalalogging.Logger

import DataFusionLucene.{ F_CONTENT, F_JSON, F_TEXT, F_VAL, analyzer, docIndex, metaIndex, nerIndex }
import DataFusionLucene.DFSearching.{ DocSearch, MetaSearch, NerSearch, PosDocSearch, Query }
import PosDocSearch.{ PosQuery, T_ORGANIZATION, T_PERSON }
import PosDocSearch.JsonProtocol.posQueryCodec
import LuceneUtil.{ Searcher, directory }
import Main.CliOption
import au.csiro.data61.dataFusion.common.Timer
import au.csiro.data61.dataFusion.common.Data.{ PHits, Stats }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.pHitsCodec
import au.csiro.data61.dataFusion.common.Parallel.{ bufWriter, doParallel }
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
    def search(slop: Int, q: PosQuery) = 
      try {
        searchSpans(indexSearcher, slop, q)
      } catch {
        // TODO: probably wrong to eat exception here, do in Parallel.work instead?
        case NonFatal(e) => {
          log.error("PosDocSearcher error", e)
          PHits(Stats(0, 0.0f), List.empty, toMsg(e), q.query, q.extRefId, 0.0f, q.typ)
        }
      }
      
    def multiSearch(slop: Int, qs: PosMultiQuery) =
      PMultiHits(qs.queries.map { q => PosDocSearcher.search(slop, q) })
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
   * return the indices of the fields for: id, organisation name and person's: family, first given and other given names.
   * @param csvHdr the header line from the CSV file
   */
  def csvHeaderToIndices(c: CliOption, csvHdr: String): Seq[Int] = {
    val hdrs = csvHdr.toUpperCase.split(c.csvDelim)
    val fields = (c.csvId +: c.csvOrg +: c.csvPerson).map(_.toUpperCase)
    val hdrIdx = fields map hdrs.indexOf
    val missing = for ((f, i) <- fields zip hdrIdx if i == -1) yield f
    if (!missing.isEmpty) throw new Exception(s"CSV header is missing fields: ${missing.mkString(",")}")
    hdrIdx
  }
  
  val inTimer = Timer()
  
  /**
   * Map the input CSV lines to PosQuery's
   */
  def inCsv(c: CliOption, iter: Iterator[String]): Iterator[PosQuery] = {
    if (iter.hasNext) {
      val indices = csvHeaderToIndices(c: CliOption, iter.next)
      val alpha = "[A-Z]".r
      val space = "\\s".r
      val nameOKRE = "^[A-Z](?:[' A-Z-]*[A-Z])?$".r
      def nameOK(n: String) = nameOKRE.unapplySeq(n).isDefined // matches
      iter.flatMap { line =>
        inTimer.start
        val data = line.toUpperCase.split(c.csvDelim).toIndexedSeq.padTo(indices.max + 1, "") // data is all upper, but make sure
        val Seq(idStr, org, fam, gvn, oth) = indices.map(data(_).trim)
        val id = idStr.toLong
        
        // TODO: are any one word org names valid? If so we have to do a non-phrase search for them
        val orgQuery = for {
          _ <- alpha.findFirstMatchIn(org)
          _ <- space.findFirstMatchIn(org)
        } yield PosQuery(org, T_ORGANIZATION, id)
        if (org.nonEmpty && orgQuery.isEmpty) log.warn(s"Rejected organisation: clnt_intrnl_id = $idStr, $org")
        
        val perQuery = if (nameOK(fam) && nameOK(gvn) && (oth.isEmpty || nameOK(oth))) Some(PosQuery(s"$fam $gvn $oth", T_PERSON, id)) else None
        if ((fam.nonEmpty || gvn.nonEmpty || oth.nonEmpty) && perQuery.isEmpty) log.warn(s"Rejected person: clnt_intrnl_id = $idStr, family = '$fam', given = '$gvn', other = '$oth'")
        inTimer.stop
        orgQuery.iterator ++ perQuery.iterator
      }
    } else {
      Iterator.empty
    }
  }
  
  /**
   * CLI method to run bulk searches:
   * + way simpler than JSON web service + client (and no timeout issues)
   * + parallelism using doParallel
   */
  def cliPosDocSearch(c: CliOption): Unit = {
    log.info("cliPosDocSearch: start")
    
    val in: Iterator[PosQuery] = {
      val iter = Source.fromInputStream(new BOMInputStream(System.in), "UTF-8").getLines
      if (c.searchJson) iter.map(_.parseJson.convertTo[PosQuery]) else inCsv(c, iter)
    }
    
    val searchCount = new AtomicInteger
    val filterCount = new AtomicInteger
    
    val work: PosQuery => PHits = {

      def workNoFilter(q: PosQuery) = {
        searchCount.incrementAndGet
        PosDocSearcher.search(c.slop, q)
      }
      
      def workFilter(termFilter: BloomFilter[CharSequence])(q: PosQuery) = {
        if (DocFreq.containsAllTokens(termFilter, q.query)) {
          workNoFilter(q)
        } else {
          filterCount.incrementAndGet
          PHits(Stats(0, 0), List.empty, None, q.query, q.extRefId, 0.0f, q.typ)
        }
      }
    
      if (c.filterQuery) workFilter(DocFreq.loadTermFilter(c.maxTerms)) else workNoFilter
    }
      
    for (w <- managed(bufWriter(c.output))) {
      val outTimer = Timer()
      def out(h: PHits): Unit = {
        if (!h.hits.isEmpty) {
          outTimer.start
          w.write(h.toJson.compactPrint)
          w.write('\n')
          outTimer.stop
        }
      }
      
      doParallel[PosQuery, PHits](in, work, out, PosQuery("done", T_ORGANIZATION, 0L), PHits(Stats(0, 0), List.empty, None, "done", 0L, 0.0f, ""), c.numWorkers)
      if (!c.searchJson) log.info(s"cliPosDocSearch: Input CSV thread busy for ${inTimer.elapsedSecs} secs")
      log.info(s"cliPosDocSearch: Output thread busy for ${outTimer.elapsedSecs} secs")
      // With nuWorkers 25, runtime 1m42s, inTimer 12.5s, outTimer 22.5s,
      // so no need to move CSV/reject processing from input thread to workers
      // and no need to move JSON serialization from output thread to workers.
      log.info(s"cliPosDocSearch: performed ${searchCount.get} searches, skipped ${filterCount.get} searches containing a term not in the index")
      log.info(s"cliPosDocSearch: complete")
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
