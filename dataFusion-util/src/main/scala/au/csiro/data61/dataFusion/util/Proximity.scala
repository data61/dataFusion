package au.csiro.data61.dataFusion.util

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.JavaConverters.{ asScalaSetConverter, collectionAsScalaIterableConverter }
import scala.io.Source

import com.typesafe.scalalogging.Logger

import Main.CliOption
import au.csiro.data61.dataFusion.common.Data.{ Doc, EMAIL, Edge, ExtRef, GAZ }
import au.csiro.data61.dataFusion.common.Data.{ Ner, Node, T_ORGANIZATION, T_PERSON, T_PERSON2, WeightMap }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.{ docFormat, edgeFormat, nodeFormat }
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Util.bufWriter
import resource.managed
import spray.json.{ pimpAny, pimpString }

object Proximity {
  private val log = Logger(getClass)

  def fileWithSuffix(f: File, suffix: String) = new File(f.getPath + suffix)

  /**
   * GAZ NERs and EMAIL NERs with no extRef (possibly non-Australian - avoids duplicates with GAZ NERs).
   * Also map all EMAIL typ (FROM|TO|CC|BCC) to a single typ=EMAIL so we only get one node per person.
   */
  def nerFilter(ner: List[Ner]): Iterator[Ner] = {
//    val emailNer = ner.filter(n => n.impl == EMAIL)
//    val offStr = emailNer.view.map(_.offStr).toSet
//    emailNer.iterator ++ ner.view.filter(n => n.impl == GAZ && (n.typ == T_PERSON || n.typ == T_PERSON2 || n.typ == T_ORGANIZATION) && !offStr.contains(n.offStr))
    ner.iterator.filter(n => n.impl == GAZ || (n.impl == EMAIL && n.extRef.isEmpty)).map(n => if (n.impl == EMAIL) n.copy(typ = EMAIL) else n)
  }
  
  def doProximity(cliOption: CliOption) = {
    val prox = new Proximity(cliOption, nerFilter)
    
    val in = Source.fromInputStream(System.in, "UTF-8").getLines
    def work(json: String) = {
      prox.accDoc(json.parseJson.convertTo[Doc])
      "more"
    }
    def out(s: String) = {}
    doParallel(in, work, out, "done", "done", cliOption.numWorkers)
    log.info("load complete")
    
    type JOB = () => String
    
    val job1: JOB = () => {
      for {
        o <- cliOption.output
        w <- managed(bufWriter(fileWithSuffix(o, "node.json")))
        n <- prox.nodeMap.values.asScala
      } {
        w.write(n.toJson.compactPrint)
        w.write('\n')
      }
      "more"
    }
    
    val job2: JOB = () => {
      for {
        o <- cliOption.output
        w <- managed(bufWriter(fileWithSuffix(o, "edge.json")))
        e <- prox.edgeMap.entrySet.asScala
      } {
        w.write(Edge(e.getKey._1, e.getKey._2, e.getValue, GAZ).toJson.compactPrint)
        w.write('\n')
      }
      "more"
    }
    
    val in2 = Iterator(job1, job2)
    def work2(job: JOB) = job()
    doParallel(in2, work2, out, () => "done", "done", Math.min(2, cliOption.numWorkers))
  }
  
  case class NodeKey(name: String, typ: String)
}

/** thread-safe for concurrent accDoc's */
class Proximity(cliOption: CliOption, nerFilter: List[Ner]=> Iterator[Ner]) {
  import Proximity.NodeKey

  val nextId = new AtomicInteger(0)
  val nodeMap = new ConcurrentHashMap[NodeKey, Node]()

  // Scala's concurrent map TrieMap does not have anything like Java's ConcurrentHashMap.compute, which I think makes it rather useless!
  
  def accNode(k: NodeKey, score: Double, extRef: ExtRef): Int =
    nodeMap.computeIfAbsent(k, k => Node(nextId.getAndIncrement, extRef, score, k.typ)).nodeId

  val edgeMap = new ConcurrentHashMap[(Int, Int), WeightMap]
  
  def accEdge(source: Int, target: Int, collection: String, weight: Double): Unit = {
    val k = if (source < target) (source, target) else (target, source)
    edgeMap.compute(k, (k, v) => 
      if (v == null) Map(collection -> (weight, 1)) withDefaultValue (0.0, 0)
      else {
        val (w0, c0) = v(collection)
        v + (collection -> (w0 + weight, c0 + 1))
      }
    )
  }
  
  val collectionRE = cliOption.collectionRe.r
  def collection(path: String) = collectionRE.findFirstMatchIn(path).map(_.group(1)).getOrElse("UNKNOWN")
  
  // used concurrently
  def accDoc(d: Doc): Unit = {
    val cutoff = (cliOption.decay * 5).toInt
    for {
      ners <- nerFilter(d.ner) +: d.embedded.view.map(e => nerFilter(e.ner))
      v = ners.toIndexedSeq.sortBy(_.offStr)
      // _ = log.info(s"v.size = ${v.size}")
      i <- 0 until v.size - 1 // exclude last
      ni = v(i)
      extRefi = ni.extRef.getOrElse(ExtRef(ni.text, List.empty))
      (j, dist) <- (i + 1 until v.size).view.map { j => (j, v(j).offStr - ni.offStr) }.takeWhile(_._2 < cutoff)
      nj = v(j)
      extRefj = nj.extRef.getOrElse(ExtRef(nj.text, List.empty))
    } {
      // log.info(s"$i, $j -> $dist")
      val idi = accNode(NodeKey(extRefi.name, ni.typ), ni.score, extRefi)
      val idj = accNode(NodeKey(extRefj.name, nj.typ), nj.score, extRefj)
      if (idi != idj) accEdge(idi, idj, collection(d.path), Math.exp(-dist/cliOption.decay)) // additive weight (distance = 1/sum(weights))
    }
  }
  
}

