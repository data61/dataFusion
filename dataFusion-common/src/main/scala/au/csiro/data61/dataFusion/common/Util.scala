package au.csiro.data61.dataFusion.common

import java.io.{ BufferedWriter, File, FileOutputStream, OutputStreamWriter }
import com.typesafe.scalalogging.Logger
import scala.collection.mutable.ListBuffer

object Util {
  private val log = Logger(getClass)
  
  /** @return a BufferedWriter using UTF-8 encoding */
  def bufWriter(f: File) = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))
  
  /**
   * Modified from: https://stackoverflow.com/questions/5674741/simplest-way-to-get-the-top-n-elements-of-a-scala-iterable
   * Well the simplest is sort.take(n), but for a large collection where n << the collection size, this is much more efficient!
   */
  def extremeN[T](n: Int, it: Iterator[T])(comp1: ((T, T) => Boolean), comp2: ((T, T) => Boolean)): List[T] = {

    def sortedIns (el: T, list: List[T]): List[T] = 
      if (list.isEmpty) List (el) else 
      if (comp2 (el, list.head)) el :: list else 
        list.head :: sortedIns (el, list.tail)
  
    def updateSofar (sofar: List [T], el: T) : List [T] =
      if (comp1 (el, sofar.head)) 
        sortedIns (el, sofar.tail)
      else sofar

    val initN = {
      val buf = new ListBuffer[T]
      for (_ <- 0 until n if it.hasNext) buf += it.next
      buf.toList
    }
    if (initN.size > 1) (initN.sortWith(comp2) /: it) { updateSofar } else initN
  }
  
  /** @return smallest n elements in descending order */
  def bottom[T](n: Int, it: Iterator[T])(implicit ord: Ordering[T]): List[T] = extremeN(n, it)(ord.lt, ord.gt)

  /** @return largest n elements in ascending order */
  def top[T](n: Int, it: Iterator[T])(implicit ord: Ordering[T]): List[T] = extremeN(n, it)(ord.gt, ord.lt)
}