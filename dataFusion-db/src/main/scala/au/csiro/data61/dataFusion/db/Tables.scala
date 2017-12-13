package au.csiro.data61.dataFusion.db
// AUTO-GENERATED Slick data model

object Tables {
  /** Entity class storing rows of table Doc
   *  @param docId Database column DOC_ID SqlType(BIGINT)
   *  @param embIdx Database column EMB_IDX SqlType(INTEGER)
   *  @param path Database column PATH SqlType(VARCHAR)
   *  @param content Database column CONTENT SqlType(VARCHAR) */
  case class DocRow(docId: Long, embIdx: Int, path: Option[String], content: Option[String])
  
  /** Entity class storing rows of table ExtName
   *  @param extNameId Database column EXT_NAME_ID SqlType(BIGINT), PrimaryKey
   *  @param name Database column NAME SqlType(VARCHAR)
   *  @param typ Database column TYP SqlType(VARCHAR) */
  case class ExtNameRow(extNameId: Long, name: String, typ: String)
  
  /** Entity class storing rows of table ExtNameLink
   *  @param extNameId Database column EXT_NAME_ID SqlType(BIGINT)
   *  @param extRefId Database column EXT_REF_ID SqlType(BIGINT) */
  case class ExtNameLinkRow(extNameId: Long, extRefId: Long)
  
  /** Entity class storing rows of table Meta
   *  @param docId Database column DOC_ID SqlType(BIGINT)
   *  @param embIdx Database column EMB_IDX SqlType(INTEGER)
   *  @param key Database column KEY SqlType(VARCHAR)
   *  @param value Database column VALUE SqlType(VARCHAR) */
  case class MetaRow(docId: Long, embIdx: Int, key: String, value: String)
  
  /** Entity class storing rows of table Ner
   *  @param docId Database column DOC_ID SqlType(BIGINT)
   *  @param embIdx Database column EMB_IDX SqlType(INTEGER)
   *  @param text Database column TEXT SqlType(VARCHAR)
   *  @param typ Database column TYP SqlType(VARCHAR)
   *  @param impl Database column IMPL SqlType(VARCHAR)
   *  @param score Database column SCORE SqlType(DOUBLE)
   *  @param extNameId Database column EXT_NAME_ID SqlType(BIGINT)
   *  @param posStr Database column POS_STR SqlType(INTEGER)
   *  @param posEnd Database column POS_END SqlType(INTEGER)
   *  @param offStr Database column OFF_STR SqlType(INTEGER)
   *  @param offEnd Database column OFF_END SqlType(INTEGER) */
  case class NerRow(docId: Long, embIdx: Int, text: String, typ: String, impl: String, score: Double, extNameId: Option[Long], posStr: Int, posEnd: Int, offStr: Int, offEnd: Int)
}
import Tables._

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Doc.schema ++ ExtName.schema ++ ExtNameLink.schema ++ Meta.schema ++ Ner.schema

  /** GetResult implicit for fetching DocRow objects using plain SQL queries */
  implicit def GetResultDocRow(implicit e0: GR[Long], e1: GR[Int], e2: GR[Option[String]]): GR[DocRow] = GR{
    prs => import prs._
    DocRow.tupled((<<[Long], <<[Int], <<?[String], <<?[String]))
  }
  /** Table description of table DOC. Objects of this class serve as prototypes for rows in queries. */
  class Doc(_tableTag: Tag) extends profile.api.Table[DocRow](_tableTag, "DOC") {
    def * = (docId, embIdx, path, content) <> (DocRow.tupled, DocRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(docId), Rep.Some(embIdx), path, content).shaped.<>({r=>import r._; _1.map(_=> DocRow.tupled((_1.get, _2.get, _3, _4)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column DOC_ID SqlType(BIGINT) */
    val docId: Rep[Long] = column[Long]("DOC_ID")
    /** Database column EMB_IDX SqlType(INTEGER) */
    val embIdx: Rep[Int] = column[Int]("EMB_IDX")
    /** Database column PATH SqlType(VARCHAR) */
    val path: Rep[Option[String]] = column[Option[String]]("PATH")
    /** Database column CONTENT SqlType(VARCHAR) */
    val content: Rep[Option[String]] = column[Option[String]]("CONTENT")

    /** Primary key of Doc (database name CONSTRAINT_1) */
    val pk = primaryKey("CONSTRAINT_1", (docId, embIdx))
  }
  /** Collection-like TableQuery object for table Doc */
  lazy val Doc = new TableQuery(tag => new Doc(tag))

  /** GetResult implicit for fetching ExtNameRow objects using plain SQL queries */
  implicit def GetResultExtNameRow(implicit e0: GR[Long], e1: GR[String]): GR[ExtNameRow] = GR{
    prs => import prs._
    ExtNameRow.tupled((<<[Long], <<[String], <<[String]))
  }
  /** Table description of table EXT_NAME. Objects of this class serve as prototypes for rows in queries. */
  class ExtName(_tableTag: Tag) extends profile.api.Table[ExtNameRow](_tableTag, "EXT_NAME") {
    def * = (extNameId, name, typ) <> (ExtNameRow.tupled, ExtNameRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(extNameId), Rep.Some(name), Rep.Some(typ)).shaped.<>({r=>import r._; _1.map(_=> ExtNameRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column EXT_NAME_ID SqlType(BIGINT), PrimaryKey */
    val extNameId: Rep[Long] = column[Long]("EXT_NAME_ID", O.PrimaryKey)
    /** Database column NAME SqlType(VARCHAR) */
    val name: Rep[String] = column[String]("NAME")
    /** Database column TYP SqlType(VARCHAR) */
    val typ: Rep[String] = column[String]("TYP")
  }
  /** Collection-like TableQuery object for table ExtName */
  lazy val ExtName = new TableQuery(tag => new ExtName(tag))

  /** GetResult implicit for fetching ExtNameLinkRow objects using plain SQL queries */
  implicit def GetResultExtNameLinkRow(implicit e0: GR[Long]): GR[ExtNameLinkRow] = GR{
    prs => import prs._
    ExtNameLinkRow.tupled((<<[Long], <<[Long]))
  }
  /** Table description of table EXT_NAME_LINK. Objects of this class serve as prototypes for rows in queries. */
  class ExtNameLink(_tableTag: Tag) extends profile.api.Table[ExtNameLinkRow](_tableTag, "EXT_NAME_LINK") {
    def * = (extNameId, extRefId) <> (ExtNameLinkRow.tupled, ExtNameLinkRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(extNameId), Rep.Some(extRefId)).shaped.<>({r=>import r._; _1.map(_=> ExtNameLinkRow.tupled((_1.get, _2.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column EXT_NAME_ID SqlType(BIGINT) */
    val extNameId: Rep[Long] = column[Long]("EXT_NAME_ID")
    /** Database column EXT_REF_ID SqlType(BIGINT) */
    val extRefId: Rep[Long] = column[Long]("EXT_REF_ID")
  }
  /** Collection-like TableQuery object for table ExtNameLink */
  lazy val ExtNameLink = new TableQuery(tag => new ExtNameLink(tag))

  /** GetResult implicit for fetching MetaRow objects using plain SQL queries */
  implicit def GetResultMetaRow(implicit e0: GR[Long], e1: GR[Int], e2: GR[String]): GR[MetaRow] = GR{
    prs => import prs._
    MetaRow.tupled((<<[Long], <<[Int], <<[String], <<[String]))
  }
  /** Table description of table META. Objects of this class serve as prototypes for rows in queries. */
  class Meta(_tableTag: Tag) extends profile.api.Table[MetaRow](_tableTag, "META") {
    def * = (docId, embIdx, key, value) <> (MetaRow.tupled, MetaRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(docId), Rep.Some(embIdx), Rep.Some(key), Rep.Some(value)).shaped.<>({r=>import r._; _1.map(_=> MetaRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column DOC_ID SqlType(BIGINT) */
    val docId: Rep[Long] = column[Long]("DOC_ID")
    /** Database column EMB_IDX SqlType(INTEGER) */
    val embIdx: Rep[Int] = column[Int]("EMB_IDX")
    /** Database column KEY SqlType(VARCHAR) */
    val key: Rep[String] = column[String]("KEY")
    /** Database column VALUE SqlType(VARCHAR) */
    val value: Rep[String] = column[String]("VALUE")

    /** Foreign key referencing Doc (database name CONSTRAINT_2) */
    lazy val docFk = foreignKey("CONSTRAINT_2", (docId, embIdx), Doc)(r => (r.docId, r.embIdx), onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table Meta */
  lazy val Meta = new TableQuery(tag => new Meta(tag))

  /** GetResult implicit for fetching NerRow objects using plain SQL queries */
  implicit def GetResultNerRow(implicit e0: GR[Long], e1: GR[Int], e2: GR[String], e3: GR[Double], e4: GR[Option[Long]]): GR[NerRow] = GR{
    prs => import prs._
    NerRow.tupled((<<[Long], <<[Int], <<[String], <<[String], <<[String], <<[Double], <<?[Long], <<[Int], <<[Int], <<[Int], <<[Int]))
  }
  /** Table description of table NER. Objects of this class serve as prototypes for rows in queries. */
  class Ner(_tableTag: Tag) extends profile.api.Table[NerRow](_tableTag, "NER") {
    def * = (docId, embIdx, text, typ, impl, score, extNameId, posStr, posEnd, offStr, offEnd) <> (NerRow.tupled, NerRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(docId), Rep.Some(embIdx), Rep.Some(text), Rep.Some(typ), Rep.Some(impl), Rep.Some(score), extNameId, Rep.Some(posStr), Rep.Some(posEnd), Rep.Some(offStr), Rep.Some(offEnd)).shaped.<>({r=>import r._; _1.map(_=> NerRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7, _8.get, _9.get, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column DOC_ID SqlType(BIGINT) */
    val docId: Rep[Long] = column[Long]("DOC_ID")
    /** Database column EMB_IDX SqlType(INTEGER) */
    val embIdx: Rep[Int] = column[Int]("EMB_IDX")
    /** Database column TEXT SqlType(VARCHAR) */
    val text: Rep[String] = column[String]("TEXT")
    /** Database column TYP SqlType(VARCHAR) */
    val typ: Rep[String] = column[String]("TYP")
    /** Database column IMPL SqlType(VARCHAR) */
    val impl: Rep[String] = column[String]("IMPL")
    /** Database column SCORE SqlType(DOUBLE) */
    val score: Rep[Double] = column[Double]("SCORE")
    /** Database column EXT_NAME_ID SqlType(BIGINT) */
    val extNameId: Rep[Option[Long]] = column[Option[Long]]("EXT_NAME_ID")
    /** Database column POS_STR SqlType(INTEGER) */
    val posStr: Rep[Int] = column[Int]("POS_STR")
    /** Database column POS_END SqlType(INTEGER) */
    val posEnd: Rep[Int] = column[Int]("POS_END")
    /** Database column OFF_STR SqlType(INTEGER) */
    val offStr: Rep[Int] = column[Int]("OFF_STR")
    /** Database column OFF_END SqlType(INTEGER) */
    val offEnd: Rep[Int] = column[Int]("OFF_END")

    /** Foreign key referencing Doc (database name CONSTRAINT_12) */
    lazy val docFk = foreignKey("CONSTRAINT_12", (docId, embIdx), Doc)(r => (r.docId, r.embIdx), onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table Ner */
  lazy val Ner = new TableQuery(tag => new Ner(tag))
}
