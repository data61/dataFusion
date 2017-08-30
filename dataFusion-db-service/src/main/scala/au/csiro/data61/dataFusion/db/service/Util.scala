package au.csiro.data61.dataFusion.db.service

object Util {
  
  def loader = getClass.getClassLoader // or Thread.currentThread.getContextClassLoader
  
  /** Get a Scala singleton Object.
    * @param fqn object's fully qualified name
    * @return object as type T
    */
  def getObject[T](fqn: String): T = {
    val m = scala.reflect.runtime.universe.runtimeMirror(loader)
    m.reflectModule(m.staticModule(fqn)).instance.asInstanceOf[T]
  }  
}