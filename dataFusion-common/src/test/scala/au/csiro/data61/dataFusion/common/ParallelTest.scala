package au.csiro.data61.dataFusion.common

import scala.collection.mutable.ListBuffer

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger
import scala.util.Success

class ParallelTest extends FlatSpec with Matchers {
  val log = Logger(getClass)

  "Threads" should "do stuff in parallel" in {
    val l = ListBuffer[String]()
    Parallel.doParallel(Iterator.range(0, 1000).map(_.toString), (s: String) => Success(s), (s: String) => l += s, "done", "done", 4)
    l.size should be(1000)
    for {
      (a, b) <- l.map(_.toInt).sortBy(identity).zipWithIndex
    } a should be(b)
  }
}