package com.act.ro

import scala.io.Source
import java.io.FileWriter
import act.server.SQLInterface.MongoDB
import act.shared.Chemical
import act.server.Molecules.RO
import act.server.Molecules.RxnWithWildCards
import act.server.Molecules.RxnTx
import collection.JavaConverters._

object apply {

  def exec(args: Array[String]) {
    test

    val ros_file = args(0)
    val mol_file = args(1)

    val ros = read_ros(ros_file)
    val mols = read_mols(mol_file)

    val products = tx_roSet(mols, ros)
    // println("ROs: " + ros.foldLeft("")(_ + "\n" + _))
    println("Substrates: " + mols)
    println("Products: " + products)
  }

  def read_ros(file: String): Map[Int, String] = {
    val arity = 1 // -ve indicates all, else all with 0 < arity <= this_val
    val sz_witnesses = 10 // ros that have at least these many witness rxns
    val ros = read_ros(file, arity, sz_witnesses)

    ros
  }

  def read_ros(file: String, arity: Int, gtK_witnesses: Int) = {
    def filterfn(r: RORow) = 
        (arity < 0 || (r.arity <= arity && r.arity > 0)) &&
        r.witness_sz > gtK_witnesses

    val lines = Source.fromFile(file).getLines
    val ros_all_data = lines.map(l => RORow.fromString(l))
    val ros_filtered = ros_all_data.filter(filterfn)
    val ros_map = ros_filtered.map(r => (r.ero_id, r.ero.rxn)).toMap

    ros_map
  }

  def read_mols(file: String) = {
    val lines = Source.fromFile(file).getLines
    val m_tuples = lines.map(l => { 
        val a = l.split('\t'); 
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

  def tx_roSet(s: List[String], ros: Map[Int, String]): Map[Int, Products] = {
    val prds = ros.map(kv => (kv._1, tx(s, kv._2)))
    val real_prds = prds.filter(id_p => ! id_p._2.isEmpty)
    real_prds
  }

  def tx_roSet(ss: Map[Int, List[String]], ros: Map[Int, String]): Map[Int, Map[Int, Products]] = {
    val prds = ss.map(kv => (kv._1, tx_roSet(kv._2, ros)))
    val prds_didapply = prds.filter(id_p => ! id_p._2.isEmpty)
    prds_didapply
  }

  def tx_check(s_inchis: List[String], p_inchis: Option[List[String]], ro: String) = {
    val products = tx(s_inchis, ro)
    products.containsMatch(p_inchis)
  }

  def tx_roSet_check(s: List[String], p: Option[List[String]], ros: Map[Int, String]) = ros.exists(kv => tx_check(s, p, kv._2))

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
    val cmd = args(0)
    val cargs = args.drop(1)
    if (args.length == 0) {
      println("Usage: sbt \"run roapply rodumpfile substratefile\"")
      println("Usage: sbt \"run roinfer rxndumpfile\"")
      println("Usage: sbt \"run rodump rodumpfile\"")
      println("Usage: sbt \"run rxndump rxndumpfile\"")
      System.exit(-1)
    }

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
    def collapse_bro_differ(l: List[RORow]): List[RORow] = {
      // the following assumes that the rows have the same ero_id
      // which also means the cro_id, arity, ero will be the same
      // outputs single row by merging witness_sz, witnesses, 
      def combine_rows(rs: List[RORow]): RORow = {
        def rdc(a: RORow, b: RORow) = new RORow(a.arity, a.witness_sz + b.witness_sz, a.ero_id, a.cro_id, a.ero, a.witnesses ++ b.witnesses)
        return rs.reduce(rdc)
      }
      val by_eroid = l groupBy (x => x.ero_id)
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
      eros_ :+= new RORow(arity, witness size, ero ID, cro ID, ero, witness)
    }
    val (eros_bad, eros_good) = eros_.partition(x => malformed(x.ero))
    val eros = collapse_bro_differ(eros_good)
    // we can check for malformed cro or ero by checking 
    // if rxn.trim starts or ends with >>

    val eroSort = eros.sortBy(x => (x.arity, x.witness_sz, x.ero_id))
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

object RORow {
  def fromString(s: String) = { 
    val ss = s.split('\t')
    val ro = new RO(new RxnWithWildCards(ss(4)))
    new RORow(ss(0).toInt, ss(1).toInt, ss(2).toInt, ss(3).toInt, ro, ss(5).split(' ').map(_.toInt))
  }
}

class RORow(ar: Int, w_sz: Int, e_id: Int, c_id: Int, e: RO, rxns: Seq[Any])  {
  val arity = ar
  val witness_sz =  w_sz
  val ero_id = e_id
  val cro_id = c_id
  val ero = e
  val witnesses = rxns

  override def toString() = arity + "\t" + witness_sz + "\t" + ero_id + "\t" + cro_id + "\t" + ero.rxn + "\t" + witnesses.reduce(_ + " " + _)

  def render(db: MongoDB) = ero.render("sz:" + witness_sz + "id:" + ero_id + ".png", "E.g.:" + db.getReactionFromUUID(witnesses(0).asInstanceOf[Int].toLong).getReactionName)
}
