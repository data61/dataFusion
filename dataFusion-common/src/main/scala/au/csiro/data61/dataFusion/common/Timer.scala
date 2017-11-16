package au.csiro.data61.dataFusion.common

import com.typesafe.scalalogging.Logger

/** Accumulate time since constructed or reset.
 *  
 *  Usage:
 *  {{{
 *  val t = Timer()
 *  ...
 *  t.stop
 *  log.info(s"... took ${t.elapsedSecs} secs")
 *  }}}
 */
class Timer {
  private var t0 = 0L      // start of currently measured time period
  private var elapsed = 0L // sum of previous time periods ended by stop/elapsedSecs

  reset

  def reset = {
    elapsed = 0L
    start
  }

  /** `start` need not be used - used to discard (not accumulate) the time between `stop` and `start`. */
  def start = t0 = System.currentTimeMillis

  def stop = elapsed += (System.currentTimeMillis - t0)

  /** Get accumulated seconds up to `stop` */
  def elapsedSecs: Float = elapsed * 1e-3f
}

object Timer {
  
  private lazy val log = Logger(getClass)
  
  def apply() = new Timer()
  
  /** Log elapsed time as info.
   *  
   *  Usage:
   *  {{{
   *  val a: A = timed("it took {} secs") {
   *     ...
   *     new A()
   *  }
   *  }}}
   *  
   *  @param msg contains "{}" which is replaced by the elapsed time in secs
   *  @param action thunk to execute and time
   */
  def timed[T](msg: String)(action: => T) = {
    val t = Timer()
    val x = action
    t.stop
    log.info(msg, t.elapsedSecs.toString)
    x
  }
}