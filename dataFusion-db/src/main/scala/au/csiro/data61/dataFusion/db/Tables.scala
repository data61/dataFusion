package au.csiro.data61.dataFusion.db

import slick.collection.heterogeneous._
import slick.collection.heterogeneous.syntax._

object Tables {
  
  /** Entity class storing rows of table GraphEdge
   *  @param source Database column source SqlType(int8)
   *  @param target Database column target SqlType(int8)
   *  @param distance Database column distance SqlType(float8)
   *  @param `type` Database column type SqlType(int4) */
  case class GraphEdgeRow(source: Long, target: Long, distance: Double, `type`: Int)

  /** Entity class storing rows of table GraphNode
   *  @param entityId Database column entity_id SqlType(int8), PrimaryKey
   *  @param label Database column label SqlType(varchar), Length(256,true)
   *  @param `type` Database column type SqlType(int4) */
  case class GraphNodeRow(entityId: Long, label: String, `type`: Int)
  
  /** Entity class storing rows of table SearchHits
   *  @param entityId Database column entity_id SqlType(int8), Default(None)
   *  @param path Database column path SqlType(varchar), Length(256,true)
   *  @param search Database column search SqlType(varchar), Length(256,true)
   *  @param docId Database column doc_id SqlType(int4)
   *  @param embIdx Database column emb_idx SqlType(int4)
   *  @param posStr Database column pos_str SqlType(int4)
   *  @param posEnd Database column pos_end SqlType(int4)
   *  @param offStr Database column off_str SqlType(int4)
   *  @param offEnd Database column off_end SqlType(int4)
   *  @param text Database column text SqlType(varchar), Length(256,true)
   *  @param `type` Database column type SqlType(int4) */
  case class SearchHitsRow(entityId: Option[Long] = None, path: String, search: String, docId: Int, embIdx: Int, posStr: Int, posEnd: Int, offStr: Int, offEnd: Int, text: String, `type`: Int)

  /** Entity class storing rows of table TikaMain
   *  @param path Database column path SqlType(varchar), Length(256,true), Default(None)
   *  @param content Database column content SqlType(text), Default(None)
   *  @param id Database column id SqlType(int4), PrimaryKey
   *  @param searchId Database column search_id SqlType(int4), Default(None) */
  case class TikaMainRow(path: Option[String] = None, content: Option[String] = None, id: Int, searchId: Option[Int] = None)

  /** Entity class storing rows of table TikaMeta
   *  @param key Database column key SqlType(varchar), Length(64,true), Default(None)
   *  @param `val` Database column val SqlType(varchar), Length(256,true), Default(None)
   *  @param id Database column id SqlType(int4), Default(None) */
  case class TikaMetaRow(key: Option[String] = None, `val`: Option[String] = None, id: Option[Int] = None)

  /** Entity class storing rows of table TikaNer
   *  @param text Database column text SqlType(text), Default(None)
   *  @param impl Database column impl SqlType(varchar), Length(8,true), Default(None)
   *  @param typ Database column typ SqlType(varchar), Length(20,true), Default(None)
   *  @param end Database column end_ SqlType(int4), Default(None)
   *  @param start Database column start_ SqlType(int4), Default(None)
   *  @param id Database column id SqlType(int4), Default(None) */
  case class TikaNerRow(text: Option[String] = None, impl: Option[String] = None, typ: Option[String] = None, end: Option[Int] = None, start: Option[Int] = None, id: Option[Int] = None)

  /** Entity class storing rows of table TikaEmbMain
   *  @param content Database column content SqlType(text), Default(None)
   *  @param id Database column id SqlType(int4)
   *  @param embeddedidx Database column embeddedidx SqlType(int4) */
  case class TikaEmbMainRow(content: Option[String] = None, id: Int, embeddedidx: Int)

  /** Entity class storing rows of table TikaEmbMeta
   *  @param key Database column key SqlType(varchar), Length(64,true), Default(None)
   *  @param `val` Database column val SqlType(varchar), Length(16000,true), Default(None)
   *  @param id Database column id SqlType(int4), Default(None)
   *  @param embeddedidx Database column embeddedidx SqlType(int4), Default(None) */
  case class TikaEmbMetaRow(key: Option[String] = None, `val`: Option[String] = None, id: Option[Int] = None, embeddedidx: Option[Int] = None)

  /** Entity class storing rows of table TikaEmbNer
   *  @param text Database column text SqlType(varchar), Length(10000,true), Default(None)
   *  @param impl Database column impl SqlType(varchar), Length(8,true), Default(None)
   *  @param typ Database column typ SqlType(varchar), Length(20,true), Default(None)
   *  @param end Database column end_ SqlType(int4), Default(None)
   *  @param start Database column start_ SqlType(int4), Default(None)
   *  @param id Database column id SqlType(int4), Default(None)
   *  @param embeddedidx Database column embeddedidx SqlType(int4), Default(None) */
  case class TikaEmbNerRow(text: Option[String] = None, impl: Option[String] = None, typ: Option[String] = None, end: Option[Int] = None, start: Option[Int] = None, id: Option[Int] = None, embeddedidx: Option[Int] = None)

}
import Tables._

// AUTO-GENERATED Slick data model

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val schemaName: Option[String]
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(
      GraphEdge.schema, GraphNode.schema, 
      SearchHits.schema, 
      TikaMain.schema, TikaMeta.schema, TikaNer.schema,
      TikaEmbMain.schema, TikaEmbMeta.schema, TikaEmbNer.schema 
      ).reduceLeft(_ ++ _)
  
  /** GetResult implicit for fetching GraphEdgeRow objects using plain SQL queries */
  implicit def GetResultGraphEdgeRow(implicit e0: GR[Long], e1: GR[Double], e2: GR[Int]): GR[GraphEdgeRow] = GR{
    prs => import prs._
    GraphEdgeRow.tupled((<<[Long], <<[Long], <<[Double], <<[Int]))
  }
  /** Table description of table graph_edge. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: type */
  class GraphEdge(_tableTag: Tag) extends profile.api.Table[GraphEdgeRow](_tableTag, schemaName, "graph_edge") {
    def * = (source, target, distance, `type`) <> (GraphEdgeRow.tupled, GraphEdgeRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(source), Rep.Some(target), Rep.Some(distance), Rep.Some(`type`)).shaped.<>({r=>import r._; _1.map(_=> GraphEdgeRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column source SqlType(int8) */
    val source: Rep[Long] = column[Long]("source")
    /** Database column target SqlType(int8) */
    val target: Rep[Long] = column[Long]("target")
    /** Database column distance SqlType(float8) */
    val distance: Rep[Double] = column[Double]("distance")
    /** Database column type SqlType(int4)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `type`: Rep[Int] = column[Int]("type")

    /** Foreign key referencing GraphNode (database name graph_edge_source_fkey) */
    lazy val graphNodeFk1 = foreignKey("graph_edge_source_fkey", source, GraphNode)(r => r.entityId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing GraphNode (database name graph_edge_target_fkey) */
    lazy val graphNodeFk2 = foreignKey("graph_edge_target_fkey", target, GraphNode)(r => r.entityId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table GraphEdge */
  lazy val GraphEdge = new TableQuery(tag => new GraphEdge(tag))

  /** GetResult implicit for fetching GraphNodeRow objects using plain SQL queries */
  implicit def GetResultGraphNodeRow(implicit e0: GR[Long], e1: GR[String], e2: GR[Int]): GR[GraphNodeRow] = GR{
    prs => import prs._
    GraphNodeRow.tupled((<<[Long], <<[String], <<[Int]))
  }
  /** Table description of table graph_node. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: type */
  class GraphNode(_tableTag: Tag) extends profile.api.Table[GraphNodeRow](_tableTag, schemaName, "graph_node") {
    def * = (entityId, label, `type`) <> (GraphNodeRow.tupled, GraphNodeRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(entityId), Rep.Some(label), Rep.Some(`type`)).shaped.<>({r=>import r._; _1.map(_=> GraphNodeRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column entity_id SqlType(int8), PrimaryKey */
    val entityId: Rep[Long] = column[Long]("entity_id", O.PrimaryKey)
    /** Database column label SqlType(varchar), Length(256,true) */
    val label: Rep[String] = column[String]("label", O.Length(256,varying=true))
    /** Database column type SqlType(int4)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `type`: Rep[Int] = column[Int]("type")
  }
  /** Collection-like TableQuery object for table GraphNode */
  lazy val GraphNode = new TableQuery(tag => new GraphNode(tag))

  /** GetResult implicit for fetching SearchHitsRow objects using plain SQL queries */
  implicit def GetResultSearchHitsRow(implicit e0: GR[Option[Long]], e1: GR[String], e2: GR[Int]): GR[SearchHitsRow] = GR{
    prs => import prs._
    SearchHitsRow.tupled((<<?[Long], <<[String], <<[String], <<[Int], <<[Int], <<[Int], <<[Int], <<[Int], <<[Int], <<[String], <<[Int]))
  }
  /** Table description of table search_hits. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: type */
  class SearchHits(_tableTag: Tag) extends profile.api.Table[SearchHitsRow](_tableTag, schemaName, "search_hits") {
    def * = (entityId, path, search, docId, embIdx, posStr, posEnd, offStr, offEnd, text, `type`) <> (SearchHitsRow.tupled, SearchHitsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (entityId, Rep.Some(path), Rep.Some(search), Rep.Some(docId), Rep.Some(embIdx), Rep.Some(posStr), Rep.Some(posEnd), Rep.Some(offStr), Rep.Some(offEnd), Rep.Some(text), Rep.Some(`type`)).shaped.<>({r=>import r._; _2.map(_=> SearchHitsRow.tupled((_1, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column entity_id SqlType(int8), Default(None) */
    val entityId: Rep[Option[Long]] = column[Option[Long]]("entity_id", O.Default(None))
    /** Database column path SqlType(varchar), Length(256,true) */
    val path: Rep[String] = column[String]("path", O.Length(256,varying=true))
    /** Database column search SqlType(varchar), Length(256,true) */
    val search: Rep[String] = column[String]("search", O.Length(256,varying=true))
    /** Database column doc_id SqlType(int4) */
    val docId: Rep[Int] = column[Int]("doc_id")
    /** Database column emb_idx SqlType(int4) */
    val embIdx: Rep[Int] = column[Int]("emb_idx")
    /** Database column pos_str SqlType(int4) */
    val posStr: Rep[Int] = column[Int]("pos_str")
    /** Database column pos_end SqlType(int4) */
    val posEnd: Rep[Int] = column[Int]("pos_end")
    /** Database column off_str SqlType(int4) */
    val offStr: Rep[Int] = column[Int]("off_str")
    /** Database column off_end SqlType(int4) */
    val offEnd: Rep[Int] = column[Int]("off_end")
    /** Database column text SqlType(varchar), Length(256,true) */
    val text: Rep[String] = column[String]("text", O.Length(256,varying=true))
    /** Database column type SqlType(int4)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `type`: Rep[Int] = column[Int]("type")

    /** Index over (docId) (database name search_hits_doc_id) */
    val index1 = index("search_hits_doc_id", docId)
  }
  /** Collection-like TableQuery object for table SearchHits */
  lazy val SearchHits = new TableQuery(tag => new SearchHits(tag))


  /** GetResult implicit for fetching TikaEmbMainRow objects using plain SQL queries */
  implicit def GetResultTikaEmbMainRow(implicit e0: GR[Option[String]], e1: GR[Int]): GR[TikaEmbMainRow] = GR{
    prs => import prs._
    TikaEmbMainRow.tupled((<<?[String], <<[Int], <<[Int]))
  }
  /** Table description of table tika_emb_main. Objects of this class serve as prototypes for rows in queries. */
  class TikaEmbMain(_tableTag: Tag) extends profile.api.Table[TikaEmbMainRow](_tableTag, schemaName, "tika_emb_main") {
    def * = (content, id, embeddedidx) <> (TikaEmbMainRow.tupled, TikaEmbMainRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (content, Rep.Some(id), Rep.Some(embeddedidx)).shaped.<>({r=>import r._; _2.map(_=> TikaEmbMainRow.tupled((_1, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column content SqlType(text), Default(None) */
    val content: Rep[Option[String]] = column[Option[String]]("content", O.Default(None))
    /** Database column id SqlType(int4) */
    val id: Rep[Int] = column[Int]("id")
    /** Database column embeddedidx SqlType(int4) */
    val embeddedidx: Rep[Int] = column[Int]("embeddedidx")

    /** Primary key of TikaEmbMain (database name tika_emb_main_pkey) */
    val pk = primaryKey("tika_emb_main_pkey", (id, embeddedidx))

    /** Foreign key referencing TikaMain (database name tika_emb_main_id_fkey) */
    lazy val tikaMainFk = foreignKey("tika_emb_main_id_fkey", id, TikaMain)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table TikaEmbMain */
  lazy val TikaEmbMain = new TableQuery(tag => new TikaEmbMain(tag))


  /** GetResult implicit for fetching TikaEmbMetaRow objects using plain SQL queries */
  implicit def GetResultTikaEmbMetaRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[TikaEmbMetaRow] = GR{
    prs => import prs._
    TikaEmbMetaRow.tupled((<<?[String], <<?[String], <<?[Int], <<?[Int]))
  }
  /** Table description of table tika_emb_meta. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: val */
  class TikaEmbMeta(_tableTag: Tag) extends profile.api.Table[TikaEmbMetaRow](_tableTag, schemaName, "tika_emb_meta") {
    def * = (key, `val`, id, embeddedidx) <> (TikaEmbMetaRow.tupled, TikaEmbMetaRow.unapply)

    /** Database column key SqlType(varchar), Length(64,true), Default(None) */
    val key: Rep[Option[String]] = column[Option[String]]("key", O.Length(64,varying=true), O.Default(None))
    /** Database column val SqlType(varchar), Length(16000,true), Default(None)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `val`: Rep[Option[String]] = column[Option[String]]("val", O.Length(16000,varying=true), O.Default(None))
    /** Database column id SqlType(int4), Default(None) */
    val id: Rep[Option[Int]] = column[Option[Int]]("id", O.Default(None))
    /** Database column embeddedidx SqlType(int4), Default(None) */
    val embeddedidx: Rep[Option[Int]] = column[Option[Int]]("embeddedidx", O.Default(None))

    /** Foreign key referencing TikaEmbMain (database name tika_emb_meta_id_fkey) */
    lazy val tikaEmbMainFk = foreignKey("tika_emb_meta_id_fkey", (id, embeddedidx), TikaEmbMain)(r => (Rep.Some(r.id), Rep.Some(r.embeddedidx)), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table TikaEmbMeta */
  lazy val TikaEmbMeta = new TableQuery(tag => new TikaEmbMeta(tag))

  /** GetResult implicit for fetching TikaEmbNerRow objects using plain SQL queries */
  implicit def GetResultTikaEmbNerRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[TikaEmbNerRow] = GR{
    prs => import prs._
    TikaEmbNerRow.tupled((<<?[String], <<?[String], <<?[String], <<?[Int], <<?[Int], <<?[Int], <<?[Int]))
  }
  /** Table description of table tika_emb_ner. Objects of this class serve as prototypes for rows in queries. */
  class TikaEmbNer(_tableTag: Tag) extends profile.api.Table[TikaEmbNerRow](_tableTag, schemaName, "tika_emb_ner") {
    def * = (text, impl, typ, end, start, id, embeddedidx) <> (TikaEmbNerRow.tupled, TikaEmbNerRow.unapply)

    /** Database column text SqlType(varchar), Length(10000,true), Default(None) */
    val text: Rep[Option[String]] = column[Option[String]]("text", O.Length(10000,varying=true), O.Default(None))
    /** Database column impl SqlType(varchar), Length(8,true), Default(None) */
    val impl: Rep[Option[String]] = column[Option[String]]("impl", O.Length(8,varying=true), O.Default(None))
    /** Database column typ SqlType(varchar), Length(20,true), Default(None) */
    val typ: Rep[Option[String]] = column[Option[String]]("typ", O.Length(20,varying=true), O.Default(None))
    /** Database column end_ SqlType(int4), Default(None) */
    val end: Rep[Option[Int]] = column[Option[Int]]("end_", O.Default(None))
    /** Database column start_ SqlType(int4), Default(None) */
    val start: Rep[Option[Int]] = column[Option[Int]]("start_", O.Default(None))
    /** Database column id SqlType(int4), Default(None) */
    val id: Rep[Option[Int]] = column[Option[Int]]("id", O.Default(None))
    /** Database column embeddedidx SqlType(int4), Default(None) */
    val embeddedidx: Rep[Option[Int]] = column[Option[Int]]("embeddedidx", O.Default(None))

    /** Foreign key referencing TikaEmbMain (database name tika_emb_ner_id_fkey) */
    lazy val tikaEmbMainFk = foreignKey("tika_emb_ner_id_fkey", (id, embeddedidx), TikaEmbMain)(r => (Rep.Some(r.id), Rep.Some(r.embeddedidx)), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table TikaEmbNer */
  lazy val TikaEmbNer = new TableQuery(tag => new TikaEmbNer(tag))

  /** GetResult implicit for fetching TikaMainRow objects using plain SQL queries */
  implicit def GetResultTikaMainRow(implicit e0: GR[Option[String]], e1: GR[Int], e2: GR[Option[Int]]): GR[TikaMainRow] = GR{
    prs => import prs._
    TikaMainRow.tupled((<<?[String], <<?[String], <<[Int], <<?[Int]))
  }
  /** Table description of table tika_main. Objects of this class serve as prototypes for rows in queries. */
  class TikaMain(_tableTag: Tag) extends profile.api.Table[TikaMainRow](_tableTag, schemaName, "tika_main") {
    def * = (path, content, id, searchId) <> (TikaMainRow.tupled, TikaMainRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (path, content, Rep.Some(id), searchId).shaped.<>({r=>import r._; _3.map(_=> TikaMainRow.tupled((_1, _2, _3.get, _4)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column path SqlType(varchar), Length(256,true), Default(None) */
    val path: Rep[Option[String]] = column[Option[String]]("path", O.Length(256,varying=true), O.Default(None))
    /** Database column content SqlType(text), Default(None) */
    val content: Rep[Option[String]] = column[Option[String]]("content", O.Default(None))
    /** Database column id SqlType(int4), PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.PrimaryKey)
    /** Database column search_id SqlType(int4), Default(None) */
    val searchId: Rep[Option[Int]] = column[Option[Int]]("search_id", O.Default(None))

    /** Uniqueness Index over (path) (database name tika_main_path) */
    val index1 = index("tika_main_path", path, unique=true)
    /** Uniqueness Index over (searchId) (database name tiks_main_search_id) */
    val index2 = index("tiks_main_search_id", searchId, unique=true)
  }
  /** Collection-like TableQuery object for table TikaMain */
  lazy val TikaMain = new TableQuery(tag => new TikaMain(tag))


  /** GetResult implicit for fetching TikaMetaRow objects using plain SQL queries */
  implicit def GetResultTikaMetaRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[TikaMetaRow] = GR{
    prs => import prs._
    TikaMetaRow.tupled((<<?[String], <<?[String], <<?[Int]))
  }
  /** Table description of table tika_meta. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: val */
  class TikaMeta(_tableTag: Tag) extends profile.api.Table[TikaMetaRow](_tableTag, schemaName, "tika_meta") {
    def * = (key, `val`, id) <> (TikaMetaRow.tupled, TikaMetaRow.unapply)

    /** Database column key SqlType(varchar), Length(64,true), Default(None) */
    val key: Rep[Option[String]] = column[Option[String]]("key", O.Length(64,varying=true), O.Default(None))
    /** Database column val SqlType(varchar), Length(256,true), Default(None)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `val`: Rep[Option[String]] = column[Option[String]]("val", O.Length(256,varying=true), O.Default(None))
    /** Database column id SqlType(int4), Default(None) */
    val id: Rep[Option[Int]] = column[Option[Int]]("id", O.Default(None))

    /** Foreign key referencing TikaMain (database name tika_meta_id_fkey) */
    lazy val tikaMainFk = foreignKey("tika_meta_id_fkey", id, TikaMain)(r => Rep.Some(r.id), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table TikaMeta */
  lazy val TikaMeta = new TableQuery(tag => new TikaMeta(tag))

  /** GetResult implicit for fetching TikaNerRow objects using plain SQL queries */
  implicit def GetResultTikaNerRow(implicit e0: GR[Option[String]], e1: GR[Option[Int]]): GR[TikaNerRow] = GR{
    prs => import prs._
    TikaNerRow.tupled((<<?[String], <<?[String], <<?[String], <<?[Int], <<?[Int], <<?[Int]))
  }
  /** Table description of table tika_ner. Objects of this class serve as prototypes for rows in queries. */
  class TikaNer(_tableTag: Tag) extends profile.api.Table[TikaNerRow](_tableTag, schemaName, "tika_ner") {
    def * = (text, impl, typ, end, start, id) <> (TikaNerRow.tupled, TikaNerRow.unapply)

    /** Database column text SqlType(text), Default(None) */
    val text: Rep[Option[String]] = column[Option[String]]("text", O.Default(None))
    /** Database column impl SqlType(varchar), Length(8,true), Default(None) */
    val impl: Rep[Option[String]] = column[Option[String]]("impl", O.Length(8,varying=true), O.Default(None))
    /** Database column typ SqlType(varchar), Length(20,true), Default(None) */
    val typ: Rep[Option[String]] = column[Option[String]]("typ", O.Length(20,varying=true), O.Default(None))
    /** Database column end_ SqlType(int4), Default(None) */
    val end: Rep[Option[Int]] = column[Option[Int]]("end_", O.Default(None))
    /** Database column start_ SqlType(int4), Default(None) */
    val start: Rep[Option[Int]] = column[Option[Int]]("start_", O.Default(None))
    /** Database column id SqlType(int4), Default(None) */
    val id: Rep[Option[Int]] = column[Option[Int]]("id", O.Default(None))

    /** Foreign key referencing TikaMain (database name tika_ner_id_fkey) */
    lazy val tikaMainFk = foreignKey("tika_ner_id_fkey", id, TikaMain)(r => Rep.Some(r.id), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table TikaNer */
  lazy val TikaNer = new TableQuery(tag => new TikaNer(tag))

}
