package au.csiro.data61.dataFusion.search

import java.io.File

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.SortedMap
import scala.language.implicitConversions

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.{ Document, Field, FieldType }
import org.apache.lucene.index.{ IndexOptions, IndexReader, IndexWriter, IndexWriterConfig, PostingsEnum, Term, TermsEnum }
import org.apache.lucene.search.{ DocIdSetIterator, IndexSearcher, ScoreDoc }
import org.apache.lucene.search.spans.{ SpanCollector, SpanNearQuery, SpanWeight, Spans }
import org.apache.lucene.store.Directory
import org.apache.lucene.util.BytesRef

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Timer
import LuceneUtil.postIter

import spray.json._, DefaultJsonProtocol._

/**
 * dataFusion specific field names, analyzers etc. for Lucene.
 */
object DataFusionLucene {
  private val log = Logger(getClass)
  
  val conf = ConfigFactory.load.getConfig("search")
  
  val Seq(docIndex, metaIndex, nerIndex) = 
    Seq("docIndex", "metaIndex", "nerIndex").map(k => new File(conf.getString(k)))

  val EMB_IDX_MAIN = -1 // a searchable value for embIdx to represent main content - not embedded

  case class LDoc(docId: Long, embIdx: Int, content: String, path: String)
  case class LMeta(docId: Long, embIdx: Int, key: String, `val`: String)
  case class LNer(docId: Long, embIdx: Int, posStr: Int, posEnd: Int, offStr: Int, offEnd: Int, text: String, typ: String, impl: String)
  
  object JsonProtocol {
    implicit val ldocCodec = jsonFormat4(LDoc)
    implicit val lmetaCodec = jsonFormat4(LMeta)
    implicit val lnerCodec = jsonFormat9(LNer)  
  }
  import JsonProtocol._
  
  /** field names */
  val F_DOC_ID = "docId"
  val F_EMB_IDX = "embIdx"
  val F_JSON = "json"
  
  val F_CONTENT = "content"
  val F_PATH = "path"

  val F_KEY = "key"
  val F_VAL = "val"
  
  val F_TEXT = "text"
  val F_TYP = "typ"
  val F_IMPL = "impl"
  
  // searching for names, we don't want stop words or stemming
  val lowerAnalyzer = new Analyzer {
    override protected def createComponents(fieldName: String): TokenStreamComponents = {
      val src = new StandardTokenizer
      new TokenStreamComponents(src, new LowerCaseFilter(src)) 
    }
  }
  val kwAnalyzer = new KeywordAnalyzer
  val analyzer = new PerFieldAnalyzerWrapper(kwAnalyzer, Map(F_CONTENT -> lowerAnalyzer, F_VAL -> lowerAnalyzer, F_TEXT -> lowerAnalyzer).asJava)
    
  object DFIndexing {
    val indexedKeywordType = {
      val t = new FieldType
      t.setIndexOptions(IndexOptions.DOCS)
      t.setTokenized(false)
      t.setStored(false)
      t.freeze();
      t
    }
    
    val jsonType = {
      val t = new FieldType
      t.setIndexOptions(IndexOptions.NONE)
      t.setTokenized(false)
      t.setStored(true)
      t.freeze();
      t
    }
      
    val contentType = {
      val t = new FieldType
      // based on TextField
      t.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
      t.setTokenized(true)
      t.setStored(false)
      // PosDocSearch no longer need term vectors
//      t.setStoreTermVectors(true)
//      t.setStoreTermVectorPositions(true) // token positions (word index/offset)
//      t.setStoreTermVectorOffsets(true) // token character index/offset
      t.freeze();
      t
    }
      
//    val pathType = {
//      val t = new FieldType
//      t.setIndexOptions(IndexOptions.DOCS)
//      t.setTokenized(true)
//      t.setStored(false)
//      t.freeze();
//      t
//    }
      
    val nerTextType = {
      val t = new FieldType
      // based on TextField
      t.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
      t.setTokenized(true)
      t.setStored(false)
      t.freeze();
      t
    }
    val metaTextType = nerTextType
          
    val fieldType = Map(
      F_DOC_ID -> indexedKeywordType,
      F_EMB_IDX -> indexedKeywordType,
      F_JSON -> jsonType,
      F_CONTENT -> contentType,
      F_PATH -> indexedKeywordType,
      
      F_KEY -> indexedKeywordType,
      F_VAL -> metaTextType,
      
      F_TEXT -> nerTextType,
      F_TYP -> indexedKeywordType,
      F_IMPL -> indexedKeywordType
    )
  
    class DocBuilder {
      val d = new Document
      
      def add(f: String, v: String): DocBuilder = {
        d.add(new Field(f, v, fieldType(f)))
        this
      }
      def get = d
    }
    
    object DocBuilder {
      def apply() = new DocBuilder
    }
    
    implicit def ldoc2doc(x: LDoc): Document = DocBuilder()
      .add(F_DOC_ID, x.docId.toString)
      .add(F_EMB_IDX, x.embIdx.toString)
      .add(F_JSON, x.toJson.compactPrint)
      .add(F_CONTENT, x.content)
      .add(F_PATH, x.path)
      .get
    
    implicit def lmeta2doc(x: LMeta): Document = DocBuilder()
      .add(F_DOC_ID, x.docId.toString)
      .add(F_EMB_IDX, x.embIdx.toString)
      .add(F_JSON, x.toJson.compactPrint)
      .add(F_KEY, x.key)
      .add(F_VAL, x.`val`)
      .get
      
    implicit def lner2doc(x: LNer): Document = DocBuilder()
      .add(F_DOC_ID, x.docId.toString)
      .add(F_EMB_IDX, x.embIdx.toString)
      .add(F_JSON, x.toJson.compactPrint)
      .add(F_IMPL, x.impl)
      .add(F_TYP, x.typ)
      .add(F_TEXT, x.text)
      .get
    
    def mkIndexer(dir: Directory) = new IndexWriter(
      dir,
      new IndexWriterConfig(analyzer)
        .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
    )
  }
  
  object DFSearching {
    
    case class Query(query: String, numHits: Int)
    case class Stats(totalHits: Int, elapsedSecs: Float)

    object JsonProtocol {
      implicit val queryCodec = jsonFormat2(Query)
      implicit val statsCodec = jsonFormat2(Stats)
    }
    import JsonProtocol._
    
    def ldoc(doc: Document) = doc.get(F_JSON).parseJson.convertTo[LDoc]
     
    object DocSearch {
      case class DHits(stats: Stats, hits: List[(Float, LDoc)], error: Option[String])
      
      object JsonProtocol {
        implicit val dHitsCodec = jsonFormat3(DHits)
      }
  
      def toHit(scoreDoc: ScoreDoc, doc: Document) = {
        val d = ldoc(doc)
        (scoreDoc.score, d)
       }
      
      def toResult(totalHits: Int, elapsedSecs: Float, hits: Seq[(Float, LDoc)], error: Option[String])
        = DHits(Stats(totalHits, elapsedSecs), hits.toList, error)
    }
    
    object PosDocSearch {
      case class PosQuery(query: String, clnt_intrnl_id: Long)
      case class PosMultiQuery(queries: List[PosQuery])
      case class PosInfo(score: Float, posStr: Int, posEnd: Int, offStr: Int, offEnd: Int, text: String)
      case class LPosDoc(docId: Long, embIdx: Int, posInfos: List[PosInfo], path: String)
      case class PHits(stats: Stats, hits: List[LPosDoc], error: Option[String], query: String, clnt_intrnl_id: Long)
      case class PMultiHits(pHits: List[PHits])
      
      object JsonProtocol {
        implicit val posQueryCodec = jsonFormat2(PosQuery)
        implicit val posMultiQueryCodec = jsonFormat1(PosMultiQuery)
        implicit val posInfoCodec = jsonFormat6(PosInfo)
        implicit val lposDocCodec = jsonFormat4(LPosDoc)
        implicit val pHitsCodec = jsonFormat5(PHits)
        implicit val pMultiHitsCodec = jsonFormat1(PMultiHits)
      }

      class MySpanCollector extends SpanCollector {
        private var minPos = Int.MaxValue
        private var maxPos = Int.MinValue
        private var minOff = Int.MaxValue
        private var maxOff = Int.MinValue
        
        override def reset: Unit = {
          log.info(s"PosDocSearch.MySpanCollector.reset():")
          minPos = Int.MaxValue 
          maxPos = Int.MinValue
          minOff = Int.MaxValue 
          maxOff = Int.MinValue
        }
        
        override def collectLeaf(p: PostingsEnum, pos: Int, t: Term): Unit = {
          log.debug(s"PosDocSearch.MySpanCollector: docId = ${p.docID}, pos = $pos, t = ${t.text}, offsets: str = ${p.startOffset}, end = ${p.endOffset}")
          if (pos < minPos) minPos = pos
          if (pos + 1 > maxPos) maxPos = pos + 1
          if (p.startOffset < minOff) minOff = p.startOffset
          if (p.endOffset > maxOff) maxOff = p.endOffset
        }
        
        def posInfo(score: Float, text: String) = PosInfo(score, minPos, maxPos, minOff, maxOff, text.substring(minOff, maxOff))
      }
      
      def searchSpans(searcher: IndexSearcher, lq: SpanNearQuery, q: PosQuery, numTerms: Int) = {
        val timer = Timer()
        val weight = lq.createWeight(searcher, true) // needsScores
        val collector = new MySpanCollector
        val hits = (for {
          lrc <- searcher.getIndexReader.getContext.leaves.asScala
          spans <- Option(weight.getSpans(lrc, SpanWeight.Postings.OFFSETS)).toList
          // scoring code modified from org.apache.lucene.search.spans.SpanScorer
          docScorer = weight.getSimScorer(lrc)
          docId <- Iterator.continually(spans.nextDoc).takeWhile(_ !=  DocIdSetIterator.NO_MORE_DOCS)
          doc = ldoc(lrc.reader.document(docId)) // docId relative to lrc, not searcher.getIndexReader
          posns = Iterator.continually(spans.nextStartPosition).takeWhile(_ !=  Spans.NO_MORE_POSITIONS).map { _ =>
            val freq = docScorer.computeSlopFactor(spans.width)
            val score = docScorer.score(docId, freq)
            log.debug(s"provide scores: docId = $docId, width = ${spans.width}, freq = $freq, score = $score")
            collector.reset
            spans.collect(collector)
            collector.posInfo(score, doc.content)
          }.filter(p => p.posEnd - p.posStr == numTerms).toList
          // TODO: handle duplicate terms in query - same num terms in query and match
          // e.g. search for "Aaron H Aaron" cannot match "H Aaron"
          // is this good enough? what about "H H H"?
        } yield LPosDoc(doc.docId, doc.embIdx, posns, doc.path)).filter(_.posInfos.nonEmpty).toList
        PHits(Stats(hits.size, timer.elapsedSecs), hits, None, q.query, q.clnt_intrnl_id)
      }
  
      /** TODO: posInfo.score always set to 1.0. Is it faster without scoring? */
      def searchSpansNoScores(searcher: IndexSearcher, lq: SpanNearQuery, q: PosQuery, numTerms: Int) = {
        val timer = Timer()
        val weight = lq.createWeight(searcher, false) // not needsScores
        val collector = new MySpanCollector
        val hits = (for {
          lrc <- searcher.getIndexReader.getContext.leaves.asScala
          spans <- Option(weight.getSpans(lrc, SpanWeight.Postings.OFFSETS)).toList
          docId <- Iterator.continually(spans.nextDoc).takeWhile(_ !=  DocIdSetIterator.NO_MORE_DOCS)
          doc = ldoc(lrc.reader.document(docId)) // docId relative to lrc, not searcher.getIndexReader
          posns = Iterator.continually(spans.nextStartPosition).takeWhile(_ !=  Spans.NO_MORE_POSITIONS)
            .map { _ =>
              collector.reset
              spans.collect(collector)
              collector.posInfo(1.0f, doc.content)
            }.filter(p => p.posEnd - p.posStr == numTerms).toList
            // TODO: handle duplicate terms in query - same num terms in query and match
            // e.g. search for "Aaron H Aaron" cannot match "H Aaron"
            // is this good enough? what about "H H H"?
        } yield LPosDoc(doc.docId, doc.embIdx, posns, doc.path)).filter(_.posInfos.nonEmpty).toList
        PHits(Stats(hits.size, timer.elapsedSecs), hits, None, q.query, q.clnt_intrnl_id)
      }
    }
  
    object MetaSearch {
      case class MHits(stats: Stats, hits: List[(Float, LMeta)], error: Option[String])
      
      object JsonProtocol extends DefaultJsonProtocol {
        implicit val mHitsCodec = jsonFormat3(MHits)
      }
  
      def toHit(scoreDoc: ScoreDoc, doc: Document) = (scoreDoc.score, doc.get(F_JSON).parseJson.convertTo[LMeta])
      
      def toResult(totalHits: Int, elapsedSecs: Float, hits: Seq[(Float, LMeta)], error: Option[String])
        = MHits(Stats(totalHits, elapsedSecs), hits.toList, error)        
    }
  
    object NerSearch {
      case class NHits(stats: Stats, hits: List[(Float, LNer)], error: Option[String])

      object JsonProtocol extends DefaultJsonProtocol {
        implicit val nHitsCodec = jsonFormat3(NHits)
      }
  
      def toHit(scoreDoc: ScoreDoc, doc: Document) = (scoreDoc.score, doc.get(F_JSON).parseJson.convertTo[LNer])
      
      def toResult(totalHits: Int, elapsedSecs: Float, hits: Seq[(Float, LNer)], error: Option[String])
        = NHits(Stats(totalHits, elapsedSecs), hits.toList, error)
        
    }
  }

}