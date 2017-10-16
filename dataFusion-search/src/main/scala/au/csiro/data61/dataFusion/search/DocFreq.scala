package au.csiro.data61.dataFusion.search

import java.io.OutputStreamWriter

import scala.collection.mutable.HashSet
import scala.io.Source

import org.apache.lucene.index.{ DirectoryReader, MultiFields }

import com.typesafe.scalalogging.Logger

import DataFusionLucene.{ F_CONTENT, analyzer, docIndex }
import DataFusionLucene.DFSearching.PosDocSearch, PosDocSearch.PosQuery, PosDocSearch.JsonProtocol._, PosDocSearch.T_ORGANIZATION
import LuceneUtil.{ directory, termIter, tokenIter }
import Main.CliOption
import au.csiro.data61.dataFusion.common.Timer
import resource.managed
import spray.json.{ pimpAny, pimpString }
import au.csiro.data61.dataFusion.common.Data.Doc
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.nio.charset.Charset

object DocFreq {
  private val log = Logger(getClass)
  
  /**
   * Output docFreq,term
   */
  def writeDocFreqs(c: CliOption) = {
    for {
      r <- managed(DirectoryReader.open(directory(docIndex)))
      ti <- termIter(MultiFields.getFields(r).terms(F_CONTENT))
    } {
      println(s"${ti.docFreq},${ti.term.utf8ToString}")
    }
  }

  def loadTermFilter(expectedInsertions: Int) = {
    val timer = Timer()
    val termFilter = BloomFilter.create(Funnels.stringFunnel(Charset.forName("UTF-8")), expectedInsertions)
    var n = 0
    for {
      r <- managed(DirectoryReader.open(directory(docIndex)))
      ti <- termIter(MultiFields.getFields(r).terms(F_CONTENT)) // we could filter this: /^[A-Z](?:['A-Z-]*[A-Z])$/, but there are not too many without filtering
    } {
      termFilter put ti.term.utf8ToString
      n += 1
    }
    log.info(s"loadTermSet: $n terms loaded in ${timer.elapsedSecs} secs. Max expectedInsertions = $expectedInsertions")
    if (n > expectedInsertions) log.error(s"Exceeded expectedInsertions = $expectedInsertions")
    termFilter
  }
  
  /**
   * true iff termFilter mightContain all the tokens in query
   */
  def containsAllTokens(termFilter: BloomFilter[CharSequence], query: String) = {
    val tokens = tokenIter(analyzer, F_CONTENT, query).toList
    log.debug(s"containsAllTokens: analyzed tokens = ${tokens.toList}")
    tokens forall termFilter.mightContain // if false the filter definitely does not contain the term
  }
  
  def filterQuery(c: CliOption) = {
    val termFilter = loadTermFilter(c.maxTerms)
    for (w <- managed(new OutputStreamWriter(System.out, "UTF-8"))) {
      for (line <- Source.fromInputStream(System.in, "UTF-8").getLines) {
        val q = line.parseJson.convertTo[PosQuery]
        if (containsAllTokens(termFilter, q.query)) {
          w.write(line)
          w.write('\n')
        } else log.debug(s"filterQuery: not all tokens in index")
      }
    }
  }
  
  /**
   * read NER results, filter, write queries
   */
  def nerToQuery(c: CliOption) = {
    val rNonName = "[^A-Za-z.'-]".r
    val rBigSpace = " {2,}".r
    def clean(q: String) = {
      val q2 = rNonName.replaceAllIn(q, " ").trim
      rBigSpace.replaceAllIn(q2, " ")
    }
    
    val termFilter = loadTermFilter(c.maxTerms)
    for (w <- managed(new OutputStreamWriter(System.out, "UTF-8"))) {
      for (line <- Source.fromInputStream(System.in, "UTF-8").getLines) {
        val query = clean(line.parseJson.toString)
        if (query.length >= 6 && containsAllTokens(termFilter, query)) {
          val q = PosQuery(query, T_ORGANIZATION, List.empty)
          w.write(q.toJson.compactPrint)
          w.write('\n')
        } else log.debug(s"nerToQuery: shorter than 6 chars or not all tokens in index")
      }
    }
  }
  
}