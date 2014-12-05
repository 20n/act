package com.act.ro

import scala.reflect.ClassTag
import scala.io.Source
import java.io.FileWriter
import java.io.Serializable
import act.server.SQLInterface.MongoDB
import act.shared.Chemical
import act.shared.Seq
import act.shared.sar.SARConstraint
import act.shared.sar.SAR
import act.server.Molecules.RO
import act.server.Molecules.ERO
import act.server.Molecules.RxnWithWildCards
import act.server.Molecules.RxnTx
import collection.JavaConverters._
import com.ggasoftware.indigo.IndigoException
import com.ggasoftware.indigo.Indigo
import com.ggasoftware.indigo.IndigoInchi
import act.server.FnGrpDomain.FnGrpAbstractChemInChI

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._

class CString(name: String, inchi: String) extends Serializable {
  val nm = name
  val i = inchi

  override def toString() = "[" + i + ", " + name + "]"
  override def equals(o: Any) = o match {
    case that: CString => that.i.equals(this.i)
    case _ => false
  }
  override def hashCode = i.hashCode
}

class RString(val id: Int, val s_inchis: List[CString], val p_inchis: List[CString]) extends Serializable {
  override def toString() = "act:r:" + id
  override def equals(o: Any) = o match {
    case that: RString => that.id.equals(this.id)
    case _ => false
  }
  override def hashCode = id.hashCode
}

object apply {
  /*
   * "roapply check" runs using "sbt assembly; spark-submit..." not "sbt run"
   */

  /*
   * There are three functions of use in this object:
   * exec(Array(check|expand, rofile, molfile))
   * Runs either the expansion (expects rows of substrates in molfile)
   * Or the ro validation check (expects rows of lit mining pairs in molfile)
   * The rofile is expected to be the one that gets dumped out by rodump
   *
   * tx_roSet:: List[CString] -> Map[roid, rotx] -> Option[Map[roid, sar]]
   *            -> Map[roid, product]
   *    1st arg is the substrates to expand (list of mols incoming into the rxn)
   *    2nd arg is the map to ro queryrxn smarts indexed by (roid, dir=T|F)
   *    3rd arg is the map to sar substructures indexed by (roid, dir=T|F)
   *    Result is products indexed by the ros that validate the product
   * This function is used for expansions of substrate lists using ro sets
   *
   * tx_roSet:: Map[rowid, List[CString]] -> Map[roid, rotx]  -> Option[Map[roid, sar]]
   *            -> Map[rowid, Map[roid, Products]]
   *    1st arg is a bunch of rows, each by its id and group of "AND substrates"
   *    2nd arg is the map of ro quertrxn smarts indexed by (id, dir=T|F)
   *    3rd arg is the map to sar substructures indexed by (roid, dir=T|F)
   *    Result is rowid -> roid -> products
   *
   * tx_roSet_check:: List[String] -> List[String] -> Map[RODirID, String] 
   *                    -> Option[(RODirId, String)]
   *    1st arg represents multiple substrates of a candidate rxn
   *    2nd arg represents multiple products of a candidate rxn
   *            is encased in Option; and is checked for contain in ro output
   *    3rd arg is the map to ro queryrxn smarts index by (id, dir T=fwd|F=rev)
   *    4th arg: TODO: needs to be sar just like tx_roSet
   *    Result is the boolean of whether there exists ONE RO that validates
   * This function is used for filtering (substrate, product) using ros
   * The code for this uses spark; and therefore has to be run w/ spark-submit
   */

  def exec(args: Array[String]) {
    val cmd = args(0)
    val ros_file = args(1)
    val mol_file = args(2)

    // The number of slices is the size of each unit of work assigned
    // to each worker. The number of workers is defined by the 
    // spark-submit script. Instances below:
    // --master local[1]: one worker thread on localhost
    // --master local[4]: four worker theads on localhost
    // --master spark://host:port where the master EC2 location is from:
    // ./spark-ec2 -k <kpair> -i <kfile> -s <#slaves> launch <cluster-name>
    val slices = if (args.length > 3) args(3).toInt else 2

    val sar_file = if (args.length > 4) Some(args(4)) else None

    val conf = new SparkConf().setAppName("Spark RO Apply")
    val spark = new SparkContext(conf)

    val ros = read_ros(spark, ros_file)
    val sar = read_sar(spark, sar_file)

    def validate_thr_ros(c: CandidateRxnRow) = {
      if (c.s_inchi.equals(c.p_inchi)) 
        None
      else {
        val substrate = List(c.s_inchi)
        val product = Some(List(c.p_inchi))
        val validating_ro = tx_roSet_check(substrate, product, ros)
        validating_ro
      }
    }

    def filterMap[I, O](cs: RDD[I], fn: I => Option[O]) = {
      val c_fnout = cs.zip(cs.map(fn))

      // filter to those whose fn application did not output None
      val has_output = c_fnout.filter(m => m match { 
        case (c, Some(_)) => true
        case (c, None) => false
      })

      // then remove the option on it 
      // there cannot be None's in this lst
      val out = has_output.map(m => m match {
        case (c, Some(a)) => (c, a)
      })

      out
    }

    def printvalid(c_ro: (CandidateRxnRow, (RODirID, String))) {
      val c = c_ro._1
      val roid = c_ro._2._1
      println("VALID: " + c.id + "\t" + c.s_inchi.nm + 
              "\t" + " <-> " + "\t" + c.p_inchi.nm + 
              "\t" + c.orig_srctxt + 
              "\t" + "BY" + "\t" + roid)
    }

    def printidlist(v: (CandidateRxnRow, (RODirID, String))) {
      // v == (Cand, ((Int, Bool), String))
      val cid = v._1.id
      val roid = v._2._1._1
      val rodir = v._2._1._2
      println("Validation Witness:\t" + cid + "\t" + roid + "\t" + rodir)
    }
    
    def joinRDD[K, V1, V2](a: RDD[(K, V1)], b: Map[K, V2]) = {
      val common_a = a.filter( kv => b.contains(kv._1) )
      common_a.map( kv => (kv._1, (kv._2, b(kv._1))) )
    }
    
    def join[K, V1, V2](a: Map[K, V1], b: Map[K, V2]) = {
      // returns a map composed of keys shared between the two inputs
      // and the values a tuple of join of values from the original maps
      val intersect_keys = a.keys.filter(b.contains(_))
      intersect_keys.map(k => (k, (a(k), b(k)))).toMap
    }

    def to_rxn_str(subs_prd: (List[CString], Map[RODirID, Products])) = {
      val substrates = subs_prd._1
      val ro_prd = subs_prd._2
      val indigoi = new IndigoInchi(new Indigo)
      def smile(mol: CString) = {
        val m = indigoi.loadMolecule(mol.i)
        // reaction operators do not keep stereochemistry/cis-trans intact
        // so it is pointless to render with substrates have those annotations
        // remove from both sides
        m.clearStereocenters
        m.clearCisTrans
        m.foldHydrogens
        m.smiles
      }
      def reactset(l: List[CString]) = l.foldLeft(""){ 
                                          case (a,m) => 
                                            val s = smile(m)
                                            if (a=="") s else a + "." + smile(m)
                                       } 
      val ro_real_prd = ro_prd.filter{ case (r, p) => ! p.isEmpty }
      val rxn_map = ro_real_prd.map{ case (roid, p_sets) => {
        // p_sets is guaranteed to be non empty because of the filter above
        val rxnsmiles_list = p_sets.getSome.map( products => reactset(substrates) + ">>" + reactset(products))
        roid -> rxnsmiles_list
      }}

      // this is now Map[roid, rxnsmiles'list]
      rxn_map
    }

    def mols_from_lines(l: String) = {
      val a = l.split('\t')
      val id = a(0).toInt
      val name = id.toString
      val mol_list = a.drop(1).toList
      (id, mol_list.map(new CString(name, _))) 
    }

    def printstdout(mols: String, expanded: String) {
      println("====== Input Substrates ======\n" + mols)
      println("====== Expanded SMILES  ======\n" + expanded)
    }

    cmd match {
      case "expand" => { 
        if (!spark.isLocal) {
          println("Call expand_spark instead of expand")
          System.exit(-1)
        }

        val lines: List[String] = get_lines(spark, mol_file)

        // read the mols as a map: rowid -> List[substrate]
        val mols = lines.map( mols_from_lines ).toMap

        // ros are the reaction ops as a map: roid -> rosmarts
        val products = tx_roSet(mols, ros, sar)

        // product is a map: rowid -> map(roid -> products)
        // join mols with products to get rowid->(List[subs], map(roid -> prd))
        val subs_prd = join(mols, products)

        // convert (subs'list, map(roid -> prd)) TO 
        //              map(rowid -> map(roid -> rxnsmiles'list))
        val outrxns = subs_prd.map{ 
          case (rowid, rxndata) => rowid -> to_rxn_str(rxndata) 
        }

        // convert map(rowid -> map(roid -> rxnsmiles'list)) TO 
        //              (rxnsmiles, rowid, roid)'list
        val rxn_list = for ((rowid, mp) <- outrxns; // take all rows
                        (roid, rxns)  <- mp;    // all roid within row
                        rxn <- rxns)            // all rxns resulting from roid
                      yield (rxn, (rowid, roid))

        val molsStr = mols.mkString("\n")
        val expanded = rxn_list.map{case(r,ids)=>r + "\t" + ids}.mkString("\n")
        printstdout(molsStr, expanded)

      }
      case "expand_spark" => {
        val lines: RDD[String] = spark.textFile(mol_file, slices).cache() 

        // read the mols as a RDD[(rowid, List[substrate])]
        val mols = lines.map( mols_from_lines )

        // ros are the reaction ops as a map: roid -> rosmarts
        // apply to the RDD mols to get RDD[(roid, map(roid -> products)] 
        val products = tx_roSet(mols, ros, sar)
        
        // the number of successful products created is small, so reduce it 
        // to simple Map
        val products_map = products.collect().toMap

        // products is a Map[rowid,  map(roid -> products)]
        // join mols w products get RDD[(rowid, (List[subs], map(roid -> prd)))]
        val subs_prd = joinRDD(mols, products_map)

        // convert (subs'list, map(roid -> prd)) TO 
        //              map(rowid -> map(roid -> rxnsmiles'list))
        val outrxns = subs_prd.map{ 
          case (rowid, rxndata) => rowid -> to_rxn_str(rxndata) 
        }

        // convert map(rowid -> map(roid -> rxnsmiles'list)) TO 
        //              (rxnsmiles, rowid, roid)'list
        val rxn_list = for ((rowid, mp) <- outrxns; // take all rows
                        (roid, rxns)  <- mp;    // all roid within row
                        rxn <- rxns)            // all rxns resulting from roid
                      yield (rxn, (rowid, roid))

        val molsFlat = mols.map(m => List(m)).reduce(_ ++ _)
        val rxn_listFlat = rxn_list.map(r => List(r)).reduce(_ ++ _)

        val molsStr = molsFlat.mkString("\n")
        val expanded = rxn_listFlat.map{case(r,ids)=>r + "\t" + ids}.mkString("\n")
        printstdout(molsStr, expanded)

      }
      case "check" => {
        // cache() persists RDD
        val lines: RDD[String] = spark.textFile(mol_file, slices).cache() 

        // lines have format: id|substrate|product|srcdbid|txt|enzymes'; '*
        // substrates and products are chemicals in inchi format
        // CandidateRxnRow.fromString parses the format to get structured
        val candidates = lines.map(l => CandidateRxnRow.fromString(l))

        // filter those rows that have plausible substrate, product pairs
        val valid = filterMap(candidates, validate_thr_ros)

        // reduce to a single list of valid candidates
        val all_valid = valid.map(List(_)).reduce(_ ++ _)
        
        // Report the IDs of rxn and the ID of the ro,dir of valid
        all_valid.foreach(printidlist(_))
        all_valid.foreach(printvalid(_))
      }
      case "litmine" => {
        val lines: RDD[String] = spark.textFile(mol_file, slices).cache() 
        
        // lines have format: id|enzymes|pmid|sentence|chemical_list
        // chemical_list is formatted as tab-sep strings "name --> inchi"
        val sentences = lines.map(l => LitmineSentence.fromString(l))

        // map to List[(pl_c,pl_c)] of candidates; where a plausible 
        // reactant chemical pl_c is one that is not a cofactor, has some
        // carbons in it, is not abstract with an R group, etc etc..
        // is_plausible_reactant: c -> bool does this check; and 
        // pairs: List(pl_c) -> List((pl_c, pl_c)) removes duplicates
        //        in list and constructs the n^2 pairs
        def s2pairs(l: LitmineSentence) = {
          val plausible = l.chemicals.filter(is_plausible_reactant)
          pairs(plausible) // pairs of inchis
        }
        val candidates = sentences.map(s2pairs)

        // filter to those sentences that have at least one plausible pair
        // and ensure that the sentence metadata is present
        val plausible_sentences = sentences.zip(candidates).filter(! _._2.isEmpty)
        
        // construct CandidateRxnRow(s) from LitmineSentence (_1) by using the
        // metadata frm LitmineSentence but chemical pairs from candidates (_2)
        val plausible_cand = plausible_sentences.map(LitmineSentence.toCandidateRxnRows(_))
        // each map above results in a list of candidates; need to flatten it
        val plausible_cand_set = plausible_cand.flatMap(identity)

        // filter those rows that have plausible substrate, product pairs
        val valid = filterMap(plausible_cand_set, validate_thr_ros)
        
        // reduce to a single list of valid candidates
        val all_valid = valid.map(List(_)).reduce(_ ++ _)

        // Report the IDs of the sentences that were valid
        all_valid.foreach(printidlist(_))
        all_valid.foreach(printvalid(_))
      }
      case _ => println("Usage: roapply <expand|check> " + 
                        "<rofile> <substratesf|molpairf> <option #slices>")
    }

  }
  
  def is_plausible_reactant(chemical: CString) = {

    // formula is index=1 after we split on fwd-slashes
    // e.g., InChI=1S/C6H9N3O2/c7-5(6(10)11)1-4-2-8-3-9-4/h2-3,5H,1,7H2,(H,8,9)(H,10,11)
    val name = chemical.nm
    val inchi = chemical.i
    val spl = inchi.split('/')
    spl.size > 2 && {
      val formula = spl(1) 
      val R = """C([0-9]+)""".r
      val carbons = (R findFirstMatchIn formula) match { 
        case Some(m) => m.group(1).toInt; 
        case None => if (formula contains 'C') 1 else 0;
      }

      // we care about chemicals with C\in[2,\inf) (e.g., CO2 is useless)
      // but this is not perfect coz C(O)=N type molecules exist: InChI=1S/CH3NO/c2-1-3/h1H,(H2,2,3)
      val is_plausible = ! inchi.contains('R') && carbons >= 2 && ! too_common(formula, name, inchi)

      println((if (is_plausible) "T" else "F") + "\t" + formula + "\t" + carbons + "-C" + "\t" + name + "\t" + inchi )
      // for getting stats on chemicals mentioned in litmining
      // if you set this function to always return false
      // and redirect stdout to filter.out; then:
      // $ grep "^T" filter.out | cut -f 1-4 > filter.T
      // $ head -500000 filter.T  | sort | uniq -c | sort -n
      // gets you the top chemicals in the dataset; and you
      // might find some that are too_common (so might add below for exclusion)

      is_plausible
    }
  }
  
  def too_common(formula: String, name: String, inchi: String) = {
    val common_formulae = Map(
      "C10H15N5O10P2" -> "ADP",
      "C10H16N5O13P3" -> "ATP",
      "C10H12N5O6P"   -> "cAMP",
      "C10H14N5O7P"   -> "AMP", // and derivatives
      "C10H13N5O5"    -> "GMP",
      "C10H16N5O14P3" -> "GTP",
      "C10H12N5O7P"   -> "cGMP",
      "C21H27N7O14P2" -> "NAD",
      "C21H29N7O14P2" -> "NADH", // and derivatives
      "C21H30N7O17P3" -> "NADPH", // and derivatives
      "C6H4Cl2N2O2"   -> "cDNA",
      "C28H47N5O18"   -> "glycoprotein",
      "C21H15N5O10S2" -> "phospholipase A2",
      "C32H64NO8P"    -> "PLC",
      "C34H42N4O4.Fe" -> "heme",
      "C33H36N4O6"    -> "bilirubin",
      "C60H73N15O13"  -> "GnRH",
      "C16H16N2O4S2"  -> "RNA"
    )

    common_formulae.contains(formula) // if the common formulae contains this it is too common
  }

  def pairs(set: List[CString]): List[(CString, CString)] = {
    // removes duplicates
    val uniq = set.distinct
    // creates the n^2 pairs from list
    for (a <- uniq; b <- uniq if !a.equals(b)) yield (a,b)
  }

  def read_ros(spark: SparkContext, file: String): Map[RODirID, String] = {
    val arity = 1 // -ve indicates all, else all with 0 < arity <= this_val
    val sz_witnesses = 10 // ros that have at least these many witness rxns
    val ros = read_ros(spark, file, arity, sz_witnesses)

    ros
  }

  def get_lines(spark: SparkContext, file: String) = {
    // Source.fromFile(file).getLines: non-spark
    // below is the spark version; works both for local files
    // and those specified using spark uri (hdfs, file://, etc)
    val lines: RDD[String] = spark.textFile(file).cache() 
    lines.map(l => List(l)).reduce(_ ++ _)
  }

  def read_ros(spark: SparkContext, file: String, arity: Int, gtK_witnesses: Int) = {
    def filterfn(r: RORow) = 
        (arity < 0 || (r.arity <= arity && r.arity > 0)) &&
        r.witness_sz > gtK_witnesses

    val lines = get_lines(spark, file)
    val ros_all_data = lines.map(l => RORow.fromString(l))
    val ros_filtered = ros_all_data.filter(filterfn)
    val ros_map = ros_filtered.map(r => ((r.ero_id, r.dir), r.ero.rxn)).toMap

    ros_map
  }

  def read_sar(spark: SparkContext, file: Option[String]): Option[Map[RODirID, Set[String]]] = {
    file match {
      case None => None
      case Some(fname) => {
        val lines = get_lines(spark, fname)
        val sars = lines.map(SARRow.fromString)
        val sar_map = sars.map(s => ((s.ero_id, s.dir), s.sars)).toMap
        Some(sar_map)
      }
    }
  }

  class Products(ps: Option[List[List[CString]]]) extends Serializable {
    // Outer set represents result of ro applying in different places on mol
    // Inner set represents the result of one loc appl,
    //       but possibly resulting in combination of different mols
    val mols = ps

    def containsMatch(expected: Option[List[CString]]) = {
      def isSub(big: List[CString], sm: List[CString])=sm.forall(big.contains(_))
      expected match {
        case None => mols == None
        case Some(exp) => mols match {
                    case None => false
                    case Some(products) => 
                          products.exists(ms => isSub(ms, exp))
                  }
      }
    }

    def isEmpty = mols == None

    def getSome = mols match { case Some(l) => l }

    override def toString() = mols.toString
  }

  def unimplemented(msg: String) {
    println("Unimplemented: " + msg)
    System.exit(-1);
  }

  def passes_sar(substrate_inchis: List[CString], sar_sub: Set[String]): Boolean = {
    val sar_match_abstraction = {
      if (substrate_inchis.size != 1) 
        unimplemented("SARs only correspond to single substrates. \n" + 
                      "Multiple substrates passed to RO application.")

      // to match against the sar substructure constraints, we use the 
      // approach we use in identifying substructure tags on reachables
      // from com.act.reachables.CreateActTree:getAbstraction
      
      // create a basis set consisting of the set of possible substructures
      val sar_names = sar_sub // dont have special names so use smarts as name
      val sar_basis: Map[String, String] = sar_sub.zip(sar_names).toMap
      // create an abstractor unit based on those basis elements
      val sar_abs = new FnGrpAbstractChemInChI(sar_basis.asJava)
      // create an abstraction of the substrate inchi using the abstractor
      val inchi = substrate_inchis(0).i
      // we have to jump through a few hoops to convert the mutable
      // java Map returned by createAbstraction to an immutable scala map
      // 1. get a scala mutable map
      // 2. convert it to a Seq of tuples (k,v)
      // 3. construct a Map(..) by splat-ing the Seq using _*
      //    since Map takes in varargs, _* converts Seq[X] to X*
      //    see description of the splat operator here:
      //    https://www.safaribooksonline.com/library/view/scala-cookbook/9781449340292/ch05s08.html
      val abs: Map[String, Integer] = Map(sar_abs.createAbstraction(inchi).asScala.toSeq: _*)

      abs
    }

    // return is any of the sar subpatterns appeared >0 in substrate
    val did_match = sar_match_abstraction.exists{case (sub, cnt) => cnt>0}

    println("SAR Check: " + (if (did_match) "passed" else "failed"))
    println("SAR Check: On substrate: " + substrate_inchis)
    println("SAR Check: SAR subtructures: " + sar_sub)
    println()

    did_match
  }

  def tx(substrate_inchis: List[CString], ro: String, sar_sub: Set[String]): Products = {
    // check if the substrate pass one of the SAR substructures associated
    // w/ this ro -- there might be multiple because the ERO abstracts many
    // sequences. If it passes one of the substructure sars then just
    // apply the RO and return the products
    passes_sar(substrate_inchis, sar_sub) match {
      case true  => tx(substrate_inchis, ro)
      case false => new Products(None)
    }
  }
  
  def tx(substrate_inchis: List[CString], ro: String): Products = {
    // convert java List<List<S>> to scala immutable List[List[String]]
    def scalaL(ll:java.util.List[java.util.List[String]]) = 
        List() ++ (for ( l <- ll.asScala ) yield List() ++ l.asScala)
  
    // products come out with no names, so we assign empty name to them
    def toCString(l: List[String]): List[CString] = l.map(i => new CString("", i))

    def printlnerr(s: String) { System.err.println(s) }

    try {

      val txfn = RxnTx.expandChemical2AllProductsNormalMol _
      val ps = txfn(substrate_inchis.map(_.i).asJava, ro)
      new Products(if (ps == null) None else Some(scalaL(ps).map(toCString)))

    } catch {
      case ioe: IndigoException => {
        printlnerr("FAIL(IndigoException) tx on: " + substrate_inchis + " ro_apply: " + ro)
        new Products(None)
      }
      case npe: NullPointerException => {
        printlnerr("FAIL(NPE) tx on: " + substrate_inchis + " ro_apply: " + ro)
        new Products(None)
      }
      case e: Exception => {
        printlnerr("FAIL(EXCEPTION) tx on: " + substrate_inchis + " ro_apply: " + ro)
        new Products(None)
      }
    }
  }

  type RODirID = (Int, Boolean) // true indicates fwd

  def tx_roSet(s: List[CString], ros: Map[RODirID, String], sar: Option[Map[RODirID, Set[String]]]): Map[RODirID, Products] = {
    val prds = sar match {
      case None       => ros.map(kv => (kv._1, tx(s, kv._2)))
      case Some(sars) => ros.map(kv => (kv._1, tx(s, kv._2, sars(kv._1))))
    }
    val real_prds = prds.filter(id_p => ! id_p._2.isEmpty)
    real_prds
  }

  def tx_roSet(ss: Map[Int, List[CString]], ros: Map[RODirID, String], sar: Option[Map[RODirID, Set[String]]]): Map[Int, Map[RODirID, Products]] = {
    val prds = ss.map(kv => (kv._1, tx_roSet(kv._2, ros, sar)))
    val prds_didapply = prds.filter(id_p => ! id_p._2.isEmpty)
    prds_didapply
  }

  def tx_roSet(ss: RDD[(Int, List[CString])], ros: Map[RODirID, String], sar: Option[Map[RODirID, Set[String]]]): RDD[(Int, Map[RODirID, Products])] = {
    val prds = ss.map(kv => (kv._1, tx_roSet(kv._2, ros, sar)))
    val prds_didapply = prds.filter(id_p => ! id_p._2.isEmpty)
    prds_didapply
  }

  def tx_check(s_inchis: List[CString], p_inchis: Option[List[CString]], ro: String) = {
    val products = tx(s_inchis, ro)
    products.containsMatch(p_inchis)
  }

  def tx_roSet_check(s: List[CString], p: Option[List[CString]], ros: Map[RODirID, String]) = ros.find(kv => tx_check(s, p, kv._2)) // returns the first match

  def test() {
    // TODO: move this to scala testing framework (i.e., under src/test/scala/)
    // See: http://www.scala-sbt.org/0.12.4/docs/Detailed-Topics/Testing.html
    val dehydrogenase_O_OH = "[H,*:1]C([H,*:2])([H,*:3])C([Ac])(O[Ac])C" +
          "([H,*:4])([H,*:5])[H,*:6]>>[H,*:1]C([H,*:2])([H,*:3])" +
          "C([H])(O[H])C([H,*:4])([H,*:5])[H,*:6]"
    val A = ( List(new CString("acetone", "InChI=1S/C3H6O/c1-3(2)4/h1-2H3")) , 
      Some(List(new CString("outA", "InChI=1S/C3H8O/c1-3(2)4/h3-4H,1-2H3"))) )
    val B = (List(new CString("aromatic","InChI=1S/C5H4O2/c6-5-1-3-7-4-2-5/h1-4H")),
      Some(List(new CString("outB", "InChI=1S/C5H6O2/c6-5-1-3-7-4-2-5/h1-6H"))) )
    val C = ( List(new CString("dbl_aromatic", "InChI=1S/C9H6O2/c10-8-5-6-11-9-4-2-1-3-7(8)9/h1-6H")), 
      Some(List(new CString("outC", "InChI=1S/C9H12O2/c10-8-5-6-11-9-4-2-1-3-7(8)9/h1-4,7-10H,5-6H2"))) )
    val D = ( List(new CString("id4460", "InChI=1S/C16H12O6/c1-21-12-5-4-9(15(19)16(12)20)11-7-22-13-6-8(17)2-3-10(13)14(11)18/h2-7,17,19-20H,1H3")) ,
      Some(List(new CString("outD", "InChI=1S/C16H18O6/c1-21-12-5-4-9(15(19)16(12)20)11-7-22-13-6-8(17)2-3-10(13)14(11)18/h2-6,10-11,13-14,17-20H,7H2,1H3"))))
    val E = ( List(new CString("id7298", "InChI=1S/C22H18O11/c23-10-5-12(24)11-7-18(33-22(31)9-3-15(27)20(30)16(28)4-9)21(32-17(11)6-10)8-1-13(25)19(29)14(26)2-8/h1-6,18,21,23-30H,7H2/t18-,21-/m1/s1")) , None )

    val cases = List(A, B, C, D, E)
    val areOk = cases.map(x => tx_check(x._1, x._2, dehydrogenase_O_OH))
    // val outs = cases.map(_._1).map(tx(_, dehydrogenase_O_OH))
    // println("Results : " + outs)
    println("Results : " + areOk)
    if (areOk.reduce(_ && _)) 
      println("TEST SUCCESS: Products match expected.")
    else
      println("TEST FAILED: Some products did not match.")
  }
}

object infer {
  /*
   * "roinfer rotype rxndumpfile numslices" runs using "sbt assembly; spark-submit..." not "sbt run"
   */

  def exec(args: Array[String]) {
    val rxns_file = args(0)

    // The number of slices is the size of each unit of work assigned
    // to each worker. The number of workers is defined by the 
    // spark-submit script. Instances below:
    // --master local[1]: one worker thread on localhost
    // --master local[4]: four worker theads on localhost
    // --master spark://host:port where the master EC2 location is from:
    // ./spark-ec2 -k <kpair> -i <kfile> -s <#slaves> launch <cluster-name>
    val slices = if (args.length > 1) args(1).toInt else 2

    val conf = new SparkConf().setAppName("Spark RO Infer")
    val spark = new SparkContext(conf)

    val lines: RDD[String] = spark.textFile(rxns_file, slices).cache() 

    // read the rxns as a RDD[Option[RString]]
    val raw_rxns    = lines.map( rxns_from_lines )

    // get statistics over reactions in the entire dataset
    val rxn_stats   = get_rxn_stats( raw_rxns )

    // mass conservation: reactions should be atom balanced
    val bal_rxns    = raw_rxns.map( balance_rxn )

    // compute "atom-delta" ROs
    val atom_deltas = bal_rxns.map( compute_atom_delta )

    unimplemented("spark infer bro, cro, ero")

    // compute "bond-delta" RO

    // compute "reaction-center" RO

    // compute "electronic" RO

  }

  def balance_rxn(rxn: Option[RString]) = {
    apply_on_some(rxn, balance_rxnz)
  }

  def balance_rxnz(rxn: RString) = {
    // finds missing reactants
    // computes the stoichiometric coefficients
    Some(rxn)
  }

  def compute_atom_delta(rxn: Option[RString]) = {
    apply_on_some(rxn, compute_atom_deltaz)
  }

  def compute_atom_deltaz(rxn: RString) = {
    Some(rxn)
  }

  def apply_on_some[X,Y](rxn: Option[X], fn: X=>Option[Y]) = rxn match {
    case None => None
    case Some(x) => fn(x)
  }

  class RxnStat(val chem_freq: RDD[(CString, Int)]) { }

  def get_rxn_stats(rxns: RDD[Option[RString]]) = {
    // compute relevant metrics over the entire dataset
    // e.g., statistics on the most commonly occuring chemicals
  
    def rxn_to_chem_count(rxn: RString) = {
      rxn.s_inchis.map(s => (s, 1)) ++ rxn.p_inchis.map(p => (p, 1))
    }

    val all_rxns = keep_some( rxns )
    
    // map reduce by 
    // 1. mapping each rxn to (chem, 1) counts
    // 2. then reduce by key on chem identity
    // 3. then swapping to get the counts as the key
    // 4. then sorting by key (counts)
    // 5. then swapping back to get the chem as key
    // note that 3. does not require unique counts as
    // operations are done over list(tuple) and not maps
    val chem_counts = all_rxns
                      .flatMap( rxn_to_chem_count ) // 1.
                      .reduceByKey(_ + _)           // 2.
                      .map(_.swap)                  // 3.
                      .sortByKey()                  // 4. 
                      .map(_.swap)                  // 5.

    chem_counts.foreach{ case (che, cnt) => {
      val nm = if (che.nm != null) che.nm else che.i
      println(nm + " -> " + cnt)
    }}

    val stats = new RxnStat(chem_counts)

    stats
  }

  def keep_some[A:ClassTag](data: RDD[Option[A]]): RDD[A] = {
    data
      .filter( _ != None )
      .map{ 
        case Some(a) => a 
        case None    => new Object().asInstanceOf[A] // unreachable
      }
  }

  def unimplemented(msg: String) {
    println("Unimplemented: " + msg)
    System.exit(-1);
  }
  
  def rxns_from_lines(rxn_line: String) = {
    // look at inout.rxndump.tabular for the format of each line
    // id<tab>#<tab>
    // 2-tab-separated substrates<tab>#<tab>
    // 2-tab-separated products
    val a = rxn_line.split("[\t][#][\t]") // takes regex so a 3-letter regex
    def split_cstring(x: String) = { 
      val nm_i = x.split("[\t][=][\t]")
      val name = if (nm_i(0).equals("null")) null else nm_i(0)
      new CString(name, nm_i(1))
    }
    a.length match { 
      case 3 =>  {
        if (a(1).equals("") || a(2).equals("")) 
          None
        else {
          val id = a(0).toInt
          val substrates = a(1).split("[\t][\t]").toList.map(split_cstring)
          val products = a(2).split("[\t][\t]").toList.map(split_cstring)
          Some(new RString(id, substrates, products))
        }
      }
      case _ => None
    }
  }

}

object inout {
  // hardcode the port and host, as only under strange circumstances
  // would we go to a non-local machine for the main data
  var port="27017"
  var host="localhost"
  var dbs="actv01"

  def main(args: Array[String]) {
    if (args.length < 2) {
      println("Usage: sbt \"run roapply expand rodumpfile substratefile sardumpfile\"")
      println("Usage: sbt \"run roapply expand_spark rodumpfile substratefile sardumpfile\"")
      println("Usage: sbt \"run roapply check rodumpfile molpairfile\"")
      println("Usage: sbt \"run roapply litmine rodumpfile litcandidates\"")
      println("Usage: sbt \"run roinfer rxndumpfile\"")
      println("Usage: sbt \"run rodump rodumpfile\"")
      println("Usage: sbt \"run sardump sardumpfile\"")
      println("Usage: sbt \"run rxndump rxndumpfile\"")
      System.exit(-1)
    }

    val cmd = args(0)
    val cargs = args.drop(1)

    if (cmd == "roapply") 
      apply.exec(cargs)
    else if (cmd == "roinfer")
      infer.exec(cargs)
    else if (cmd == "rodump")
      rodump(cargs(0))
    else if (cmd == "sardump")
      sardump(cargs(0))
    else if (cmd == "rxndump")
      rxndump(cargs(0))
  }

  def rodump(outfile: String) {
    val db = new MongoDB(host, port.toInt, dbs)
    val numOps = -1 // no max# of ops
    val ops_whitelist = null // no whitelist

    // count arity by taking whatever is left of ">" and then counting
    // the number of "." in that substring. char.bool_binop is just
    // shorthand for a partial fn (after partial app of the boolean compare)
    def arityIs(o: RO) = o.rxn.takeWhile('>'.!=).count('.'.==) + 1
    def malformed(o: RO) = o.rxn.trim.startsWith(">>") || o.rxn.trim.endsWith(">>")
    def collapse_same_eroid(l: List[RORow]): List[RORow] = {
      // the following assumes that the rows have the same (dir, ero_id)
      // this was ensured by doing the groupBy on these two fields
      // This means the cro_id, arity, ero will be the same
      // outputs single row by merging witness_sz, witnesses, 
      def combine_rows(rs: List[RORow]): RORow = {
        def rdc(a: RORow, b: RORow) = new RORow(a.dir, a.arity, 
                        a.witness_sz + b.witness_sz, a.ero_id, a.cro_id, a.ero,
                        a.witnesses ++ b.witnesses)
        return rs.reduce(rdc)
      }
      val by_eroid = l groupBy (x => (x.dir, x.ero_id))
      val uniq_rows = by_eroid.map( kv => combine_rows(kv._2) )
      return uniq_rows.toList
    }

    var eros_ = List[RORow]()
    for (rodata <- db.getOperators(numOps, ops_whitelist).asScala) {
      val operator_id = rodata.fst()
      val witness = rodata.snd().asScala
      val theoryRO = rodata.third()
      val bro = theoryRO BRO
      val ero = theoryRO ERO
      val cro = theoryRO CRO
			val arity = arityIs(ero)
      val fwd_dir = true
      eros_ :+= new RORow(fwd_dir, arity, witness size, ero ID, cro ID, ero, witness)
    }
    // we can check for malformed cro or ero by checking 
    // if rxn has no products|substrates (rxn.trim starts or ends with >>)
    val (eros_bad, eros_good) = eros_.partition(x => malformed(x.ero))

    // sometimes the same cro/ero will be split across multiple rows
    // because same transformation happens under different cofactors
    // collapse those:
    val eros = collapse_same_eroid(eros_good)

    val eros_bothdir = eros ++ eros.map(e => {
        val rev = e.ero.asInstanceOf[ERO].reverse
        val ar = arityIs(rev)
        val rev_dir = false
        new RORow(rev_dir, ar, e.witness_sz, e.ero_id, e.cro_id, rev, e.witnesses)
    })

    val eroSort = eros_bothdir.sortBy(x => (x.arity, x.witness_sz, x.ero_id))
    write(outfile, eroSort.map(_.toString))

    println("# output to: " + outfile)
    println("# bad eros: " + (eros_bad size))
    println("# good eros: " + (eros size))

    if (true) {
      println("rendering the top(50) arity(1) EROs")
      val topX = eroSort.filter(_.arity == 1).takeRight(50)
      topX foreach (x => x.render(db))
    }

  }

  def sardump(outfile: String) {
    val db = new MongoDB(host, port.toInt, dbs)
    def get_eroid(rxn: Long) = {
      val ero = db.getEROForRxn(rxn.asInstanceOf[Int])
      ero match {
        case null => None
        case _    => Some(ero.ID)
      }
    }
    var sars = List[SARRow]()
    var multi_ero = List[Seq]()
    for (seq <- db.getSeqWithSARConstraints().asScala) {
      val rxns = seq.getReactionsCatalyzed()
      val eros_ = rxns.asScala.map(get_eroid(_)).filter(_ != None)
      val eros = eros_.filter{ 
                    // remove Nones (when no ero for rxn)
                    case None => false 
                    // remove the { >> } ero with ID == 0
                    case Some(x) => x != 0 
                  }.map {
                    case None => 0 // unreachable
                    case Some(id) => id
                  } 
      eros.size match {
        case 0 => {
          // no ero for seq, skip
        }
        case 1 => {
          def isSubstructureCnstr(cnstr: (Object, SARConstraint)) = {
            val sarcnstr = cnstr._2
            // check if this is a substructure presence constraint
            val is_sub = sarcnstr.contents == SAR.ConstraintContent.substructure
            // check that the present requirement is "should_have"
            val is_present = sarcnstr.presence == SAR.ConstraintPresent.should_have
            // return: is_substructure that should be present
            is_sub && is_present
          }

          val eroid = eros.toList(0)
          val seqid = seq.getUUID
          val isfwd = true
          val sar = seq.getSAR
          val constraints = sar.getConstraints.asScala.filter(isSubstructureCnstr)
          val substructures = constraints.map{ case (data, _) => data.asInstanceOf[String] }.toSet
          sars :+= new SARRow(isfwd, eroid, seqid, substructures)
        }
        case _ => {
          // TODO: fix SAR calculation. SAR over substrates should
          // only be calculated "per ero" as opposed to all rxns of 
          // the enzyme sequence. If the ero's are different, then
          // the enzyme probably catalyzes complete different rxns
          // and so aggregating across these eros does not make sense.
          println("seq catalyzes rxns w/ multiple eros. " + 
                  "sar computation assumes that all eros are the same.")
          println("seq : " + seq.getUUID)
          println("eros: " + eros)
          println("rxns: " + rxns)
          multi_ero :+= seq
        }
      }
    }

    println("multiero seqs: " + multi_ero.map(_.getUUID));
    println("# seqs w/ multieros: " + multi_ero.size)
    println("# seqs w/ eros assigned sars: " + sars.size)

    write(outfile, sars.map(_.toString))
  }

  def rxndump(outfile: String) {
    val db = new MongoDB(host, port.toInt, dbs)
    val rids = db.getAllReactionUUIDs().asScala // List[Long]
    val rxns = rids.map(db.getReactionFromUUID) // List[Reaction]

    var cidsS = Set[java.lang.Long]()
    for (r <- rxns) 
      cidsS ++= (r.getSubstrates.toSet ++ r.getProducts.toSet)
    // for (r <- rxns) {
    //   println("[I] Reaction: " + r.getUUID)
    //   println("[S] Reaction: " + r.getSubstrates.toList)
    //   println("[P] Reaction: " + r.getProducts.toList)
    // }

    val cids = cidsS.toList
    val chems = cids.map(db.getChemicalFromChemicalUUID)
                    // remove the fake InChIs
                    .filter(c => !c.getInChI.startsWith("none") &&
                                 !c.getInChI.startsWith("InChI=/FAKE/METACYC")) 

    // for (c <- chems) println("Chemical: " + c.getInChI)

    var chemMap = Map[java.lang.Long, Chemical]()
    for (c <- chems) chemMap = chemMap + (c.getUuid -> c)

    // from rxns ignore any reaction whose substrates or products
    // contains a chemical id that is not a key in chemMap
    def resolves(ms: List[java.lang.Long]): Boolean = 
        ms.forall(m => chemMap contains m)
    val goodrxns = for (r <- rxns 
                        if resolves(r.getSubstrates.toList)
                        && resolves(r.getProducts.toList)) 
                   yield r

    def hasRgrp(ms: List[java.lang.Long]): Boolean = 
        ms.exists(m => chemMap(m).getInChI contains "R")
    val hasRorNot = goodrxns.partition(r => 
            hasRgrp(r.getSubstrates.toList) || hasRgrp(r.getProducts.toList))
    val Rgrprxns = hasRorNot._1
    val plainrxns = hasRorNot._2

    def inchize(id: java.lang.Long): CString = {
      val chem = chemMap(id)
      new CString(chem.getShortestBRENDAName, chem.getInChI)
    }

    val rxnsX = plainrxns.map(r => 
            (r.getUUID, 
            r.getSubstrates.toList.map(inchize), 
            r.getProducts.toList.map(inchize)))

    def tabular(r: (Int, List[CString], List[CString])) = {
      val id = r._1
      val s_inchis = r._2
      val p_inchis = r._3
      def c2str(cs: CString) = cs.nm + "\t=\t" + cs.i
      def tab_sep(lst: List[CString]) = {
        lst match { 
          case Nil => ""
          case _   => lst.map(c2str).reduce(_ + "\t\t" + _)
        }
      }
      id + "\t#\t" + tab_sep(s_inchis) + "\t#\t" + tab_sep(p_inchis)
    }

    write(outfile, rxnsX.toList.map(tabular))
    // for (r <- rxnsX) 
    //  println(r)

    println("#rxns: " + rxns.size)
    println("#good: " + goodrxns.size)
    println("#plain: " + plainrxns.size)
    println("#r_grp: " + Rgrprxns.size)
    println("#cids: " + cids.size)
    println("#inchis: " + chems.size)
  }

  def write(fname: String, data: List[String]) {
    val file = new FileWriter(fname)
    data.foreach(l => file write (l + "\n"))
    file close
  }
}

object LitmineSentence {
  def toCandidateRxnRows(lc: (LitmineSentence, List[(CString, CString)])) = {
    val pubmed = lc._1
    val allpairs = lc._2
    def toRxnRow(sp: (CString, CString)) = {
        val substrate = sp._1
        val product = sp._2
        new CandidateRxnRow(pubmed.id, substrate, product, 
            pubmed.pmid, pubmed.sentence, pubmed.enzymes)
    }
    allpairs.map(toRxnRow)
  }

  def chem_pairs(delimStr: String) = {
    val delim = " --> "
    val sep = delimStr.indexOf(delim)
    val sep_end = sep + delim.size
    val name = delimStr.take(sep)
    val inchi = delimStr.drop(sep_end)
    new CString(name, inchi)
  }

  def fromString(s: String) = {
    // format: id|enzymes|pmid|sentence|chemical_list
    // enzymes are formatted as "; " separated 
    // chemical_list is formatted as tab-sep strings of "name --> inchi"
    // see pubmed_candidates_serialize.py for where this formatting is injected
    val ss = s.split('\t')
    val enz = ss(1).split(';').map(_.trim).toList

    // if we drop four then we are left 
    // with tsv of chems at the end
    val chems = ss.drop(4).toList.map(chem_pairs)

    new LitmineSentence(ss(0), enz, ss(2), ss(3), chems)
  }
}

class LitmineSentence(i: String, enz: List[String], pid: String, sentc: String, chems: List[CString]) {
  val id = i
  val chemicals = chems // is map of names to inchi
  val pmid = pid
  val sentence = sentc
  val enzymes = enz
}

object CandidateRxnRow {
  def fromString(s: String) = {
    // format: id|substrate|product|srcdbid|txt|enzymes'; '..
    val ss = s.split('\t')
    val enz = ss(5).split(';').map(_.trim).toList
    val id = ss(0)
    val substrate = new CString(id+"_s", ss(1))
    val product = new CString(id+"_p", ss(2))
    new CandidateRxnRow(id, substrate, product, ss(3), ss(4), enz)
  }
}

class CandidateRxnRow(i: String, s: CString, p: CString, orig_id: String, orig_txt: String, enz: List[String]) extends Serializable {
  // format: id|substrate|product|srcdbid|txt|enzymes'; '*
  val id = i
  val s_inchi = s
  val p_inchi = p
  val orig_srcid = orig_id
  val orig_srctxt = orig_txt
  val enzymes = enz
}

object RORow {
  def fromString(s: String) = { 
    val ss = s.split('\t')
    val ro = new RO(new RxnWithWildCards(ss(5)))
    val dir = ss(0).toInt == +1 // +1 => true and -1 => false
    new RORow(dir, ss(1).toInt, ss(2).toInt, ss(3).toInt, ss(4).toInt, ro, ss(6).split(' ').map(_.toInt))
  }
}

class RORow(d: Boolean, ar: Int, w_sz: Int, e_id: Int, c_id: Int, e: RO, rxns: scala.collection.Seq[Any])  {
  val dir = d
  val arity = ar
  val witness_sz =  w_sz
  val ero_id = e_id
  val cro_id = c_id
  val ero = e
  val witnesses = rxns

  override def toString() = (if (dir) +1 else -1) + "\t" + arity + "\t" + witness_sz + "\t" + ero_id + "\t" + cro_id + "\t" + ero.rxn + "\t" + witnesses.reduce(_ + " " + _)

  def render(db: MongoDB) = ero.render("sz:" + witness_sz + "id:" + ero_id + ".png", "E.g.:" + db.getReactionFromUUID(witnesses(0).asInstanceOf[Int].toLong).getReactionName)
}

object SARRow {
  def fromString(s: String) = { 
    val ss = s.split('\t')
    val dir = ss(0).toInt == +1 // +1 => true and -1 => false
    val seq_id = ss(1).toInt
    val ero_id = ss(2).toInt
    val sars = ss(3).split(' ').toSet
    new SARRow(dir, ero_id, seq_id, sars)
  }
}

class SARRow(d: Boolean, e_id: Int, s_id: Int, s: Set[String]) {
  val dir = d
  val ero_id = e_id
  val sars = s
  val seq_id = s_id

  override def toString() = (if (dir) +1 else -1) + "\t" + seq_id + "\t" + ero_id + "\t" + sars.reduce(_ + " " + _)
}
