package com.act.query

import org.json._
import scala.collection.JavaConverters._
import act.shared.Chemical
import act.shared.Reaction
import act.shared.Seq;
import act.shared.sar.SAR;
import act.shared.sar.SARConstraint
import act.server.Molecules.RO
import act.server.Molecules.DotNotation
import act.server.SQLInterface.MongoDB
import act.installer.QueryKeywords
import com.ggasoftware.indigo.Indigo
import java.net.URLEncoder
import com.mongodb.DBObject
import com.mongodb.BasicDBList
import com.mongodb.BasicDBObject

import scala.util.Random
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
 *    VALUE   := { href:URL, text:STR } | STR | SEP | [RSLT*] | BUT
 *    
 *    {typ:img, val:URL}
 *    {typ:url, val:{href:URL, text:STR}}
 *    {typ:txt, val:STR}
 *    {typ:sep, val:STR} // empty separator, comment str ignored (for now)
 *    {typ:grp, val:[RSLT*]}
 *    {typ:but, val:STR} // button, comment str becomes the display str
 */

abstract class TYPE
case class IMG() extends TYPE { override def toString = "img" }
case class URL() extends TYPE { override def toString = "url" }
case class TXT() extends TYPE { override def toString = "txt" }
case class GRP() extends TYPE { override def toString = "grp" }
case class SEP() extends TYPE { override def toString = "sep" }
case class BUT() extends TYPE { override def toString = "but" }

abstract class SECTION
case class KNOWN() extends SECTION { override def toString = "known" }
case class PREDICTED() extends SECTION { override def toString = "predicted" }

abstract class VALUE { def json(): Any }
case class URLv(val u: String, val t: String) extends VALUE { 
  override def json(): JSONObject = { 
    val url = new JSONObject
    url.put("href", u)
    url.put("text", t)
    url
  } 
}
case class STRv(val s: String) extends VALUE { 
  override def json(): String = s 
}
case class GRPv(val g: List[RSLT]) extends VALUE {
  override def json(): JSONArray = {
    val grp = new JSONArray
    g.foreach(grp put _.json)
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

  val backendDB = ("localhost", 27017, "actv01")
  val db = new MongoDB(backendDB._1, backendDB._2, backendDB._3)

  def keywordInReaction = db.keywordInReaction _
  def keywordInChemical = db.keywordInChemicals _
  def keywordInRO = db.keywordInRO _
  def keywordInSequence = db.keywordInSequence _
  def keywordInCascade  = db.keywordInCascade _

  def getReaction = db.getReactionFromUUID _
  def getChemical = db.getChemicalFromChemicalUUID _
  def getSequence = db.getSeqFromID _
  def getERO      = db.getEROForRxn _
  def getCRO      = db.getCROForRxn _

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

  def dbfind_cascades(keyword: String): Option[List[DBObject]] = {
    val matches = backend.keywordInCascade(keyword)
    (matches size) match {
      case 0 => None
      case _ => Some( matches.asScala.toList )
    }
  }

  def lookup(keyword: String, collection: DBType): Option[List[Object]] = {

    println("looking for " + keyword + " in " + collection)

    collection match {
      case OrganismDB() => None
      case CascadesDB() => dbfind_cascades(keyword)
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

  def to_rslt_url(url: String, display_txt: String): RSLT = {
    new RSLT(new URL, URLv(url, display_txt))
  }

  def frontendAddr = "http://localhost:8080" 

  def to_rslt_render(q: String): RSLT = {
    val url = frontendAddr + "/render?q=" + URLEncoder.encode(q, "UTF-8")
    new RSLT(new IMG, URLv(url, url))
  }

  def to_rslt_lazy_img(q: String, display_txt: String): RSLT = {
    val url = frontendAddr + 
                "/render?q=" + URLEncoder.encode(q, "UTF-8")
    new RSLT(new URL, URLv(url, display_txt))
  }

  val separator = new RSLT(new SEP, new STRv("#f9f9f9"))

  val bold_separator = new RSLT(new SEP, new STRv("#aaaaaa"))

  def button(displaytxt: String): RSLT = {
    new RSLT(new BUT, new STRv(displaytxt))
  }

  def to_rslt(r: Reaction): RSLT = {
    new RSLT(new TXT, STRv(r.getReactionName))
  }

  def to_rslt_brief(r: Reaction): RSLT = {
    new RSLT(new TXT, STRv(truncate(r.getReactionName)))
  }

  def row(hdr: String, data: String) = {
    to_rslt(List(to_rslt(hdr), separator, to_rslt(data)))
  }

  def row_rslt(hdr: String, data: RSLT) = {
    to_rslt(List(to_rslt(hdr), separator, data))
  }

  def truncate(ss: String) = { 
    if (ss.length > 60) { ss.substring(0, 60) + "  ..." } else { ss }
  }

  def to_rslt_brief(s: Seq): RSLT = {
    to_rslt(truncate(s.get_sequence))
  }

  def to_rslt(s: Seq): RSLT = {
    def reaction_desc(rid: Long):RSLT = {
      to_rslt(backend getReaction rid)
    }
    
    def substrate_desc(cid: Long):RSLT = {
      to_rslt_brief(backend getChemical cid)
    }

    def constraint_desc(x: (Object, SARConstraint)) = {
      val data = x._1
      val typ = x._2

      if (typ.contents == SAR.ConstraintContent.substructure) {
        to_rslt(List(
          to_rslt(List(
            to_rslt_render(data.toString), 
            separator, 
            to_rslt(data.toString)
          )),
          separator, to_rslt(" as " + typ)))
      } else {
        to_rslt(data + " as " + typ)
      }
    }
    
    val sequence  = truncate(s.get_sequence)
    val aa_seq    = row("AA Sequence",  sequence)
    val gene_name = row("Gene", s.get_gene_name)
    val evidence  = row("Evidence", s.get_evidence)
    val uniprot_ids = s.get_uniprot_accession.asScala.toList
    val uniprot_acc = row_rslt("Uniprot Accession", to_rslt(uniprot_ids.map(to_rslt))) 
    val uniprot_act = row("Catalytic activity (Uniprot annotation)", s.get_uniprot_activity)
    val ec_num    = row("EC Number",    s.get_ec)
    val organism  = row("Organism",     s.get_org_name)

    val rxns_list = s.getReactionsCatalyzed.asScala.toList
    val rxns      = rxns_list.map(reaction_desc(_))
    val num_rxns  = row("Num reactions catalyzed",rxns.size.toString)
    val rxn_desc  = row_rslt("Reactions catalyzed",to_rslt(rxns))

    val substrates = s.getCatalysisSubstratesDiverse.asScala.toList.map(substrate_desc(_))
    val num_subs  = row("Num substrate diversity",substrates.size.toString)
    val sub_desc  = row_rslt("Substrate diversity",to_rslt(substrates))

    val csubstrates= s.getCatalysisSubstratesUniform.asScala.toList.map(substrate_desc(_))
    val num_csubs = row("Num substrate common across all reactions",csubstrates.size.toString)
    val csub_desc = row_rslt("Substrates common across all reactions",to_rslt(csubstrates))

    val constrnts = s.getSAR.getConstraints.asScala.toList.map(constraint_desc)
    val sar_desc  = row_rslt("SAR", to_rslt(constrnts))

    def rxn2ero(x: Long): RO = { backend getERO x.toInt }
    val eros      = rxns_list.map(rxn2ero(_)).filter(_ != null).map(to_rslt)
    val ero_desc  = row_rslt("EROs", to_rslt(eros))

    to_rslt_keep_orient(List(
          aa_seq, separator,
          gene_name, separator,
          uniprot_acc, separator,
          uniprot_act, separator,
          ec_num, separator,
          organism, separator,
          num_rxns, separator,
          rxn_desc, separator,
          num_subs, separator,
          sub_desc, separator,
          num_csubs, separator,
          csub_desc, separator,
          sar_desc, separator,
          ero_desc, separator
    ))
  }

  // by default, we load images eagerly, 
  // 2nd param lazy_img_load = false
  def to_rslt_brief(c: Chemical): RSLT = to_rslt_brief(c, false)

  // by default, we load images eagerly, 
  // 2nd param lazy_img_load = false
  def to_rslt(c: Chemical): RSLT = to_rslt(c, false)

  def to_rslt_brief(c: Chemical, lazy_img_load: Boolean): RSLT = {
    if (lazy_img_load) {
      // lazy img, show link in 
      // front end to be clicked on
      val chem_actid = QueryKeywords.actid(c)
      // use the chem actid both as the 
      // display text and callback fn name
      to_rslt_lazy_img(c.getInChI, chem_actid)
    } else {
      // load img immediately in front end
      to_rslt_render(c.getInChI)
    }
  }

  def to_rslt(c: Chemical, lazy_img_load: Boolean): RSLT = {
    val imge            = to_rslt_brief(c, lazy_img_load)
    val inc             = c.getInChI
    val display_inc     = if (inc startsWith "InChI=/FAKE/METACYC") "Big molecule" else inc
    val inchi           = to_rslt(display_inc)
    val name            = if (c.getShortestBRENDAName != null) 
                             to_rslt(c.getShortestBRENDAName) else to_rslt("")
    val actid           = to_rslt(QueryKeywords.actid(c))
    def refid(r: Chemical.REFS) = c.getRef(r,Array("dbid")).asInstanceOf[String]
    def rslt4ref(r: Chemical.REFS, prefix: String, db: String)  = {
      val id = refid(r)
      val url = prefix + id
      if (id != null) 
        Some(to_rslt_url(url, db + " (" + id + ")"))
      else 
        None // to_rslt("-" * 60)
    }
    val dbprefix = "http://www.drugbank.ca/drugs/"
    val wiki = rslt4ref(Chemical.REFS.WIKIPEDIA, "", "wikipedia")
    val drug = rslt4ref(Chemical.REFS.DRUGBANK,  dbprefix, "drugbank")
    val who  = rslt4ref(Chemical.REFS.WHO, dbprefix, "who")
  
    val rows = List(
      row_rslt("", imge),
      row_rslt("Name ", name),
      row_rslt("InChI", inchi),
      row_rslt("ActID", actid)
    ) ++ 
    (wiki match {
      case Some(w) => List(row_rslt("Wikipedia", w))
      case None    => List()
    }) ++ 
    (drug match {
      case Some(d) => List(row_rslt("Drugbank", d))
      case None    => List()
    }) ++
    (who match {
      case Some(w) => List(row_rslt("WHO Med", w))
      case None    => List()
    })
    to_rslt_keep_orient(rows)
  }

  def to_rslt(txt: String): RSLT = {
    new RSLT(new TXT, new STRv(txt))
  }

  def to_rslt_keep_orient(elems: List[RSLT]) = {
    to_rslt(List(to_rslt(elems)))
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
    val substr = to_rslt(rxn_substrates.map(to_rslt_brief))

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
        to_rslt_render(smiles)
      }
    }

    // output four rows of data: 1. img, 2. #rxns, 3. rxn_strs, 4. substr
    to_rslt_keep_orient(List(
          operator_rendering(o), separator,
          num_rxns, separator,
          rxn_desc, separator,
          sub_desc, separator
    ))
  }

  def to_rslt_brief(cscd: DBObject): RSLT = {
    val ondemand_imgs = false
    val cascade       = new PlaceholderCascadeExtractor(cscd)
    val chemical      = backend getChemical (cascade.target)
    val chem          = to_rslt(chemical, ondemand_imgs)
    val num_designs   = to_rslt(cascade.paths.size.toString)
    val build_button  = button("Place Preorder for Microbe")
    val designs       = row_rslt("Number of distinct designs", num_designs)

    to_rslt(List(
      to_rslt(List(chem)), separator, 
      designs, separator, 
      build_button
    ))
  }

  def to_rslt(cscd: DBObject): RSLT = {
    val cascade = new PlaceholderCascadeExtractor(cscd)

    val path_alts = cascade.paths.zipWithIndex

    val random = new Random(1)
    def random_seq = {
      val rand =  "MSAFNTTLPSLDYDDDTLREHLQGADIPTLLLTVAHLTGDLQILKPN" +
                  "WKPSIAMGVARSGMDLETEAQVREFCLQRLIDFRDSGQPAPGRPTSD" +
                  "QLHILGTWLMGPVIEPYLPLIAEEAVMGVARSGMDLETEAQVREFCL" +
                  "QRLIDFRDSGQPAPGRPTSDQLHILGTWLMGPVIEPYLPLIAEEAMG" +
                  "VARSGMDLETEAQVREFCLQRLIDFRDSGQPAPGRPTSDQLHILGTW" +
                  "LMGPVIEPYLPLIAEEAMGVARSGMDLETEAQVREFCLQRLIDFRDS" +
                  "GQPAPGRPTSDQLHILGTWLMGPVIEPYLPLIAEEAMGVARSGMDLE" +
                  "TEAQVREFCLQRLIDFRDSGQPAPGRPTSDQLHILGTWLMGPVIEPY"
      val ridx = random.nextInt(rand.length-50)+50 // not smaller than 50
      truncate(rand.substring(ridx))
    }

    def steps2rslt(steps: Map[Int, Set[Long]]): RSLT = {
      def convstep(step: Int, rxns: Set[Long]) = {
        def rxn2desc(rid: Long) = {
          val rxn = backend getReaction rid
          val seq = {
            val true_seqs = (rxn getSequences).asScala.map(sid => to_rslt_brief(backend getSequence sid))
            if (true_seqs.size > 0) true_seqs else List(to_rslt(random_seq))
          }
          val rxn_rslt = to_rslt_brief(backend getReaction rid)
          to_rslt_keep_orient(List(rxn_rslt) ++ seq)
        }
        val rxn_desc = rxns.toList.map(rxn2desc) ++ List(separator)
        row_rslt("Gene " + step, to_rslt(rxn_desc))
      }
      val sorted_steps = steps.toList.sortBy(_._1)
      to_rslt(sorted_steps.map{ case (s,rs) => convstep(s,rs) })
    }

    val build_button = button("Build Microbe")

    val path_rslts = path_alts.map{ 
      case (optpath, num) => {
        // optpath is List[Map(step -> set(rxns))]
        // the list because there are multiple substrates 
        // to follow backwards
        def substrate_opt(s: Map[Int, Set[Long]], i: Int) = row_rslt("--", steps2rslt(s))
        val optpath_rslt = optpath.zipWithIndex.map{ case (s,i) => substrate_opt(s,i) }
        val delim_options = to_rslt(optpath_rslt ++ List(separator))
        val opt_w_button = to_rslt_keep_orient(List(delim_options, build_button))
        row_rslt("DNA Design " + (num + 1), opt_w_button)
      }
    }

    val chem = to_rslt_brief(backend getChemical (cascade.target))

    to_rslt_keep_orient(List( chem, separator, to_rslt_keep_orient(path_rslts)))
  }
  
  def wrapped_rslt(db_matches:List[Object]) = {
    def detail_rslt(o: Object) = {
      o match {
        case x : RO       => to_rslt(x)
        case c : Chemical => to_rslt(c)
        case r : Reaction => to_rslt(r)
        case s : Seq      => to_rslt(s)
        case p : DBObject => to_rslt(p)
      }
    }

    def brief_rslt(o: Object) = {
      o match {
        case x : RO       => to_rslt(x)
        case c : Chemical => to_rslt_brief(c)
        case r : Reaction => to_rslt_brief(r)
        case s : Seq      => to_rslt_brief(s)
        case p : DBObject => to_rslt_brief(p)
      }
    }

    // if > 5 matches are returned, we output a condensed
    // version of the matched results; if <=5 then details shown
    val brief_or_detailed_fn =  {
      if (db_matches.size > 5) 
        brief_rslt _
      else
        detail_rslt _
    }

    // convert each matched object to a display rslt
    val c_matches = db_matches.take(10).map(brief_or_detailed_fn)
    // add a bold_separator after each match to search
    val c_matches_sep = c_matches.map{ m => List(m, bold_separator) }.flatten

    to_rslt(c_matches_sep)
  }

}

class PlaceholderCascadeExtractor(o: DBObject) {
  val target = o.get("target").asInstanceOf[Long] // long
  val fanin  = o.get("fan_in").asInstanceOf[BasicDBList] // array: BasicDBList

  // paths: List                [List                      [Map[Int, Set[Long]]]] 
  //      : many step0 options   all substrates of step0    step -> set(rxns)
  val paths  = extract_paths(fanin) 

  def extract_paths(in: BasicDBList): List[List[Map[Int, Set[Long]]]] = {
    to_list(in, new BasicDBObject).map(extract_paths)
  }

  def to_list[X](l: BasicDBList, typ: X): List[X] = {
    l.asInstanceOf[java.util.List[X]].asScala.toList
  }

  def set_of_all_rxns(stripe_rxns: BasicDBList) = {
    (for { rxn <- to_list(stripe_rxns, 0L) } yield rxn).toSet
  }

  def map_listing_all_steps(stripes: BasicDBList) = {
    (
      for {
        stripe_data <- to_list(stripes, new BasicDBObject)
        stripe_num = stripe_data.get("stripe").asInstanceOf[Int]
        stripe_rxns = stripe_data.get("rxns").asInstanceOf[BasicDBList] // array(rxnids)
      } yield stripe_num -> set_of_all_rxns(stripe_rxns)
    ).toMap
  }

  def extract_paths(in: DBObject): List[Map[Int, Set[Long]]] = {
    val rxn_up    = in.get("rxn").asInstanceOf[Long] // long
    val step0     = 0 -> Set(rxn_up)

    // array of ( array of { stripe: depth, rxns: [rxnids] } )
    val best_path = in.get("best_path").asInstanceOf[BasicDBList] 

    // best path is an array because it contains the best path upwards
    // from each of substrates of the rxn at step 0
    val best_path_diff_substrates = to_list(best_path, new BasicDBList)

    val upsteps = 
    for {
      // we pick the path upwards for each upreaching substrate
      path_up <- best_path_diff_substrates
      // we add the step0 as that is always the upleading step
      // to the rest of the steps that we extract from path_up
      steps = map_listing_all_steps(path_up) + step0
    } yield 
      // yields 0->{r0}, 1->{r1, r2}, 2->{r3, r4}, i.e, stripe->set_rxns
      steps

    upsteps
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
