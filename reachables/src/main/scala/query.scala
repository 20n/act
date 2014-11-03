package com.act.query

import org.json._
import scala.collection.JavaConverters._
import act.shared.Chemical
import act.shared.Reaction
import act.shared.Seq;
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
case class SequenceDB() extends DBType
case class OperatorDB() extends DBType
case class OrganismDB() extends DBType
case class CascadesDB() extends DBType

/* 
 * GRAMMER RSLT:
 *    RSLT    := { typ:TYPE, val:VALUE, sec:SECTION }
 *    TYPE    := img | url | txt | grp
 *    VALUE   := URL | STR | SEP | [RSLT*]
 *    
 *    {typ:img, val:URL}
 *    {typ:txt, val:STR}
 *    {typ:sep, val:STR} // empty separator, comment str ignored (for now)
 *    {typ:grp, val:[RSLT*]}
 */

abstract class TYPE
case class IMG() extends TYPE { override def toString = "img" }
case class URL() extends TYPE { override def toString = "url" }
case class TXT() extends TYPE { override def toString = "txt" }
case class GRP() extends TYPE { override def toString = "grp" }
case class SEP() extends TYPE { override def toString = "sep" }

abstract class SECTION
case class KNOWN() extends SECTION { override def toString = "known" }
case class PREDICTED() extends SECTION { override def toString = "predicted" }

abstract class VALUE { def json(): Any }
case class URLv(val u: String) extends VALUE { override def json() = u }
case class STRv(val s: String) extends VALUE { override def json() = s }
case class GRPv(val g: List[RSLT]) extends VALUE {
  override def json() = {
    val grp = new JSONArray
    g.foreach(grp put _.json)
    // println("grp val: " + grp)
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

object backend {

  def backendDB = ("localhost", 27017, "actv01")

  def db = new MongoDB(backendDB._1, backendDB._2, backendDB._3)

  def keywordInReaction = db.keywordInReaction _
  def keywordInChemical = db.keywordInChemicals _
  def keywordInRO = db.keywordInRO _
  def keywordInSequence = db.keywordInSequence _

  def getReaction = db.getReactionFromUUID _
  def getChemical = db.getChemicalFromChemicalUUID _

}

object keyword_search {

  def dbfind_actfamilies(keyword: String): Option[List[Reaction]] = {
    val matches = backend.keywordInReaction(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def dbfind_operators(keyword: String): Option[List[RO]] = {
    val matches = backend.keywordInRO(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def dbfind_chemicals(keyword: String): Option[List[Chemical]] = {
    val matches = backend.keywordInChemical(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def dbfind_sequences(keyword: String): Option[List[Seq]] = {
    val matches = backend.keywordInSequence(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def lookup(keyword: String, collection: DBType): Option[List[Object]] = {

    println("looking for " + keyword + " in " + collection)

    collection match {
      case CascadesDB() => None
      case OrganismDB() => None
      case SequenceDB() => dbfind_sequences(keyword)
      case ReactionDB() => dbfind_actfamilies(keyword)
      case ChemicalDB() => dbfind_chemicals(keyword)
      case OperatorDB() => dbfind_operators(keyword)
    }

  }

}

class semantic_combinations(set_hits: Array[List[Object]]) {
  // each element of set_hits is the result list of matches for 
  // a specific keyword in a specific db, e.g., it could be
  // paracetamol in chemicalsDB in which case it will be a
  // List[Chemical] or if it is a paracetamol in reactionDB 
  // then it will be List[Reaction] etc. It is a list because 
  // multiple docs might hit the same keyword in the specific DB.

  // We first filter out the hits by the type (ro, rxn, chem)
  // then we cross product across the sets and output the 
  // computed objects

  val (ros, rxns, chems) =
  {
    def isRO(l: List[Object]) = 
              l(0) match { case _:RO => true; case _ => false }
    def isRx(l: List[Object]) = 
              l(0) match { case _:Reaction => true; case _ => false }
    def isCh(l: List[Object]) = 
              l(0) match { case _:Chemical => true; case _ => false }
    val ro = set_hits.filter(isRO).flatten.map(_.asInstanceOf[RO])
    val rx = set_hits.filter(isRx).flatten.map(_.asInstanceOf[Reaction])
    val ch = set_hits.filter(isCh).flatten.map(_.asInstanceOf[Chemical])
    (ro, rx, ch)
  }

  def apply(ro: RO, cs: Array[Chemical]): List[Reaction] = {
    println("TODO: Need to apply ROs to chemical substrate set")
    println("TODO: RO: " + ro)
    println("TODO: Chemical: " + cs.map(c => c.getInChI))
    List()
  }
  def apply(ro: RO, c: Chemical): List[Reaction] = {
    println("TODO: Need to apply ROs to chemical")
    println("TODO: RO: " + ro)
    println("TODO: Chemical: " + c.getInChI)
    List()
  }
  def check(ro: RO, r: Reaction): List[Reaction] = {
    println("TODO: Need to check reaction against ROs")
    println("TODO: RO: " + ro)
    println("TODO: Reaction: " + r.getReactionName)
    List()
  }

  val ro_chems: List[Reaction] = {
    val apps = for (ro <- ros; chem <- chems) yield apply(ro, chem)
    apps.flatten.toList
  }

  val ro_rxns : List[Reaction] = {
    val checks  = for (ro <- ros; rxn <- rxns) yield check(ro, rxn)
    val apps    = for (ro <- ros; rxn <- rxns) 
                    yield apply(ro, rxn.getSubstrates.map(backend.getChemical))
    (checks.flatten.toList ++ apps.flatten.toList)
  }

  def get() = Array(ro_chems, ro_rxns)
}

object toRSLT {

  def frontendAddr = "http://localhost:8080" 

  def renderURI(q: String) = frontendAddr + "/render?q=" + URLEncoder.encode(q, "UTF-8")

  val separator = new RSLT(new SEP, new STRv(""))

  def to_rslt(r: Reaction): RSLT = {
    new RSLT(new TXT, STRv(r.getReactionName))
  }

  def to_rslt(s: Seq): RSLT = {
    def row(hdr: String, data: String) = {
      to_rslt(List(to_rslt(hdr), separator, to_rslt(data)))
    }

    def row_rslt(hdr: String, data: RSLT) = {
      to_rslt(List(to_rslt(hdr), separator, data))
    }

    def reaction_desc(rid: Long):RSLT = {
      to_rslt(backend getReaction rid)
    }
    
    def substrate_desc(cid: Long):RSLT = {
      to_rslt(backend getChemical cid)
    }

    val aa_seq    = row("AA Sequence",  s.get_org_name)
    val gene_name = row("Gene", s.get_gene_name)
    val evidence  = row("Evidence", s.get_evidence)
    val uniprot_ids = s.get_uniprot_accession.asScala.toList
    val uniprot_acc = row_rslt("Uniprot Accession", to_rslt(uniprot_ids.map(to_rslt))) 
    val uniprot_act = row("Catalytic activity (Uniprot annotation)", s.get_uniprot_activity)
    val ec_num    = row("EC Number",    s.get_ec)
    val organism  = row("Organism",     s.get_org_name)

    val rxns = s.getReactionsCatalyzed.asScala.toList.map(reaction_desc(_))
    val substrates = s.getCatalysisSubstrates.asScala.toList.map(substrate_desc(_))
    val num_rxns  = row("Num reactions catalyzed",rxns.size.toString)
    val rxn_desc  = row_rslt("Reactions catalyzed",to_rslt(rxns))
    val num_subs  = row("Num substrates accepted",substrates.size.toString)
    val sub_desc  = row_rslt("Substrates accepted",to_rslt(substrates))

    new RSLT(new GRP, new GRPv(List(
      new RSLT(new GRP, new GRPv(List(
          aa_seq, separator,
          gene_name, separator,
          uniprot_acc, separator,
          uniprot_act, separator,
          ec_num, separator,
          organism, separator,
          num_rxns, separator,
          rxn_desc, separator,
          num_subs, separator,
          sub_desc, separator
      )))
    )))
  }

  def to_rslt(c: Chemical): RSLT = {
    new RSLT(new IMG, URLv(renderURI(c.getInChI)))
  }
  
  def to_rslt(txt: String): RSLT = {
    new RSLT(new TXT, new STRv(txt))
  }

  def to_rslt(grp: List[RSLT]): RSLT = {
    new RSLT(new GRP, new GRPv(grp))
  }

  def to_rslt(o: RO): RSLT = {
    val hdr_rxns = to_rslt("Witness reactions")
    val hdr_subs = to_rslt("Applicable substrates")

    // convert each reaction id to Reaction object using a pull from DB
    val witnesses = o.getWitnessRxns.asScala.toList.map(x => backend.getReaction(x.toLong))

    // convert each reaction object to a RSLT description of it & wrap in GRP
    val rxns = to_rslt(witnesses.map(to_rslt))

    // create a RSLT object of the rxn header and the rxn data
    val rxn_desc = to_rslt(List(hdr_rxns, separator, rxns))

    // extract the non-cofactor substrates from each rxn
    val rxn_substrates = {
      val all_substrates = witnesses.map(_.getSubstrates).flatten
      val all_chemicals = all_substrates.map(backend.getChemical)
      val non_cofactor_chemicals = all_chemicals.filter(!_.isCofactor)
      non_cofactor_chemicals
    }

    // convert each substrate molecule to a RSLT of it & wrap in GRP
    val substr = to_rslt(rxn_substrates.map(to_rslt))

    // create a RSLT object of the header & substrate data & wrap in GRP
    val sub_desc = to_rslt(List(hdr_subs, separator, substr))

    // add a data field of num witness reactions & wrap in GRP
    val num_rxns = to_rslt(List(
                      to_rslt("Num witness rxns"),
                      separator,
                      to_rslt(witnesses.size.toString)
                   ))

    // if a BRO then just the text; else if renderable then smiles
    def operator_rendering(o: RO) = {
      if (!o.hasRxnSMILES) {
        // BROs extend RO but don't have a rxn string
        // so we just output its toString
        new RSLT(new TXT, STRv(o.toString))
      } else {
        val smiles = o.rxn.replaceAllLiterally("[H,*:", "[*:")
        new RSLT(new IMG, URLv(renderURI(smiles)))
      }
    }

    // output four rows of data: 1. img, 2. #rxns, 3. rxn_strs, 4. substr
    new RSLT(new GRP, new GRPv(List(
      new RSLT(new GRP, new GRPv(List(
          operator_rendering(o), separator,
          num_rxns, separator,
          rxn_desc, separator,
          sub_desc, separator
      )))
    )))
  }
  
  def wrapped_rslt(db_matches:List[Object]) = {
    val c_matches = db_matches.map { o =>
      o match {
        case c : Chemical => to_rslt(c)
        case r : Reaction => to_rslt(r)
        case x : RO => to_rslt(x)
        case s : Seq => to_rslt(s)
      }
    }
    to_rslt(c_matches)
  }

}

class solver {
  /* 
   * Receive: query with ";" as phrase separator
   * Respond: json (GRAMMER RSLT) of results
   */
  def solve(query: String, jsonp_cb_wrapper: String) = {
    println("Query received: " + query)

    def tokenize(line: String) = line.split(" ").map(_.trim).toList
    val phrases = query.split(";").map(tokenize)
    val keywrds_collections = phrases.map(annotate_type).filter(_._1.nonEmpty)

    val soln_objs = for {
                      (ks, cs:Set[DBType]) <- keywrds_collections
                      k <- ks
                      c <- cs
                      look = keyword_search.lookup(k,c) 
                      if (look != None)
                    } yield look match { case Some(found) => found }
    val soln = soln_objs.map(toRSLT.wrapped_rslt)

    val combined_objs = new semantic_combinations(soln_objs)
    val combinations = combined_objs.get.map(toRSLT.wrapped_rslt)

    val rslts = toRSLT.to_rslt(soln.toList ++ combinations.toList)
    
    // See api/www/html/nw/semantic-search.js for ajax queries that
    // we need to repond to. Since the call mechanism is through jsonp
    // we need to wrap the json response in a function wrapper with 
    // a callback name that is passed as input parameter to the request
    jsonp_cb_wrapper + "(" + rslts.json.toString + ");"
  }

  val all_collections = Set(ChemicalDB, 
                            ReactionDB, 
                            SequenceDB, 
                            OrganismDB, 
                            CascadesDB)

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
   * If not known then look it up in all collections
   */
  def collection_keyword(token: String): Option[DBType] = {
    val cascadesPrefixes = Set("biosynthesis", "pathway", "cascade")
    val chemicalPrefixes = Set("chemical", "molecule", "compound", 
                                "substrate", "product", "cas", 
                                "reactant", "inchi", "smile")
    val enzymesqPrefixes = Set("enzyme", "gene", "sequence", "seq") 
    val reactionPrefixes = Set("reaction", "ec")
    val operatorPrefixes = Set("ro", "ero", "cro", "bro")
    val organismPrefixes = Set("organism")

    def hasPrefix(s: Set[String]) = s.exists(token equals _)

    val terms = List((cascadesPrefixes, new CascadesDB),
                     (reactionPrefixes, new ReactionDB),
                     (enzymesqPrefixes, new SequenceDB),
                     (operatorPrefixes, new OperatorDB),
                     (chemicalPrefixes, new ChemicalDB),
                     (organismPrefixes, new OrganismDB))
    // find the pair (prefixes, db) which contains this token's prefix
    // and return the corresponding db
    val located = terms.find{ case (preS, db) => hasPrefix(preS) }
    located match { case None => None ; case Some((p, db)) => Some(db) }
  }

}
