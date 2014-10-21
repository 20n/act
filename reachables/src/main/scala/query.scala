package com.act.query

import org.json._
import scala.collection.JavaConverters._
import act.shared.Chemical
import act.shared.Reaction
import act.server.Molecules.RO
import act.server.Molecules.DotNotation
import act.server.SQLInterface.MongoDB
import com.ggasoftware.indigo.Indigo
import java.net.URLEncoder

import scala.collection.JavaConverters._

object solver {
  val instance = new solver
  def solve = instance.solve _
}

abstract class DBType
case class ChemicalDB() extends DBType
case class ReactionDB() extends DBType
case class OperatorDB() extends DBType
case class OrganismDB() extends DBType
case class CascadesDB() extends DBType

object keyword_search {

  def frontendAddr = "http://localhost:8080" 

  def backendDB = ("localhost", 27017, "actv01")

  def db = new MongoDB(backendDB._1, backendDB._2, backendDB._3)

  /* 
   * GRAMMER RSLT:
   *    RSLT    := { typ:TYPE, val:VALUE, sec:SECTION }
   *    TYPE    := img | url | txt | grp
   *    VALUE   := URL | STR | [RSLT*]
   *    
   *    {typ:img, val:URL}
   *    {typ:txt, val:STR}
   *    {typ:grp, val:[RSLT*]}
   */

  abstract class TYPE
  case class IMG() extends TYPE { override def toString = "img" }
  case class URL() extends TYPE { override def toString = "url" }
  case class TXT() extends TYPE { override def toString = "txt" }
  case class GRP() extends TYPE { override def toString = "grp" }

  abstract class SECT
  case class KNOWN() extends SECT { override def toString = "known" }
  case class PREDICTED() extends SECT { override def toString = "predicted" }

  abstract class VALUE { def json(): Any }
  case class URLv(val u: String) extends VALUE { override def json() = u }
  case class STRv(val s: String) extends VALUE { override def json() = s }
  case class GRPv(val g: List[RSLT]) extends VALUE {
    override def json() = {
      val grp = new JSONArray
      g.foreach(grp put _.json)
      println("grp val: " + grp)
      grp
    }
  }

  class RSLT(val typ: TYPE, val value: VALUE) {
    def json() = { 
      val j = new JSONObject
      j.put("typ", typ)
      j.put("val", value.json)
      j
    }
  }

  def dbfind_actfamilies(keyword: String): Option[List[Reaction]] = {
    val matches = db.keywordInReaction(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def dbfind_operators(keyword: String): Option[List[RO]] = {
    val matches = db.keywordInRO(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def dbfind_chemicals(keyword: String): Option[List[Chemical]] = {
    val matches = db.keywordInChemicals(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def renderURI(q: String) = frontendAddr + "/render/" + URLEncoder.encode(q, "UTF-8")

  def toRSLT(elems: List[RSLT]) = new RSLT(new GRP, GRPv(elems))

  def lookup(keyword: String, collection: DBType): Option[RSLT] = {

    def chemical2rslt(c: Chemical) =
      new RSLT(new IMG, URLv(renderURI(c.getInChI)))
  
    def operator2rslt(o: RO) = {
      val smiles = o.rxn.replaceAllLiterally("[H,*:", "[*:")
      new RSLT(new IMG, URLv(renderURI(smiles)))
    }
  
    def reaction2rslt(r: Reaction) = 
      new RSLT(new TXT, STRv(r.getReactionName))

    def matches2rslt[A](matches:Option[List[A]], mapper: A=>RSLT) = 
      matches match {
        case None => None
        case Some(db_matches) => {
          val c_matches = db_matches.map(mapper)
          val rsl = toRSLT(c_matches)
          Some(rsl)
        }
      }

    println("looking for " + keyword + " in " + collection)

    collection match {
      case CascadesDB() => None
      case OrganismDB() => None
      case ReactionDB() => 
        matches2rslt(dbfind_actfamilies(keyword), reaction2rslt)
      case ChemicalDB() =>
        matches2rslt(dbfind_chemicals(keyword), chemical2rslt)
      case OperatorDB() =>
        matches2rslt(dbfind_operators(keyword), operator2rslt)
    }

  }


}

class solver {
  /* 
   * Receive: query with ";" as phrase separator
   * Respond: json (GRAMMER RSLT) of results
   */
  def solve(query: String) = {
    println("Query received: " + query)

    def tokenize(line: String) = line.split(" ").map(_.trim).toList
    val phrases = query.split(";").map(tokenize)
    val keywrds_collections = phrases.map(annotate_type).filter(_._1.nonEmpty)

    val soln = for {
                  (ks, cs:Set[DBType]) <- keywrds_collections
                  k <- ks
                  c <- cs
                  look = keyword_search.lookup(k,c) 
                  if (look != None)
                } yield look match { case Some(found) => found }

    val combinations = {
      println("TODO: semantic solve e.g., ero+chemical = application")
      List()
    }

    val rslts = keyword_search.toRSLT(soln.toList ++ combinations)
    
    // See api/www/html/nw/semantic-search.js for ajax queries that
    // we need to repond to. Since the call mechanism is through jsonp
    // and the hardcoded callback function "jsonQueryResponse" we use
    // that as the wrapper
    "jsonQueryResponse(" +
      rslts.json.toString + 
    ");"
  }

  val all_collections = Set(ChemicalDB, ReactionDB, OrganismDB, CascadesDB)

  // @in: phrase is a set of tokens, 
  //      e.g., List("tylenol", "chemical", "PABA", "hithere")
  //      e.g., List("tylenol", "biosynthesis", "pathway")
  // @out: 
  //      e.g., (List("tylenol", "PABA", "hithere"), Set(ChemicalDB))
  //      e.g., (List("tylenol"), Set(CascadeDB))
  def annotate_type(phrase: List[String]) = {
    // map the tokens -> (token, possible collection)
    val phrase_dbs = phrase.zip(phrase.map(collection_keyword))

    // map the (token, possible collection) -> 
    //              List(token, None), List(token, Some(..))
    val keys_colls = phrase_dbs.partition(_._2 == None)

    // pick the 1st of the tuple of partitioned lists above
    // and remove the "None" (which is the _2) from it; picking only _1
    val keys =  keys_colls._1.map(_._1)

    // remove the empty strings
    val key_wrds = keys.filter(_ != "")

    // pick the 2nd of the tuple of partitioned lists above
    // and dedup the accumulated list of collections to a set
    // in addition strip the Some away
    val coll_wrds = keys_colls._2.map{ case (_,Some(x)) => x }.toSet

    // if no collection words in the phrase then default: look in all
    val collections = if (coll_wrds.isEmpty) all_collections else coll_wrds

    (key_wrds, collections)
  }

  /*
   * KEYWORDs identifying source collection:
   *    biosynthesis | pathway | cascade  —> db.cascade
   *    chemical | molecule | compound    -> db.chemicals
   *    substrate | product | cas         -> db.chemicals
   *    reactant | smiles | inchi         —> db.chemicals
   *    enzyme | gene | reaction | ec     -> db.{actfamilies, seq, {e,c,b}ros}
   *    ro | ero | cro | bro              —> db.{actfamilies, seq, {e,c,b}ros}
   *    organism                          —> db.organismnames
   * If collection C known then query keywords looked up in db.C.query_terms
   * If not known then lookup in all collections
   */
  def collection_keyword(token: String): Option[DBType] = {
    val cascadesPrefixes = Set("biosynthesi", "pathway", "cascade")
    val chemicalPrefixes = Set("chemical", "molecule", "compound", 
                                "substrate", "product", "cas", 
                                "reactant", "inchi", "smile")
    val reactionPrefixes = Set("enzyme", "gene", "reaction", "ec")
    val operatorPrefixes = Set("ro", "ero", "cro", "bro")
    val organismPrefixes = Set("organism")

    def hasPrefix(s: Set[String]) = s.exists(x => token startsWith x)

    val terms = List((cascadesPrefixes, new CascadesDB),
                     (reactionPrefixes, new ReactionDB),
                     (operatorPrefixes, new OperatorDB),
                     (chemicalPrefixes, new ChemicalDB),
                     (organismPrefixes, new OrganismDB))
    // find the pair (prefixes, db) which contains this token's prefix
    // and return the corresponding db
    val located = terms.find{ case (preS, db) => hasPrefix(preS) }
    located match { case None => None ; case Some((p, db)) => Some(db) }
  }

}
