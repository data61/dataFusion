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
import spray.json.pimpString

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
      System.out.println(s"${ti.docFreq},${ti.term.utf8ToString}")
    }
  }

  def filterQuery(c: CliOption) = {
    val timer = Timer()
    val termSet = new HashSet[String]
    for {
      r <- managed(DirectoryReader.open(directory(docIndex)))
      ti <- termIter(MultiFields.getFields(r).terms(F_CONTENT)) // we could filter this: /^[A-Z](?:['A-Z-]*[A-Z])$/, but there are not too many without filtering
    } termSet += ti.term.utf8ToString
    log.info(s"${termSet.size} terms loaded in ${timer.elapsedSecs} secs")
    
    for (w <- managed(new OutputStreamWriter(System.out, "UTF-8"))) {
      for (line <- Source.fromInputStream(System.in, "UTF-8").getLines) {
        val q = line.parseJson.convertTo[PosQuery]
        val tokens = tokenIter(analyzer, F_CONTENT, q.query).toList
        log.debug(s"filterQuery: analyzed tokens = ${tokens.toList}")
        if (tokens forall termSet.contains) {
          w.write(line)
          w.write('\n')
        } else log.debug(s"filterQuery: not all tokens in index")
      }
    }
  }
  
}