package au.csiro.data61.dataFusion.graph.service

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import Main._
import scala.io.Source
import scala.io.Codec

class MainTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  implicit val codec = Codec.UTF8
  
  "graph" should "provide local network" in {
    def getSource(resourcePath: String) = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(resourcePath))
    val data = new GraphService(getSource("node.json"), getSource("edge.json"))
    val g = data.graph(GraphQuery(224, 2, 20))
    log.debug(s"g = $g")
    g.nodes.map(_.nodeId).toSet should be(Set(1, 2, 3, 4))
    g.edges.map(e => (e.source, e.target)).toSet should be(Set((2,3), (3,4), (1,2), (1,3)))
  }
  
}