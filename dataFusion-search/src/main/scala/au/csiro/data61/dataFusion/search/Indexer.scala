package au.csiro.data61.dataFusion.search

import scala.io.{ Codec, Source }

import org.apache.lucene.index.IndexWriter

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import DataFusionLucene.{ LDoc, LMeta, LNer, docIndex, metaIndex, nerIndex }
import DataFusionLucene.DFIndexing.{ ldoc2doc, lmeta2doc, lner2doc, mkIndexer }
import LuceneUtil.directory
import Main.CliOption
import au.csiro.data61.dataFusion.common.Data.{ EMB_IDX_MAIN, Doc, IdEmbIdx }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.docFormat
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import resource.managed
import spray.json.pimpString

object Indexer {
  private val log = Logger(getClass)
  implicit val codec = Codec.UTF8
  
  def indexer(docIndexer: IndexWriter, metaIndexer: IndexWriter, nerIndexer: IndexWriter)(d: Doc): Unit = {
    val idMain = IdEmbIdx(d.id, EMB_IDX_MAIN)
    docIndexer.addDocument(LDoc(idMain, d.content.getOrElse(""), d.path))
    for {
      (k, v) <- d.meta
    } metaIndexer.addDocument(LMeta(idMain, k, v))
    for {
      n <- d.ner
    } nerIndexer.addDocument(LNer(idMain, n.posStr, n.posEnd, n.offStr, n.offEnd, n.text, n.typ, n.impl))
    
    for {
      (e, embIdx) <- d.embedded.zipWithIndex
    } {
      val idEmb = IdEmbIdx(d.id, embIdx)
      docIndexer.addDocument(LDoc(idEmb, e.content.getOrElse(""), d.path))
      for {
        (k, v) <- e.meta
      } metaIndexer.addDocument(LMeta(idEmb, k, v))
      for {
        n <- e.ner
      } nerIndexer.addDocument(LNer(idEmb, n.posStr, n.posEnd, n.offStr, n.offEnd, n.text, n.typ, n.impl))
    }
  }
  
  /**
   * Reads JSON Doc's from stdin (one per line) and indexes them.
   */
  def run(c: CliOption) = {
    val conf = ConfigFactory.load.getConfig("search")

    for {
      docIndexer <- managed(mkIndexer(directory(docIndex)))
      metaIndexer <- managed(mkIndexer(directory(metaIndex)))
      nerIndexer <- managed(mkIndexer(directory(nerIndex)))
    } {
      val index: Doc => Unit = indexer(docIndexer, metaIndexer, nerIndexer)
      
      var count = 0
      val in: Iterator[String] = Source.fromInputStream(System.in).getLines.map { json =>
        count += 1
        if (count % 1000 == 0) log.info(s"run.in: Queued $count docs ...")
        json
      }
      def work(json: String): Boolean = {
        index(json.parseJson.convertTo[Doc])
        true
      }
      def out(more: Boolean): Unit = ()
      
      doParallel(in, work, out, "", false, c.numWorkers)
      log.info(s"run: complete. Indexed $count docs")
    }
  }

}