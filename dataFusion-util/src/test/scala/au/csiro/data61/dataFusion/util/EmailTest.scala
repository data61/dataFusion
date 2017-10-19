package au.csiro.data61.dataFusion.util

import java.util.regex.Pattern.{ CASE_INSENSITIVE, MULTILINE }
import java.util.regex.Pattern

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger
  

class EmailTest extends FlatSpec with Matchers {
  val log = Logger(getClass)

  val text = """
Some junk before
the headers
From: Ardern Jacinda (Wellington)

To: Bloggs Frederick (Akaroa); Smith
Michael (Ekatahuna); Walters Roger (Pink Floyd)

Cc: Zealand New (Aotearoa)

Bcc: Peters Winston (Wellington)

Subject: Forming Government
in an MMP System

Date: Today

It's Labour ... and Prime Minister Jacinda Ardern.

New Zealand First has crowned Ardern the next prime minister with its decision to back a Labour-led government, which will also need the Green Party to govern.

Ardern will claim the top job after only two and a-half months as Labour leader - and follows her former mentor Helen Clark into the top job.

"""
  def p(re: String) = Pattern.compile(re, MULTILINE | CASE_INSENSITIVE)
  
  val name = "[A-Z'-]+"
  val location = "\\([^)]+\\)"
  val person = s"${name}(?:\\s+${name})*(?:\\s+${location})?"
  val head = "(?:From|To|Cc|Bcc):?\\s+"
  val personList = p(s"^(${head})(${person}(?:;\\s+${person})*)")
  
  val startHead = p("^From:?\\s")
  val endHead = p("^(?:Subject|Date):?\\s")
  
  val nonHeadLine = s"^(?!${head}).*$$"
  val body = p(s"nonHeadLine{10}(nonHeadLine{3})")
  
  "emailParser" should "find names" in {
    val m1 = startHead.matcher(text)
    if (m1.find) {
      val str = m1.start
      val m2 = endHead.matcher(text)
      val end = if (m2.find(str)) m2.start else {
        val m3 = body.matcher(text)
        if (m3.find(str)) m3.start(1) else text.length
      }
      val m = personList.matcher(text.substring(str, end))
      while (m.find) {
        log.debug(s"start = ${m.start}, end = ${m.end}, text = ${m.group}, groupCount = ${m.groupCount}")
        for (i <- 1 to m.groupCount) log.debug(s"group $i = ${m.group(i)}")
      }
    } else log.debug("no From")
  }
  
}