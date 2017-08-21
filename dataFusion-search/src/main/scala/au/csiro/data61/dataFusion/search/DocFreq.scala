package au.csiro.data61.dataFusion.search

import java.io.OutputStreamWriter

import scala.collection.mutable.HashSet
import scala.io.Source

import org.apache.lucene.index.{ DirectoryReader, MultiFields }

import com.typesafe.scalalogging.Logger

import DataFusionLucene.{ F_CONTENT, analyzer, docIndex }
import DataFusionLucene.DFSearching.PosDocSearch, PosDocSearch.PosQuery, PosDocSearch.JsonProtocol._
import LuceneUtil.{ directory, termIter, tokenIter }
import Main.CliOption
import au.csiro.data61.dataFusion.common.Timer
import resource.managed
import spray.json.{ pimpAny, pimpString }
import au.csiro.data61.dataFusion.common.Data.Doc
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._

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

  def loadTermSet = {
    val timer = Timer()
    val termSet = new HashSet[String]
    for {
      r <- managed(DirectoryReader.open(directory(docIndex)))
      ti <- termIter(MultiFields.getFields(r).terms(F_CONTENT)) // we could filter this: /^[A-Z](?:['A-Z-]*[A-Z])$/, but there are not too many without filtering
    } termSet += ti.term.utf8ToString
    log.info(s"loadTermSet: ${termSet.size} terms loaded in ${timer.elapsedSecs} secs")
    termSet.toSet
  }
  
  /**
   * true iff termSet contains all tokens in query
   */
  def containsAllTokens(termSet: Set[String], query: String) = {
    val tokens = tokenIter(analyzer, F_CONTENT, query).toList
    log.debug(s"containsAllTokens: analyzed tokens = ${tokens.toList}")
    tokens forall termSet.contains
  }
  
  def filterQuery(c: CliOption) = {
    val termSet = loadTermSet
    for (w <- managed(new OutputStreamWriter(System.out, "UTF-8"))) {
      for (line <- Source.fromInputStream(System.in, "UTF-8").getLines) {
        val q = line.parseJson.convertTo[PosQuery]
        if (containsAllTokens(termSet, q.query)) {
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
    
    val termSet = loadTermSet
    for (w <- managed(new OutputStreamWriter(System.out, "UTF-8"))) {
      for (line <- Source.fromInputStream(System.in, "UTF-8").getLines) {
        val query = clean(line.parseJson.toString)
        if (query.length >= 6 && containsAllTokens(termSet, query)) {
          val q = PosQuery(query, -1L)
          w.write(q.toJson.compactPrint)
          w.write('\n')
        } else log.debug(s"nerToQuery: shorter than 6 chars or not all tokens in index")
      }
    }
  }
  
}