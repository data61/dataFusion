package au.csiro.data61.dataFusion.search

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data.{ DHits, EMB_IDX_MAIN, IdEmbIdx, LDoc, Stats }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.dHitsCodec
import spray.json.{ pimpAny, pimpString }

class JsonTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  
  val hits = DHits(Stats(1, 0.5f), List((12.3f, LDoc(IdEmbIdx(1, EMB_IDX_MAIN), "some content", "a/path"))), None)
  
  "DocHits" should "ser/deserialize" in {
    val json = hits.toJson.compactPrint
    log.debug(s"json = $json")
    val d2 = json.parseJson.convertTo[DHits]
    d2 should be(hits)
  }
  
}