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
import DataFusionLucene.DFSearching.{ Query, PosDocSearch, DocSearch, MetaSearch, NerSearch }
import PosDocSearch.PosQuery
import PosDocSearch.JsonProtocol.posQueryCodec
import LuceneUtil.{ Searcher, directory }
import Main.CliOption
import au.csiro.data61.dataFusion.common.Data.{ PHits, Stats, ExtRef, T_ORGANIZATION, T_PERSON, T_PERSON2 }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.pHitsCodec
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Timer
import au.csiro.data61.dataFusion.common.Util.bufWriter
import resource.managed
import spray.json.{ pimpAny, pimpString }
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer

   
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
    def search(slop: Int, q: PosQuery, minScore: Float) = 
      try {
        searchSpans(indexSearcher, slop, q, minScore)
      } catch {
        // TODO: probably wrong to eat exception here, do in Parallel.work instead?
        case NonFatal(e) => {
          log.error("PosDocSearcher error", e)
          PHits(Stats(0, 0.0f), List.empty, toMsg(e), q.extRef, 0.0f, q.typ)
        }
      }
      
    def multiSearch(slop: Int, qs: PosMultiQuery, minScore: Float) =
      PMultiHits(qs.queries.map { q => PosDocSearcher.search(slop, q, minScore) })
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
      
      // single threaded load used to take 20 min using immutable data structs
      val buf = new ArrayBuffer[PosQuery](20000000)
      def add(q: PosQuery) = buf.synchronized {
        buf += q
      }
      
      def work(line: String): List[String] = {
        val data = line.toUpperCase.split(c.csvDelim).toIndexedSeq.padTo(indices.max + 1, "") // data is all upper, but make sure
        val Seq(idStr, org, fam, gvn, oth) = indices.map(data(_).trim)
        val id = List(idStr.toLong)
        
        val warnBuf = new ListBuffer[String]
        
        // TODO: are any one word org names valid? If so we have to do a non-phrase search for them
        if (alpha.findFirstMatchIn(org).isDefined && space.findFirstMatchIn(org).isDefined) add(PosQuery(ExtRef(org, id), T_ORGANIZATION))
        else if (org.nonEmpty) warnBuf += s"Rejected organisation: ${c.csvId} = $idStr, $org"
        
        if (nameOK(fam) && nameOK(gvn) && nameOK(oth)) add(PosQuery(ExtRef(s"$gvn $oth $fam", id), T_PERSON))
        else if (fam.nonEmpty || gvn.nonEmpty || oth.nonEmpty) warnBuf += s"Rejected person for 3 name query: ${c.csvId} = $idStr, family = '$fam', given = '$gvn', other = '$oth'"

        if (c.csvPersonWith2Names) {
          if (nameOK(fam) && nameOK(gvn)) add(PosQuery(ExtRef(s"$gvn $fam", id), T_PERSON2))
          else if (fam.nonEmpty || gvn.nonEmpty) warnBuf += s"Rejected person for 2 name query: ${c.csvId} = $idStr, family = '$fam', given = '$gvn', other = '$oth'"
        }
        
        warnBuf.toList
      }
      
      def out(msgs: List[String]): Unit = for (m <- msgs) log.warn(m)
      
      doParallel(iter, work, out, "done", List("done"), Math.min(4, c.numWorkers)) // 1 worker -> 12.5 min, 2 -> 7.5 min, 4 -> 6.5 min, slower with more
      log.info(s"inCsv: load completed")
      
      val endMarker = PosQuery(ExtRef("done", List.empty), "done")
      buf += endMarker
      val sorted = buf.toArray
      buf.clear
      val cmp = new java.util.Comparator[PosQuery] {
        override def compare(a: PosQuery, b: PosQuery): Int = {
          val i = a.extRef.name.compareTo(b.extRef.name)
          if (i != 0) i else a.typ.compareTo(b.typ)
        }
      }
      java.util.Arrays.parallelSort(sorted, 0, sorted.length - 1, cmp) // don't sort endMarker, ~20 sec compared to 7 min for scala groupBy! 
      log.info(s"inCsv: sort completed")
      
      // manual groupBy, merge sorted items with same query & typ
      val extRefId = new ListBuffer[Long]
      sorted.iterator.sliding(2).flatMap {
        case Seq(a, b) => {
          extRefId ++= a.extRef.ids
          if (a.extRef.name == b.extRef.name && a.typ == b.typ) {
            Iterator.empty
          } else {
            val p = a.copy(extRef = a.extRef.copy(ids = extRefId.toList))
            extRefId.clear
            Iterator.single(p)
          }
        }
        case _ => Iterator.empty
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
        PosDocSearcher.search(c.slop, q, c.minScore)
      }
      
      def workFilter(termFilter: BloomFilter[CharSequence])(q: PosQuery) = {
        if (DocFreq.containsAllTokens(termFilter, q.extRef.name)) {
          workNoFilter(q)
        } else {
          filterCount.incrementAndGet
          PHits(Stats(0, 0), List.empty, None, q.extRef, 0.0f, q.typ)
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
      
      val queryEndMarker = PosQuery(ExtRef("done", List.empty), T_ORGANIZATION)
      val hitsEndMarker = PHits(Stats(0, 0), List.empty, None, ExtRef("done", List.empty), 0.0f, "")
      doParallel(in, work, out, queryEndMarker, hitsEndMarker, c.numWorkers)
      
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
