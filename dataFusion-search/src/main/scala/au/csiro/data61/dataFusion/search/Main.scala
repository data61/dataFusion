package au.csiro.data61.dataFusion.search

import java.io.File

import scala.util.control.NonFatal

import com.typesafe.scalalogging.Logger

object Main {
  private val log = Logger(getClass)
  
  case class CliOption(output: File, index: Boolean, searchJson: Boolean, searchCsv: Boolean, csvDelim: Char, csvPerson: Seq[String], csvOrg: String, csvId: String, docFreq: Boolean, export: Boolean, filterQueryOnly: Boolean, filterQuery: Boolean, maxTerms: Int, nerToQuery: Boolean, slop: Int, numWorkers: Int)
  
  val defaultCliOption = CliOption(new File("hits.json"), false, false, false, '\t', Seq("STRCTRD_FMLY_NM", "STRCTRD_GVN_NM", "STRCTRD_OTHR_GVN_NM"), "USTRCTRD_FULL_NM", "CLNT_INTRNL_ID", false, false, false, true, 10000000, false, 0, Runtime.getRuntime.availableProcessors)
  
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
    opt[String]("csvDelim") action { (v, c) =>
      c.copy(csvDelim = v.headOption.getOrElse(defaultCliOption.csvDelim))
    } text (s"CSV field delimeter (default ${if (defaultCliOption.csvDelim == '\t') "tab" else defaultCliOption.csvDelim.toString})")
    opt[Seq[String]]("csvPerson") action { (v, c) =>
      c.copy(csvPerson = v)
    } validate { v =>
      if (v.size == 3) success
      else failure("3 field names are required")
    } text (s"CSV field names (3) for person's family, first given and other names (default ${defaultCliOption.csvPerson.toList})")
    opt[String]("csvOrg") action { (v, c) =>
      c.copy(csvOrg = v)
    } text (s"CSV field name for organisation (default ${defaultCliOption.csvOrg})")
    opt[String]("csvId") action { (v, c) =>
      c.copy(csvId = v)
    } text (s"CSV field name for ID (default ${defaultCliOption.csvId})")
    opt[Unit]("docFreq") action { (_, c) =>
      c.copy(docFreq = true)
    } text (s"output term document frequencies from index as CSV (default ${defaultCliOption.docFreq})")
    opt[Unit]("export") action { (_, c) =>
      c.copy(export = true)
    } text (s"output the stored JSON for each doc (default ${defaultCliOption.export})")
    opt[Unit]("filterQueryOnly") action { (_, c) =>
      c.copy(filterQueryOnly = true)
    } text (s"filter Query JSON from stdin to stdout, outputing only lines with all query terms most likely in the index (default ${defaultCliOption.filterQueryOnly})")
    opt[Boolean]("filterQuery") action { (v, c) =>
      c.copy(filterQuery = v)
    } text (s"search CLI skips search if any query term is definitely not in the index (default ${defaultCliOption.filterQuery})")
    opt[Int]("maxTerms") action { (v, c) =>
      c.copy(maxTerms = v)
    } text (s"maxTerms for Bloom Filter used with filterQuery, (default ${defaultCliOption.maxTerms})")
    opt[Unit]("nerToQuery") action { (_, c) =>
      c.copy(nerToQuery = true)
    } text (s"filter JSON names from stdin to stdout, outputing queries only for lines with all specified query terms in the index (default ${defaultCliOption.filterQuery})")
    opt[Int]("slop") action { (v, c) =>
      c.copy(slop = v)
    } text (s"slop for posQuery, (default ${defaultCliOption.slop})")
    opt[Int]("numWorkers") action { (v, c) =>
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