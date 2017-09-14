package au.csiro.data61.dataFusion.common

import java.io.{ BufferedWriter, File, FileOutputStream, OutputStreamWriter }
import java.util.concurrent.ArrayBlockingQueue

import scala.util.{ Failure, Success, Try }

import com.typesafe.scalalogging.Logger

object Parallel {
  private val log = Logger(getClass)

  def bufWriter(f: File) = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))
  
  /**
   * One thread does `in`,
   * One thread does `out`,
   * `numWorkers` threads do `work`.
   */
  def doParallel[I, O](in: Iterator[I], work: I => O, out: O => Unit, inDone: I, outDone: O, numWorkers: Int) = {
    val qFactor = 10
    val qSize = numWorkers * qFactor
    val iq = new ArrayBlockingQueue[I](qSize)
    val oq = new ArrayBlockingQueue[Try[O]](qSize)
    
    val iThread = new Thread {
      override def run = {
        in.foreach(i => iq.put(i))
        iq.put(inDone)
      }
    }
    iThread.start
    
    val oThread = new Thread {
      override def run = {
        Iterator.continually(oq.take) takeWhile(_ != Success(outDone)) foreach { _ match {
          case Success(o) => out(o)
          case Failure(e) => log.error("worker exception", e)
        } }
      }
    }
    oThread.start
    
    val workers = (0 until numWorkers).map { i => new Thread {
      override def run = {
        Iterator.continually(iq.take) takeWhile(_ != inDone) foreach { i => oq.put(Try{ work(i) }) }
        iq.put(inDone) // tell another worker
      }        
    } }
    workers.foreach(_.start)
    
    iThread.join
    log.debug("iThread done")
    workers.foreach(_.join)
    log.debug("workers done")
    oq.put(Success(outDone))
    oThread.join
    log.debug("oThread done")
  }

}