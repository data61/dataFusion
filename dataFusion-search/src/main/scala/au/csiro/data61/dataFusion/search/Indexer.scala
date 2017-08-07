package au.csiro.data61.dataFusion.search

import scala.io.{ Codec, Source }

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import DataFusionLucene.{ EMB_IDX_MAIN, LDoc, LMeta, LNer, docIndex, metaIndex, nerIndex }
import DataFusionLucene.DFIndexing.{ ldoc2doc, lmeta2doc, lner2doc, mkIndexer }
import LuceneUtil.directory
import Main.CliOption
import au.csiro.data61.dataFusion.common.Data.Doc
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.docFormat
import resource.managed
import spray.json.pimpString

object Indexer {
  private val log = Logger(getClass)
  implicit val codec = Codec.UTF8
  
  /**
   * Reads path names from stdin (one per line) and indexes the named JSON files
   * which must contain an array of DocOut.
   * 
   * TODO: Parallelize to speed up from 30 mins? Use a par collection of filenames (because par collection of docs in a bunch might be smaller than the number of cpus).
   */
  def run(c: CliOption) = {
    val conf = ConfigFactory.load.getConfig("search")

    for {
      docIndexer <- managed(mkIndexer(directory(docIndex)))
      metaIndexer <- managed(mkIndexer(directory(metaIndex)))
      nerIndexer <- managed(mkIndexer(directory(nerIndex)))
    } {
      
      def index(d: Doc): Unit = {
        docIndexer.addDocument(LDoc(d.id, EMB_IDX_MAIN, d.content.getOrElse(""), d.path))
        for {
          (k, v) <- d.meta
        } metaIndexer.addDocument(LMeta(d.id, EMB_IDX_MAIN, k, v))
        for {
          n <- d.ner
        } nerIndexer.addDocument(LNer(d.id, EMB_IDX_MAIN, n.posStr, n.posEnd, n.offStr, n.offEnd, n.text, n.typ, n.impl))
        
        for {
          (e, embIdx) <- d.embedded.zipWithIndex
        } {
          docIndexer.addDocument(LDoc(d.id, embIdx, e.content.getOrElse(""), d.path))
          for {
            (k, v) <- e.meta
          } metaIndexer.addDocument(LMeta(d.id, embIdx, k, v))
          for {
            n <- e.ner
          } nerIndexer.addDocument(LNer(d.id, embIdx, n.posStr, n.posEnd, n.offStr, n.offEnd, n.text, n.typ, n.impl))
        }
      }
      
      for (json <- Source.fromInputStream(System.in).getLines) 
        index(json.parseJson.convertTo[Doc])
    }
  }

}