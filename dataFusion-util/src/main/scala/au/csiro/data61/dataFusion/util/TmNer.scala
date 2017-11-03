package au.csiro.data61.dataFusion.util

import spray.json.DefaultJsonProtocol._
import java.io.InputStream
import scala.io.Source
import spray.json._
import au.csiro.data61.dataFusion.common.Data._

/**
 * Merge Debbie's ner results.
 * Data provided as CSV files with Windows line endings and with our doc id in the filename but not in the data.
 * Steps to clean the data:<ul>
 * <li> sed -i 's/\r//' *                                       # get rid of Windows \r
 * <li> awk -f /data/neil/tmner.awk tmner/ *.csv > tmner.json   # convert to JSON with id in the data
 * </ul>
 * This code merges in the resulting JSON which has the structure of case class Tmner.
 */
object TmNer {
  case class Tmner(id: Long, typ: String, offStr: Int, offEnd: Int, text: String)  
  
  implicit val tmnerFormat = jsonFormat5(Tmner)  
  
  def tmnerIter(hIn: InputStream): Iterator[Tmner] = Source.fromInputStream(hIn, "UTF-8").getLines.map(_.parseJson.convertTo[Tmner])
  
  type TMap = Map[Long, Seq[Tmner]]
  def tmnerMap(iter: Iterator[Tmner]): TMap = iter.toSeq.groupBy(_.id)    
  
  def toNer(t: Tmner) = Ner(-1, -1, t.offStr, t.offEnd, 1.0f, t.text, t.typ, "TMNER", None)
      
  def augment(m: TMap): Doc => Doc = { d =>
    m.get(d.id) match {
      case Some(s) => d.copy(ner = d.ner ++ s.map(toNer))
      case None => d
    }
  }

}