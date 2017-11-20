package au.csiro.data61.dataFusion.util

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import au.csiro.data61.dataFusion.common.Data._
  
class EmailTest extends FlatSpec with Matchers {
  private val log = Logger(getClass)

  val text = """
Some junk before
the headers
From: Ardern Jacinda (Wellington)

To: Bloggs Frederick (Akaroa); Smith
Michael (Ekatahuna); Walters Roger (Pink Floyd)

Cc: Zealand New (Aotearoa)

Bcc: Peters Winston (Wellington)

Sent: Today

Subject: Forming Government
in an MMP System

It's Labour ... and Prime Minister Jacinda Ardern.

New Zealand First has crowned Ardern the next prime minister with its decision to back a Labour-led government, which will also need the Green Party to govern.

Ardern will claim the top job after only two and a-half months as Labour leader - and follows her former mentor Helen Clark into the top job.

"""
    
  "Email.toNer" should "find names" in {
    val extRef = Some(ExtRef("Jacinda Ardern", List(1, 2)))
    val gazNer = List(Ner(7, 9, 36, 50, 1.0, "Jacinda Ardern", T_PERSON2, GAZ , extRef))
    val ners = Email.toNer(Email.extRef(gazNer))(text).toList
    for (n <- ners) log.debug(s"ner = $n")
    val expected = Seq(
      Ner(7, 10, 36, 63, 1.0, "Ardern Jacinda (Wellington)", "FROM", "D61EMAIL" ,extRef),
      Ner(14, 17, 96, 121, 1.0, "Smith\nMichael (Ekatahuna)", "TO", "D61EMAIL", None)
    )
    for (e <- expected) assert(ners.contains(e))
  }
  
}