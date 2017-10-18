package au.csiro.data61.dataFusion.util

import java.io.{ FileInputStream, InputStream }

import scala.io.Source

import com.typesafe.scalalogging.Logger

import Main.{ CliOption, GAZ, GAZ2 }
import au.csiro.data61.dataFusion.common.Data.{ Doc, EMB_IDX_MAIN, IdEmbIdx }
import au.csiro.data61.dataFusion.common.Data.{ LPosDoc, Ner, PHits, PosInfo, T_PERSON, T_PERSON2 }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.{ docFormat, pHitsCodec }
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Util.bufWriter
import resource.managed
import spray.json.{ pimpAny, pimpString }

object Hits {
  private val log = Logger(getClass)
  
  def doHits(c: CliOption) = {
    for {
      hFile <- c.hits
      hIn <- managed(new FileInputStream(hFile))
      oFile <- c.output
      w <- managed(bufWriter(oFile))
    } {
      val hMap = hitsMap(hitIter(hIn))
      log.info(s"doHits: loaded hits for ${hMap.size} docs/embedded docs")
      val augment: Doc => Doc = augmentWithHits(hMap)

      val in = Source.fromInputStream(System.in, "UTF-8").getLines
      val work: String => String = s => augment(s.parseJson.convertTo[Doc]).toJson.compactPrint
      val out: String => Unit = json => {
        w.write(json)
        w.write('\n')
      }
      doParallel(in, work, out, "done", "done", c.numWorkers)
    }
  }
  
  def hitIter(hIn: InputStream): Iterator[PHits] = Source.fromInputStream(hIn, "UTF-8").getLines.map(_.parseJson.convertTo[PHits])
  
  /** idEmbIdx -> extRefId, score, typ, lposdoc */
  type HitsMap = Map[IdEmbIdx, Seq[(List[Long], Float, String, LPosDoc)]]
  
  def hitsMap(iter: Iterator[PHits]): HitsMap =
    iter.flatMap { x =>
      x.hits.map(lposdoc => (x.extRefId, x.score, x.typ, lposdoc))
    }.toSeq.groupBy(_._4.idEmbIdx)    
  
  def toNer(content: String, pi: PosInfo, extRefId: List[Long], score: Double, typ: String) = 
    if (T_PERSON2 == typ) Ner(pi.posStr, pi.posEnd, pi.offStr, pi.offEnd, score, content.substring(pi.offStr, pi.offEnd), T_PERSON, GAZ2, Some(extRefId))
    else Ner(pi.posStr, pi.posEnd, pi.offStr, pi.offEnd, score, content.substring(pi.offStr, pi.offEnd), typ, GAZ, Some(extRefId))
      
  def augmentWithHits(hs: HitsMap)(d: Doc): Doc = {
    
    def searchNers(content: Option[String], idEmbIdx: IdEmbIdx): Seq[Ner] = for {
      c <- content.toSeq
      hits <- hs.get(idEmbIdx).toSeq
      (extRefId, score, typ, lposdoc) <- hits
      pi <- lposdoc.posInfos
    } yield toNer(c, pi, extRefId, score, typ)
      
    val ner = d.ner ++ searchNers(d.content, IdEmbIdx(d.id, EMB_IDX_MAIN))
    val embedded = d.embedded.zipWithIndex.map { case (e, embIdx) =>
      val ner = e.ner ++ searchNers(e.content, IdEmbIdx(d.id, embIdx))
      e.copy(ner = ner)
    }
    d.copy(ner = ner, embedded = embedded)
  }

}