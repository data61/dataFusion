package au.csiro.data61.dataFusion.util

import java.io.File

import scala.collection.mutable
import scala.io.Source

import com.typesafe.scalalogging.Logger

import Main.CliOption
import au.csiro.data61.dataFusion.common.Data._
import au.csiro.data61.dataFusion.common.Data.JsonProtocol._
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Util.bufWriter
import resource.managed
import spray.json.{ pimpAny, pimpString }

object Proximity {
  private val log = Logger(getClass)

  def fileWithSuffix(f: File, suffix: String) = new File(f.getPath + suffix)

  def doProximity(cliOption: CliOption) = {
    val prox = new Proximity(cliOption.decay)
    
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
        n <- prox.nodeMap.valuesIterator
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
        ((source, target), weight) <- prox.edgeMap
      } {
        w.write(Edge(source, target, (1.0/weight).toFloat, GAZ).toJson.compactPrint)
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
class Proximity(decay: Double) {
  import Proximity.NodeKey

  var nextId = 0
  val nodeMap = mutable.HashMap[NodeKey, Node]()

  def accNode(k: NodeKey, extRef: ExtRef): Int = nodeMap.synchronized {
    nodeMap.get(k).map(_.nodeId).getOrElse {
      val id = nextId
      nextId += 1
      val n = Node(id, extRef, k.typ)
      nodeMap += k -> n
      id
    }
  }
  
  // sourceNodeId, targetNodeId -> weight
  val edgeMap = mutable.HashMap[(Int, Int), Double]() withDefaultValue 0.0
  
  def accEdge(source: Int, target: Int, weight: Double): Unit = {
    val k = if (source < target) (source, target) else (target, source)
    edgeMap.synchronized { edgeMap += k -> (edgeMap(k) + weight) }
  }
  
  /** take EMAIL Ners and GAZ Ners that do not start at the same location as an EMAIL Ner (avoid duplicates) */
  def nerFilter(ner: List[Ner]): Iterator[Ner] = {
    val emailNer = ner.filter(n => n.impl == EMAIL)
    val offStr = emailNer.view.map(_.offStr).toSet
    emailNer.iterator ++ ner.view.filter(n => n.impl == GAZ && (n.typ == T_PERSON || n.typ == T_PERSON2 || n.typ == T_ORGANIZATION) && !offStr.contains(n.offStr))
  }
  
  // used multi-threaded usage so must be thread-safe
  def accDoc(d: Doc): Unit = {
    val cutoff = (decay * 5).toInt
    for {
      ners <- nerFilter(d.ner) +: d.embedded.view.map(e => nerFilter(e.ner))
      v = ners.toIndexedSeq.sortBy(_.offStr)
      // _ = log.info(s"v.size = ${v.size}")
      i <- 0 until v.size - 1 // exclude last
      ni = v(i)
      extRefi <- ni.extRef
      (j, dist) <- (i + 1 until v.size).view.map { j => (j, v(j).offStr - ni.offStr) }.takeWhile(_._2 < cutoff)
      nj = v(j)
      extRefj <- nj.extRef
    } {
      // log.info(s"$i, $j -> $dist")
      val idi = accNode(NodeKey(extRefi.name, ni.typ), extRefi)
      val idj = accNode(NodeKey(extRefj.name, nj.typ), extRefj)
      accEdge(idi, idj, Math.exp(-dist/decay)) // additive weight, Edge.distance will be 1/sum(weights)
    }
  }
  
}

