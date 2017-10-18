package au.csiro.data61.dataFusion.search

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import Main.defaultCliOption
import Search.inCsv
import DataFusionLucene.DFSearching.PosDocSearch.PosQuery
import au.csiro.data61.dataFusion.common.Data.{ T_ORGANIZATION, T_PERSON }

class SearchTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  
  "inCsv" should "parse CSV" in {
    val lines = Seq(
      "Clnt_Intrnl_Id|STRCTRD_FMLY_NM|STRCTRD_GVN_NM|STRCTRD_OTHR_GVN_NM|USTRCTRD_FULL_NM",
      "1|BLOGGS|FREDERICK|A|",
      "2||||COSMIC HOLDINGS INCORPORATED",
    )
    val qs = inCsv(defaultCliOption.copy(csvDelim = '|'), lines.iterator).toList
    log.debug(s"qs = $qs")
    qs should be(List(PosQuery("BLOGGS FREDERICK A", T_PERSON, List(1L)), PosQuery("COSMIC HOLDINGS INCORPORATED", T_ORGANIZATION, List(2L))))
  }
  
}