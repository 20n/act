package com.act.ro

import scala.io.Source
import java.io.FileWriter
import act.server.SQLInterface.MongoDB
import act.shared.Chemical
import act.server.Molecules.RO
import act.server.Molecules.ERO
import act.server.Molecules.RxnWithWildCards
import act.server.Molecules.RxnTx
import collection.JavaConverters._

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD

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
   * tx_roSet:: List[String] -> Map[roid, rotx] -> Map[roid, product]
   *    1st arg is the list of molecules to expand
   *    2nd arg is the map to ro queryrxn smarts indexed by (id, dir=T|F)
   *    Result is products indexed by the ros that validate the product
   * This function is used for expansions of substrate lists using ro sets
   * The code for this does not use Spark (yet)
   *
   * tx_roSet_check:: List[String]->List[String]->Map[RODirID, String] -> bool
   *    1st arg represents multiple substrates of a candidate rxn
   *    2nd arg represents multiple products of a candidate rxn
   *            is encased in Option; and is checked for contain in ro output
   *    3rd arg is the map to ro queryrxn smarts index by (id, dir T=fwd|F=rev)
   *    Result is the boolean of whether there exists ONE RO that validates
   * This function is used for filtering (substrate, product) using ros
   * The code for this uses spark; and therefore has to be run w/ spark-submit
   */

  def exec(args: Array[String]) {
    // test
    val cmd = args(0)
    val ros_file = args(1)
    val mol_file = args(2)

    val conf = new SparkConf().setAppName("Spark RO Apply")
    val spark = new SparkContext(conf)

    val ros = read_ros(spark, ros_file)
    // println("ROs: " + ros.foldLeft("")(_ + "\n" + _))

    // The number of slices is the size of each unit of work assigned
    // to each worker. The number of workers is defined by the 
    // spark-submit script. Instances below:
    // --master local[1]: one worker thread on localhost
    // --master local[4]: four worker theads on localhost
    // --master spark://host:port where the master EC2 location is from:
    // ./spark-ec2 -k <kpair> -i <kfile> -s <#slaves> launch <cluster-name>
    val slices = if (args.length > 3) args(3).toInt else 2

    cmd match {
      case "expand" => { 
        val mols = read_mols(spark, mol_file)
        val products = tx_roSet(mols, ros) // non-spark expansion
        println("Substrates: " + mols)
        println("Products: " + products)
      }
      case "check" => {
        def is_valid_rxn(c: CandidateRxnRow) = {
          val substrate = List(c.s_inchi)
          val product = Some(List(c.p_inchi))
          tx_roSet_check(substrate, product, ros)
        }

        // cache() persists RDD
        val lines: RDD[String] = spark.textFile(mol_file, slices).cache() 

        // lines have format: id|substrate|product|srcdbid|txt|enzymes'; '*
        // substrates and products are chemicals in inchi format
        // CandidateRxnRow.fromString parses the format to get structured
        val candidates = lines.map(l => CandidateRxnRow.fromString(l))
        val valid = candidates.filter(is_valid_rxn)
        println(valid.map(v => v.toString))
        val valid_ids = valid.map(c => List(c.id))
        val collected = valid_ids.reduce(_ ++ _)
        println("Valid rxns: " + collected)
      }
      case _ => println("Usage: roapply <expand|check> " + 
                        "<rofile> <substratesf|molpairf> <option #slices>")
    }

  }

  def read_ros(spark: SparkContext, file: String): Map[RODirID, String] = {
    val arity = 1 // -ve indicates all, else all with 0 < arity <= this_val
    val sz_witnesses = 10 // ros that have at least these many witness rxns
    val ros = read_ros(spark, file, arity, sz_witnesses)

    ros
  }

  def get_lines(spark: SparkContext, file: String) = {
    // Source.fromFile(file).getLines: non-spark
    // below is the spark version
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

  def read_mols(spark: SparkContext, file: String) = {
    val lines = get_lines(spark, file)
    val m_tuples = lines.map(l => { 
        val a = l.split('\t') 
        val id = a(0).toInt
        val list_mols = a.drop(1).toList
        (id, list_mols) 
    })
    m_tuples.toMap
  }

  def read_molpairs(spark: SparkContext, file: String) = {
    val lines = get_lines(spark, file)
    val m_tuples = lines.map( l => {
        val a = l.split('\t')
        val id = a(0).toInt
        val list_mols = a.drop(1).toList
        (id, list_mols) 
    })
    m_tuples.toMap
  }

  class Products(ps: Option[List[List[String]]]) {
    // Outer set represents result of ro applying in different places on mol
    // Inner set represents the result of one loc appl,
    //       but possibly resulting in combination of different mols
    val mols = ps

    def containsMatch(expected: Option[List[String]]) = {
      def isSub(big: List[String], sm: List[String])=sm.forall(big.contains(_))
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

    override def toString() = mols.toString
  }
  
  def tx(substrate_inchis: List[String], ro: String) = {
    // convert java List<List<S>> to scala immutable List[List[String]]
    def scalaL(ll:java.util.List[java.util.List[String]]) = 
        List() ++ (for ( l <- ll.asScala ) yield List() ++ l.asScala)

    val txfn = RxnTx.expandChemical2AllProductsNormalMol _
    val ps = txfn(substrate_inchis.asJava, ro)

    new Products(if (ps == null) None else Some(scalaL(ps)))
  }

  type RODirID = (Int, Boolean) // true indicates fwd

  def tx_roSet(s: List[String], ros: Map[RODirID, String]): Map[RODirID, Products] = {
    val prds = ros.map(kv => (kv._1, tx(s, kv._2)))
    val real_prds = prds.filter(id_p => ! id_p._2.isEmpty)
    real_prds
  }

  def tx_roSet(ss: Map[Int, List[String]], ros: Map[RODirID, String]): Map[Int, Map[RODirID, Products]] = {
    val prds = ss.map(kv => (kv._1, tx_roSet(kv._2, ros)))
    val prds_didapply = prds.filter(id_p => ! id_p._2.isEmpty)
    prds_didapply
  }

  def tx_check(s_inchis: List[String], p_inchis: Option[List[String]], ro: String) = {
    val products = tx(s_inchis, ro)
    products.containsMatch(p_inchis)
  }

  def tx_roSet_check(s: List[String], p: Option[List[String]], ros: Map[RODirID, String]) = ros.exists(kv => tx_check(s, p, kv._2))

  def test() {
    // TODO: move this to scala testing framework (i.e., under src/test/scala/)
    // See: http://www.scala-sbt.org/0.12.4/docs/Detailed-Topics/Testing.html
    val dehydrogenase_O_OH = "[H,*:1]C([H,*:2])([H,*:3])C([Ac])(O[Ac])C" +
          "([H,*:4])([H,*:5])[H,*:6]>>[H,*:1]C([H,*:2])([H,*:3])" +
          "C([H])(O[H])C([H,*:4])([H,*:5])[H,*:6]"
    val aceton = ( List("InChI=1S/C3H6O/c1-3(2)4/h1-2H3") , 
              Some(List("InChI=1S/C3H8O/c1-3(2)4/h3-4H,1-2H3")))
    val aromat = ( List("InChI=1S/C5H4O2/c6-5-1-3-7-4-2-5/h1-4H") , 
              Some(List("InChI=1S/C5H6O2/c6-5-1-3-7-4-2-5/h1-6H")) )
    val dbl_ar = ( List("InChI=1S/C9H6O2/c10-8-5-6-11-9-4-2-1-3-7(8)9/h1-6H"), 
              Some(List("InChI=1S/C9H12O2/c10-8-5-6-11-9-4-2-1-3-7(8)9/h1-4," + 
                        "7-10H,5-6H2")) )
    val id4460 = ( List("InChI=1S/C16H12O6/c1-21-12-5-4-9(15(19)16(12)20)11-7-22-13-6-8(17)2-3-10(13)14(11)18/h2-7,17,19-20H,1H3") ,
              Some(List("InChI=1S/C16H18O6/c1-21-12-5-4-9(15(19)16(12)20)11-7-22-13-6-8(17)2-3-10(13)14(11)18/h2-6,10-11,13-14,17-20H,7H2,1H3")))
    val id7298 = ( List("InChI=1S/C22H18O11/c23-10-5-12(24)11-7-18(33-22(31)9-3-15(27)20(30)16(28)4-9)21(32-17(11)6-10)8-1-13(25)19(29)14(26)2-8/h1-6,18,21,23-30H,7H2/t18-,21-/m1/s1") , None )

    val cases = List(aceton, aromat, dbl_ar, id4460, id7298)
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
  def exec(rxn_file: String) {
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
      println("Usage: sbt \"run roapply expand rodumpfile substratefile\"")
      println("Usage: sbt \"run roapply check rodumpfile molpairfile\"")
      println("Usage: sbt \"run roinfer rxndumpfile\"")
      println("Usage: sbt \"run rodump rodumpfile\"")
      println("Usage: sbt \"run rxndump rxndumpfile\"")
      System.exit(-1)
    }

    val cmd = args(0)
    val cargs = args.drop(1)

    if (cmd == "roapply") 
      apply.exec(cargs)
    else if (cmd == "roinfer")
      infer.exec(cargs(0))
    else if (cmd == "rodump")
      rodump(cargs(0))
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
    // println(eroSort.foldLeft("")((a,b) => a + "\n" + b ))
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

    def inchize(id: java.lang.Long): String = chemMap(id).getInChI
    val rxnsX = plainrxns.map(r => 
            (r.getUUID, 
            r.getSubstrates.toList.map(inchize), 
            r.getProducts.toList.map(inchize)))

    for (r <- rxnsX) 
      println(r)

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

object CandidateRxnRow {
  def fromString(s: String) = {
    // format: id|substrate|product|srcdbid|txt|enzymes'; '..
    val ss = s.split('\t')
    val enz = ss(5).split(';').map(_.trim).toList
    new CandidateRxnRow(ss(0).toInt, ss(1), ss(2), ss(3), ss(4), enz)
  }
}

class CandidateRxnRow(i: Int, s: String, p: String, orig_id: String, orig_txt: String, enz: List[String]) {
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

class RORow(d: Boolean, ar: Int, w_sz: Int, e_id: Int, c_id: Int, e: RO, rxns: Seq[Any])  {
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
