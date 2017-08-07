package au.csiro.data61.dataFusion.graph.service

import org.scalatest.{ FlatSpec, Matchers }

import com.typesafe.scalalogging.Logger

import Main._
import scala.io.Source
import scala.io.Codec

class MainTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  implicit val codec = Codec.UTF8
  
  "DocHits" should "ser/deserialize" in {
    def getSource(resourcePath: String) = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(resourcePath))
    val data = new GraphService(getSource("node.json"), getSource("edge.json"))
    val nds = data.findNodes(NodeQuery("Smith", 1))
    log.debug(s"nds = $nds")
    nds.nodes.size should be(3)
    
    val g = data.graph(GraphQuery(1, 2, 20))
    log.debug(s"g = $g")
    g.nodes.map(_.id).toSet should be(Set(1, 2, 3, 4))
    g.edges.toSet should be(Set(Edge(2,3,1.0f,1), Edge(3,4,1.0f,1), Edge(1,2,1.0f,1), Edge(1,3,1.0f,1)))
  }
  
}