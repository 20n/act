package com.act.query

import org.json._
import scala.collection.JavaConverters._

object solver {
  val instance = new solver
  def solve = instance.solve _
}

abstract class DBType
case class ChemicalDB() extends DBType
case class ReactionDB() extends DBType
case class OrganismDB() extends DBType
case class CascadesDB() extends DBType

object keyword_search {
  /* 
   * GRAMMER RSLT:
   *    RSLT    := { typ:TYPE, val:VALUE, sec:SECTION }
   *    SECTION := known | predicted
   *    TYPE    := img | url | txt | grp
   *    VALUE   := URL | STR | [RSLT*]
   *    
   *    {typ:img, sec:SECTION, val:URL}
   *    {typ:txt, sec:SECTION, val:STR}
   *    {typ:grp, sec:SECTION, val:[RSLT*]}
   */

  abstract class TYPE
  case class IMG() extends TYPE
  case class URL() extends TYPE
  case class TXT() extends TYPE
  case class GRP() extends TYPE

  abstract class SECT
  case class KNOWN() extends SECT
  case class PREDICTED() extends SECT

  abstract class VALUE
  case class URLv(u: String) extends VALUE
  case class STRv(s: String) extends VALUE
  case class GRPv(g: List[RSLT]) extends VALUE

  class RSLT(val typ: TYPE, val value: VALUE, val sec: SECT)

  def toJSON(r: RSLT) = { 
    val j = new JSONObject
    j.put("typ", r.typ)
    j.put("sec", r.sec)
    j.put("val", r.value)
    j
  }

  def lookup(keyword: String, collection: DBType) = {
    // lookup keyword in the collection and ret toJSON(RSLT)

    println("looking for " + keyword + " in " + collection)
    val rsl = new RSLT(new TXT, STRv(keyword), new KNOWN)















    toJSON(rsl)
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

    val lookup = for ((ks, cs:Set[DBType]) <- keywrds_collections;
                      k <- ks; 
                      c <- cs) 
                      yield keyword_search.lookup(k,c) 

    val combinations = {
      println("TODO: semantic solve e.g., ero+chemical = application")
      List()
    }

    val rslts = lookup.toList ++ combinations
    
    json(rslts).toString
  }

  def json(results: List[JSONObject]) = new JSONArray(results.asJava)

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
    val reactionPrefixes = Set("enzyme", "gene", "reaction", 
                                "ec", "ro", "ero", "cro", "bro")
    val organismPrefixes = Set("organism")

    def hasPrefix(s: Set[String]) = s.exists(x => token startsWith x)

    val terms = List((cascadesPrefixes, new CascadesDB),
                     (reactionPrefixes, new ReactionDB),
                     (chemicalPrefixes, new ChemicalDB),
                     (organismPrefixes, new OrganismDB))
    // find the pair (prefixes, db) which contains this token's prefix
    // and return the corresponding db
    val located = terms.find{ case (preS, db) => hasPrefix(preS) }
    located match { case None => None ; case Some((p, db)) => Some(db) }
  }

}
