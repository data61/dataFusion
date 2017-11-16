package au.csiro.data61.dataFusion.common

import com.typesafe.scalalogging.Logger

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.typesafe.scalalogging.Logger
import Util._
import scala.util.Random
import Timer.timed

class UtilTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  
  val data = Random.shuffle(1 to 1000000)
  val n = 10
  
  "top" should "get top members quicker than sorting" in {
    val t1 = Timer()
    val topn = top(n, data.iterator)
    t1.stop
    log.debug(s"topn in ${t1.elapsedSecs} = $topn")
    
    val t2 = Timer()
    val expected = data.sortBy(x => -x).take(n).toList
    t2.stop
    log.debug(s"sortBy.take(n) in ${t2.elapsedSecs}")
    topn.reverse should be(expected)
    assert(t1.elapsedSecs < t2.elapsedSecs)
  }
  
  "bottom" should "get bottom members quicker than sorting" in {
    val t1 = Timer()
    val bottomn = bottom(n, data.iterator)
    t1.stop
    log.debug(s"bottomn in ${t1.elapsedSecs} = $bottomn")
    
    val t2 = Timer()
    val expected = data.sorted.take(n).toList
    t2.stop
    log.debug(s"sort.take(n) in ${t2.elapsedSecs}")
    bottomn.reverse should be(expected)
    assert(t1.elapsedSecs < t2.elapsedSecs)
  }
}