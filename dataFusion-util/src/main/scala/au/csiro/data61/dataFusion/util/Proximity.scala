package au.csiro.data61.dataFusion.util

import java.io.File

import scala.collection.mutable
import scala.io.Source

import com.typesafe.scalalogging.Logger

import Main.{ CliOption, GAZ }
import au.csiro.data61.dataFusion.common.Data.{ Doc, Edge }
import au.csiro.data61.dataFusion.common.Data.JsonProtocol.{ docFormat, edgeFormat, nodeFormat }
import au.csiro.data61.dataFusion.common.Data.Node
import au.csiro.data61.dataFusion.common.Parallel.doParallel
import au.csiro.data61.dataFusion.common.Util.bufWriter
import resource.managed
import spray.json.{ pimpAny, pimpString }

object Proximity {
  private val log = Logger(getClass)

  def fileWithSuffix(f: File, suffix: String) = new File(f.getPath + suffix)

  def doProximity(cliOption: CliOption) = {
    val prox = new Proximity
    
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
}

/** thread-safe for concurrent accDoc's */
class Proximity {
  // nodeId -> Node
  val nodeMap = mutable.HashMap[Long, Node]()

  def accNode(nodeId: Long, node: => Node): Unit = nodeMap.synchronized {
    if (!nodeMap.contains(nodeId)) nodeMap += nodeId -> node
  }
  
  // sourceNodeId, targetNodeId -> weight
  val edgeMap = mutable.HashMap[(Long, Long), Double]() withDefaultValue 0.0
  
  def accEdge(source: Long, target: Long, weight: Double): Unit = {
    val key = if (source < target) (source, target) else (target, source)
    edgeMap.synchronized { edgeMap += key -> (edgeMap(key) + weight) }
  }
  
  def accDoc(d: Doc): Unit = {
    for {
      ners <- Iterator.single(d.ner.view.filter(_.impl == GAZ)) ++ d.embedded.view.map(_.ner.view.filter(_.impl == GAZ))
      v = ners.toIndexedSeq.sortBy(_.posStr)
      // _ = log.info(s"v.size = ${v.size}")
      i <- 0 until v.size - 1 // exclude last
      ni = v(i)
      extRefIdi <- ni.extRefId
      nodeIdi <- extRefIdi.headOption // use head extRefId as the nodeId
      (j, dist) <- (i + 1 until v.size).view.map { j => (j, v(j).posStr - ni.posStr) }.takeWhile(_._2 < 100)
      nj = v(j)
      extRefIdj <- nj.extRefId
      nodeIdj <- extRefIdj.headOption
    } {
      // log.info(s"$i, $j -> $dist")
      accNode(nodeIdi, Node(nodeIdi, extRefIdi, ni.typ))
      accNode(nodeIdj, Node(nodeIdj, extRefIdj, nj.typ))
      accEdge(nodeIdi, nodeIdj, Math.exp(-dist/20.0)) // additive weight, Edge.distance will be 1/sum(weights)
    }
  }
  
}

