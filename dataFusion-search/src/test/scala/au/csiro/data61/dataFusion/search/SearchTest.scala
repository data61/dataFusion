package au.csiro.data61.dataFusion.search

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import Main.defaultCliOption
import Search.inCsv
import DataFusionLucene.DFSearching.PosDocSearch.PosQuery

class SearchTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  
  "inCsv" should "parse CSV" in {
    val lines = Seq(
      "STRCTRD_FMLY_NM|STRCTRD_GVN_NM|STRCTRD_OTHR_GVN_NM|USTRCTRD_FULL_NM|CLNT_INTRNL_ID",
      "BLOGGS|FREDERICK|A||1",
      "|||COSMIC HOLDINGS INCORPORATED|2",
    )
    val qs = inCsv(defaultCliOption, lines.iterator).toList
    log.debug(s"qs = $qs")
    qs should be(List(PosQuery("BLOGGS FREDERICK A", false, 1), PosQuery("COSMIC HOLDINGS INCORPORATED", true, 2)))
  }
  
}