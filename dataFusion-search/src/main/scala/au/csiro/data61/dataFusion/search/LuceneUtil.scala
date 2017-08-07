package au.csiro.data61.dataFusion.search

import java.io.{ Closeable, File }

import scala.util.Try

import org.apache.lucene.analysis.{ Analyzer, TokenStream }
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.index.{ DirectoryReader, PostingsEnum, Terms, TermsEnum }
import org.apache.lucene.search.{ IndexSearcher, Query, ScoreDoc }
import org.apache.lucene.store.{ Directory, FSDirectory }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Timer


/**
 * Generic Lucene indexing and searching.
 * 
 * simplified from: https://github.csiro.au/bac003/social-watch/blob/master/analytics/src/main/scala/org/t3as/socialWatch/analytics/LuceneUtil.scala
 */
object LuceneUtil {
  private val log = Logger(getClass)

  def tokenIter(ts: TokenStream): Iterator[String] = {
    ts.reset
    Iterator.continually {
      val more = ts.incrementToken
      if (!more) {
        ts.end
        ts.close
        // log.debug("tokenIter: TokenStream closed")
      }
      more
    }.takeWhile(identity).map(_ => ts.getAttribute(classOf[CharTermAttribute]).toString)
  }

  def tokenIter(analyzer: Analyzer, fieldName: String, text: String): Iterator[String]
    = tokenIter(analyzer.tokenStream(fieldName, text))
    
  def directory(indexDir: File) = FSDirectory.open(indexDir.toPath)
  
  /** unsafe - returns the same TermsEnum but repositioned each iteration */
  def termIter(terms: Terms): Iterator[TermsEnum] = {
    val ti = terms.iterator
    Iterator.continually(ti.next).takeWhile(_ != null).map(_ => ti)
  }
  
  /** unsafe - returns the same PostingsEnum but repositioned each iteration. Int value is position (index of term/word in field). */
  def postIter(p: PostingsEnum): Iterator[(Int, PostingsEnum)] = {
    p.nextDoc
    Iterator.range(0, p.freq).map { _ =>
      val pos = p.nextPosition
      (pos, p)
    }
  }
  
  class Searcher[Hit, Results](
    directory: Directory,
    toHit: (ScoreDoc, Document) => Hit, // convert score and map of fields to Hit
    toResults: (Int, Float, Seq[Hit], Option[String]) => Results // convert totalHits, elapsedSecs, Seq[Hit], Option[error] to Results
  ) extends Closeable {      
    val log = Logger(getClass)

    val searcher = open
    protected def open = new IndexSearcher(DirectoryReader.open(directory))
    
    log.debug(s"Searcher: numDocs = ${searcher.getIndexReader.numDocs}")
        
    def search(q: Query, numHits: Int = 20) = {
      val timer = Timer()
      
      val result = for {
        topDocs <- Try {
          searcher.search(q, numHits)
        }
        hits <- Try {
          topDocs.scoreDocs map { scoreDoc => toHit(scoreDoc, searcher.doc(scoreDoc.doc)) }
        }
      } yield toResults(topDocs.totalHits, timer.elapsedSecs.toFloat, hits, None)
      
      result.recover { case e => toResults(0, timer.elapsedSecs.toFloat, List(), Some(e.getMessage)) }.get
    }
    
    def close = searcher.getIndexReader.close
  }

}