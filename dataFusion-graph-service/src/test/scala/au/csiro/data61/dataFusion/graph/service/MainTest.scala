package au.csiro.data61.dataFusion.graph.service

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import Main._
import scala.io.Source
import scala.io.Codec

class MainTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  implicit val codec = Codec.UTF8
  
  def getSource(resourcePath: String) = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(resourcePath))
  val gs = new GraphService(0, getSource("node.json"), getSource("edge.json"))

  "graph" should "provide local network" in {
    val g = gs.graph(GraphQuery(true, 0.0, None, None, Some(224), 2, 20))
    log.debug(s"g = $g")
    g.nodes.map(_.nodeId).toSet should be(Set(1, 2, 3, 4, 5, 6))
    g.edges.map(e => (e.source, e.target)).toSet should be(Set((2,5), (3,4), (1,6), (3,5), (4,6), (2,6), (1,3), (2,3), (1,2), (5,6)))
  }
  
  it should "filter PERSON2|EMAIL nodes" in {
    val g = gs.graph(GraphQuery(false, 0.0, None, None, Some(224), 2, 20))
    log.debug(s"g = $g")
    g.nodes.map(_.nodeId).toSet should be(Set(1, 2, 3, 4))
    g.edges.map(e => (e.source, e.target)).toSet should be(Set((2,3), (3,4), (1,2), (1,3)))
  }
  
}