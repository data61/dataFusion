package au.csiro.data61.dataFusion.search

import java.io.File

import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(output: File, index: Boolean, searchJson: Boolean, searchCsv: Boolean, cvsDelim: Char, cvsPerson: Seq[String], cvsOrg: String, cvsId: String, docFreq: Boolean, export: Boolean, filterQueryOnly: Boolean, filterQuery: Boolean, nerToQuery: Boolean, slop: Int, numWorkers: Int)
  
  val defaultCliOption = CliOption(new File("hits.json"), false, false, false, '|', Seq("STRCTRD_FMLY_NM", "STRCTRD_GVN_NM", "STRCTRD_OTHR_GVN_NM"), "USTRCTRD_FULL_NM", "CLNT_INTRNL_ID", false, false, false, true, false, 0, Runtime.getRuntime.availableProcessors)
  
  val parser = new scopt.OptionParser[CliOption]("search") {
    head("search", "0.x")
    note("Run Lucene search CLI unless one of --index, --docFreq or --filterQuery is specified.")
    opt[File]("output") action { (v, c) =>
      c.copy(output = v)
    } text (s"output JSON file, (default ${defaultCliOption.output.getPath})")
    opt[Unit]("index") action { (_, c) =>
      c.copy(index = true)
    } text (s"create Lucene indices from JSON input (default ${defaultCliOption.index})")
    opt[Unit]("searchJson") action { (_, c) =>
      c.copy(searchJson = true)
    } text (s"search with JSON queries on stdin (default ${defaultCliOption.searchJson})")
    opt[Unit]("searchCsv") action { (_, c) =>
      c.copy(searchCsv = true)
    } text (s"search with CSV queries on stdin (default ${defaultCliOption.searchCsv})")
    opt[String]("cvsDelim") action { (v, c) =>
      c.copy(cvsDelim = v.headOption.getOrElse(defaultCliOption.cvsDelim))
    } text (s"CSV field delimeter (default ${defaultCliOption.cvsDelim})")
    opt[Seq[String]]("cvsPerson") action { (v, c) =>
      c.copy(cvsPerson = v)
    } validate { v =>
      if (v.size == 3) success
      else failure("3 field names are required")
    } text (s"CSV field names (3) for person's family, first given and other names (default ${defaultCliOption.cvsPerson.toList})")
    opt[String]("cvsOrg") action { (v, c) =>
      c.copy(cvsOrg = v)
    } text (s"CSV field name for organisation (default ${defaultCliOption.cvsOrg})")
    opt[String]("cvsId") action { (v, c) =>
      c.copy(cvsId = v)
    } text (s"CSV field name for ID (default ${defaultCliOption.cvsId})")
    opt[Unit]('d', "docFreq") action { (_, c) =>
      c.copy(docFreq = true)
    } text (s"output term document frequencies from index as CSV (default ${defaultCliOption.docFreq})")
    opt[Unit]('e', "export") action { (_, c) =>
      c.copy(export = true)
    } text (s"output the stored JSON for each doc (default ${defaultCliOption.export})")
    opt[Unit]('g', "filterQueryOnly") action { (_, c) =>
      c.copy(filterQueryOnly = true)
    } text (s"filter Query JSON from stdin to stdout, outputing only lines with all query terms most likely in the index (default ${defaultCliOption.filterQueryOnly})")
    opt[Boolean]('f', "filterQuery") action { (v, c) =>
      c.copy(filterQuery = v)
    } text (s"search CLI skips search if any query term is definitely not in the index (default ${defaultCliOption.filterQuery})")
    opt[Unit]('q', "nerToQuery") action { (_, c) =>
      c.copy(nerToQuery = true)
    } text (s"filter JSON names from stdin to stdout, outputing queries only for lines with all specified query terms in the index (default ${defaultCliOption.filterQuery})")
    opt[Int]('s', "slop") action { (v, c) =>
      c.copy(slop = v)
    } text (s"slop for posQuery, (default ${defaultCliOption.slop})")
    opt[Int]('n', "numWorkers") action { (v, c) =>
      c.copy(numWorkers = v)
    } text (s"numWorkers for CLI queries, (default ${defaultCliOption.numWorkers} the number of CPUs)")
    help("help") text ("prints this usage text")
  }
    
  // TODO: perhaps use [Guava's BloomFilter](https://github.com/google/guava/blob/master/guava/src/com/google/common/hash/BloomFilter.java)
  // to internally filter out queries containing terms not in the index, rather than doing this filtering in pre-processing scripts.
  def main(args: Array[String]): Unit = {
    try {
      parser.parse(args, defaultCliOption).foreach { c => 
        if (c.index) Indexer.run(c)
        else if (c.docFreq) DocFreq.writeDocFreqs(c)
        else if (c.filterQueryOnly) DocFreq.filterQuery(c)
        else if (c.nerToQuery) DocFreq.nerToQuery(c)
        else if (c.export) Search.cliExportDocIds(c)
        else if (c.searchJson || c.searchCsv) Search.cliPosDocSearch(c)
        else log.info("Nothing to do. Try --help")
      }
    } catch {
      case NonFatal(e) => log.error("Main.main:", e)
    }
  }
  
}