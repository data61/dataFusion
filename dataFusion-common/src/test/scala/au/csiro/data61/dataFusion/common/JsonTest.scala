package au.csiro.data61.dataFusion.common

import com.typesafe.scalalogging.Logger

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.typesafe.scalalogging.Logger
import spray.json._
import Data._, JsonProtocol._

class JsonTest extends FlatSpec with Matchers {
  val log = Logger(getClass)
  
  val doc = Doc(0L, Some("content"), Map("key1" -> "val1", "key2" -> "val2"), "path", List.empty, List(
    Embedded(None, Map("key3" -> "val3", "key4" -> "val4"), List.empty), 
    Embedded(Some("embedded content2"), Map("key5" -> "val5", "key6" -> "val6"), List.empty)
  ))
  
  "DocIn" should "ser/deserialize" in {
    val json = doc.toJson.compactPrint
    log.debug(s"json = $json")
    val d2 = json.parseJson.convertTo[Doc]
    d2 should be(doc)
  }
  
  it should "deserialize real output from tika parser" in {
    // content is optional
    val json = List(
      """{
        "id": 0,
        "path": "path",
        "meta": {},
        "ner": [],
        "embedded": []
      }""",
      
      """{
        "id": 1,
        "embedded": [],
        "path": "dataFusion-tika/src/test/resources/exampleData/Email001.msg",
        "meta": {
          "meta:creation-date": "2016-10-25T02:04:26Z",
          "dc:description": "X HOLDINGS LIMITED ",
          "Last-Modified": "2016-10-25T02:04:26Z",
          "subject": "X HOLDINGS LIMITED ",
          "Creation-Date": "2016-10-25T02:04:26Z",
          "dcterms:modified": "2016-10-25T02:04:26Z",
          "language-code": "en",
          "meta:save-date": "2016-10-25T02:04:26Z",
          "X-TIKA:parse_time_millis": "1101",
          "Last-Save-Date": "2016-10-25T02:04:26Z",
          "modified": "2016-10-25T02:04:26Z",
          "resourceName": "dataFusion-tika/src/test/resources/exampleData/Email001.msg",
          "language-prob": "0.9999954",
          "X-Parsed-By": "org.apache.tika.parser.DefaultParser; org.apache.tika.parser.microsoft.OfficeParser",
          "date": "2016-10-25T02:04:26Z",
          "Content-Type": "application/vnd.ms-outlook",
          "dcterms:created": "2016-10-25T02:04:26Z",
          "title": "FW: X HOLDINGS LIMITED ",
          "dc:title": "FW: X HOLDINGS LIMITED ",
          "meta:mapi-message-class": "NOTE"
        },
        "content": "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\nFW: X HOLDINGS LIMITED \n\nFW: X HOLDINGS LIMITED \n\n \n\n \n\nFrom: Glyn Love \nSent: Friday, 18 November 2005 2:28 AM\nTo: Maureen Picot\nSubject: FW: TUNSTALL HOLDINGS LIMITED \n\n\n\n \n\n \n\nF.Y.I.\n\n\n \n\n\n \n\n\n \n\n\n\n\nFrom: Glyn Love On Behalf Of General Mail\nSent: 16 November 2005 11:19\nTo: 'michele.taylor@cspb.com'\nCc: 'michele.taylor@cstrust.com'\nSubject: FW: TUNSTALL HOLDINGS LIMITED \n\nDear Michele\n\n \n\nAs mentioned on the telephone this morning, there are several more registers needed by our BVI Office, but copies by fax will be fine, we will relay these via the CIS database:-\n\n \n\nShare registers\n\n- Dagenham\n\n- Project Finance\n\n- Pendragon\n\n- Caverston\n\n- Pendragon\n\n- Stoneycroft\n\n- Broadland Holdings\n\n- Axin\n\n- Consultate Holdings\n\n- Kudrow\n\n- Manannan\n\n \n\nDirectors registers\n\n- Broadland Holdings\n\n- Caverston\n\n- Pendragon\n\n \n\n\nKind regards\n\n\nGlyn Love (Mrs)\n\n\nfor: Mossack Fonseca & Co. (Jersey) LImited\n\n\n \n\n\n \n\n\n\n\nFrom: Glyn Love On Behalf Of General Mail\nSent: 15 November 2005 15:48\nTo: 'michelle.taylor@cspb.com'\nSubject: FW: TUNSTALL HOLDINGS LIMITED \n\n \n\nDear Michele\n\n\n \n\n\nMany thanks indeed for your letter of 14 November enclosing registers for various companies which BVI Office requested; these are needed before they can proceed with the amendment of the MAA.\n\n\n \n\n\nI will relay these registers via our CIS Database.\n\n\n \n\n\nIn case you did not receive the below email re Tunstall, I thought it best to forward it on ...\n\n \n\nKind regards\n\n\nGlyn Love (Mrs)\n\n\nfor: Mossack Fonseca & Co. (Jersey) LImited\n\n\n \n\n\ntel: 01534 767009     fax: 01534 780 673\n\n\nP O Box 168, Second Floor Suite, 1 Britannia Place, Bath Street, St Helier, Jersey JE4 8RZ\n\n\n \n\n\n****************************************************************************************************************\n\nMossack Fonseca & Co. (Jersey) Limited is regulated and registered by the Jersey Financial Services Commission under the Financial Services (Jersey) Law 1998. \n\nThis email and any additional files or attachments transmitted with it are confidential and intended solely for the named recipients only. It may contain privileged and confidential information and if you are not an intended recipient, you must not copy, distribute or take any legal action in reliance on it. If you have received this email in error please advise and return it immediately to the sender and delete the message from your system.\n\n****************************************************************************************************************\n\n\n\n \n\n\n\nFrom: Bryan Scatliffe [mailto:BScatliffe@mossfon-bvi.com] \nSent: 11 November 2005 14:57\nTo: michelle.taylor@cspb.com\nCc: Mossack Fonseca & Co (Jersey)\nSubject: TUNSTALL HOLDINGS LIMITED \n\nDear Michelle, \n\nWe refer to your request for amendment to the Mem and Arts.\n\nPlease note that we do not have any documentary evidence reflecting the resignations of Leonard Finan, Joan Finan and Irene Gordon as directors of the Company. Please send us the information by return. \n\nRegards,\n\nBryan\n\n\n\n",
        "ner": []
      }"""
    )
    
    for (j <- json) {
      val d = j.parseJson.convertTo[Doc]
      log.debug(s"d = $d")
    }
  }
  
  "Multi-line input" should "work" in {
    val json = doc.toJson.compactPrint
    val in = s"""junk
$json
junk
$json"""
    val x = in.split("\n").toList.filter(_.contains("meta")).map(_.parseJson.convertTo[Doc])
    x.size should be(2)
    for (d2 <- x) d2 should be(doc)
  }
  
}