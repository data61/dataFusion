package au.csiro.data61.dataFusion.search

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import Main.defaultCliOption
import Search.inCsv
import au.csiro.data61.dataFusion.common.Data.{ ExtRef, PosQuery, T_ORGANIZATION, T_PERSON, T_PERSON2 }

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
    val x1 = PosQuery(ExtRef("FREDERICK A BLOGGS", List(1L)), T_PERSON)
    val x2 = PosQuery(ExtRef("FREDERICK BLOGGS", List(1L)), T_PERSON2)
    val x3 = PosQuery(ExtRef("COSMIC HOLDINGS INCORPORATED", List(2L)), T_ORGANIZATION)
    qs.toSet should be(Set(x1, x2, x3)) // inCsv is parallelized so results not ordered
  }
  
}