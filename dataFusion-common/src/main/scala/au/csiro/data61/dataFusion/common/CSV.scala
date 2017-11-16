package au.csiro.data61.dataFusion.common

object CSV {
  
  /**
   * return the indices of the fields for: id, organisation name and person's: family, first given and other given names.
   * @param csvHdr the header line from the CSV file
   */
  def csvHeaderToIndices(delim: Char, fields: Seq[String], hdr: String): Seq[Int] = {
    val hdrs = hdr.toUpperCase.split(delim)
    val fieldsUp = fields.map(_.toUpperCase)
    val idx = fieldsUp map hdrs.indexOf
    val missing = for ((f, i) <- fields zip idx if i == -1) yield f
    if (!missing.isEmpty) throw new Exception(s"CSV header is missing fields: ${missing.mkString(",")}")
    idx
  }
  
  /**
   * Process the header line from iter and return a function to map the remaining lines to a seq of string data in same order as fields.
   * (Done this way to allow the function to be applied to different lines in parallel). 
   */
  def mkFieldData(delim: Char, fields: Seq[String], iter: Iterator[String]): String => Seq[String] = {
    if (iter.hasNext) {
      val idx = csvHeaderToIndices(delim, fields, iter.next)
      val reqLen = idx.max + 1
      line =>
        val d = line.toUpperCase.split(delim).toIndexedSeq.padTo(reqLen, "")
        idx.map(d(_).trim)
    } else _ => Seq.empty
  }
}