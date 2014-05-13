package com.act.reachables

import java.io.PrintWriter
import java.io.File
import act.shared.Reaction
import act.shared.Chemical
import act.server.SQLInterface.MongoDB
import act.shared.helpers.MongoDBToJSON
import org.json.JSONArray
import org.json.JSONObject
import collection.JavaConversions._ // for automatically converting to scala collections

object reachables {
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("Usage: run out_prefix")
      System.exit(-1);
    } 

    val prefix = args(0)
    val g = prefix + ".graph.json"
    val t = prefix + ".trees.json"

    write_reachable_tree(g, t)
    write_node_cascades(prefix)
  }

  def write_reachable_tree(g: String, t: String) { 
    println("Writing disjoint graphs to " + g + " and forest to " + t)

    val act = new LoadAct(true).run() // true = Load with chemicals
    val tree = ActData.ActTree

    val disjointgraphs = tree.disjointGraphs() // a JSONArray
    val graphjson = disjointgraphs.toString(2) // serialized using indent = 2
    write_to(g, graphjson)

    val disjointtrees = tree.disjointTrees() // a JSONObject
    val treejson = disjointtrees.toString(2) // serialized using indent = 2
    write_to(t, treejson)

    println("Done: Written reachables trees.")
  }

  def write_node_cascades(p: String) {
    var dir = p + "-data/"
    val dirl = new File(dir)
    if (dirl exists) {
      if (dirl.isFile) {
        println(dir + " already exists as a file. Need to dump data to that dir")
        System.exit(-1)
      }
    } else {
      dirl.mkdir()
    }
    var reachableSet = get_set(ActData.ActTree.nids.values())
    val reachables = reachableSet.toList // List(nodesIDs) in nw

    // List(Set(rxnids)) : all outgoing connections to this node
    //    Not just the ones that are in the tree, but all potential children
    //    These potential children are reachable, modulo those whose rxn requires
    //      unreachable other substrate
    val rxnsThatConsume = reachables.map( n => get_set(ActData.rxnsThatConsumeChem.get(n)) ) 
    val downRxns = rxnsThatConsume.map( ridset => ridset.map( r => new CascadeRxn(r, reachableSet)) )

    // List(Set(rxnids)) : all incoming connections to this node
    //    Not just the ones that are in the tree, but all potential parents that
    //    were rejected as parents (but as still reachable), and those that are
    //    are plain not reachable. 
    val rxnsThatProduce  = reachables.map( n => get_set(ActData.rxnsThatProduceChem.get(n)) ) 
    val upRxns = rxnsThatProduce.map( ridset => ridset.map( r => new CascadeRxn(r, reachableSet)) )

    // List(parents) : parents of corresponding reachables
    def getp(n: Long): Long = { val p = ActData.ActTree.get_parent(n); if (p == null) -1 else p; }
    val parents = reachables.map( getp )

    val cascades = ((reachables zip parents) zip (upRxns zip downRxns)).map(cascade_json)
    for ((reachid, json) <- cascades) {
      val jsonstr = json.toString(2)
      write_to(dir + "c" + reachid + ".json", jsonstr)
    }

    println("Done: Written node cascades.")

    def merge_lset(a:Set[Long], b:Set[Long]) = a ++ b 
    val rxnids = rxnsThatProduce.reduce(merge_lset) ++ rxnsThatConsume.reduce(merge_lset)
    val rxn_jsons = rxnids.toList.map( rid => rxn_json(ActData.allrxns.get(rid)) )
    for ((rxnid, json) <- rxn_jsons) {
      val jsonstr = json.toString(2)
      write_to(dir + "r" + rxnid + ".json", jsonstr)
    }

    println("Done: Written reactions.")

    // upRxns is List(Set[CascadeRxn]): need to pull out all chems in each set within each elem of list
    def foldset(s: Set[CascadeRxn]) = {
      var acc = Set[Long]()
      for (cas <- s)
        for (c <- cas.getReferencedChems()) // some issue with type (conversion bw java and scala) prevents us from using ++
          acc += c
      acc
    }
    def foldlistset(acc: Set[Long], s: Set[CascadeRxn]) = acc ++ foldset(s) 
    val upmols = upRxns.foldLeft(Set[Long]())( foldlistset )
    val downmols = downRxns.foldLeft(Set[Long]())( foldlistset )
    val molecules = (reachables ++ parents).toSet ++ upmols ++ downmols
    val moldata = molecules.toList.map( mol_json )
    for ( (m, c, mjson) <- moldata ) {
      val jsonstr = mjson.toString(2)
      write_to(dir + "m" + m + ".json", jsonstr)
    }

    // now write a big tab-sep file with the "id smiles inchi synonyms" of all chemicals referenced
    // so that later we can run a process to render each one of those chemicals.
    val torender = moldata.map { case (m, c, j) => torender_meta(c) }
    write_to("chemicals.tsv", torender.reduce( (a,b) => a + "\n" + b ))

    println("Done: Written molecules.")
  }

  def mol_json(mid: Long) = {
    val c: Chemical = ActData.chemMetadata.get(mid)
    if (c == null) {
      println("null chem for id: " + mid)
      (mid, null, new JSONObject)
    } else {
      val mongo_moljson = MongoDB.createChemicalDoc(c, c.getUuid())
      val json = MongoDBToJSON.conv(mongo_moljson)
      (mid, c, json) 
    }
  }

  def torender_meta(c: Chemical) = {
    if (c == null) {
      "(null)"
    } else {
      val inchi = c.getInChI()
      val smiles = c.getSmiles()
      val id = c.getUuid()
      // various names: canon: String, synonyms: List[String], brendaNames: List[String], 
      // not queried: (pubchem) names: Map[String, String[]]
      val names = (c.getSynonyms() ++ c.getBrendaNames()) + c.getCanon()

      id + "\t" + smiles + "\t" + inchi + "\t" + names.mkString(", ")
  }
  }

  def rxn_json(r: Reaction) = {
    val id = r.getUUID()
    val mongo_json = MongoDB.createReactionDoc(r, id)
    val json = MongoDBToJSON.conv(mongo_json)
    (id, json)
  }

  def cascade_json(c: ((Long, Long), (Set[CascadeRxn], Set[CascadeRxn]))) = {
    val chemid = c._1._1
    val parent = c._1._2
    val uprxns = c._2._1
    val downrxns = c._2._2
    val up = new JSONArray
    for (r <- uprxns) up.put(r.json())
    val down = new JSONArray
    for (r <- downrxns) down.put(r.json())

    val json = new JSONObject
    json.put("chemid", chemid)
    json.put("parent", parent)
    json.put("upstream", up)
    json.put("downstream", down)

    // return a tuple of (reachable's id, json string of cascade up and down) 
    (chemid, json) 
  }

  class CascadeRxn(rid: Long, reachables: Set[Long]) { 
    val substrates = ActData.rxnSubstrates.get(rid)
    val products = ActData.rxnProducts.get(rid)
    val substratesCofactors = ActData.rxnSubstratesCofactors.get(rid)
    val productsCofactors = ActData.rxnProductsCofactors.get(rid)

    // this reaction is "reachable" if all its non-cofactor substrates 
    // are in the reachables set
    val isreachable = substrates forall (s => reachables contains s)

    def json() = {
      // this json is just the basic information elaborating on how
      // this rxn featured in the reachables calculations. the entire
      // gamut of information about the rxn can be located by pulling
      // up the Reaction object (or its associated json in the front end)
      // from the rxnid. 
      // Please do not dump all of that information into this object
      // as this object will go into cascades for many reachables
      val json = new JSONObject
      json.put("rxnid", rid)
      json.put("reachable", isreachable)
      json.put("substrates", new JSONArray(substrates))
      json.put("products", new JSONArray(products))
      json
    }

    def getReferencedChems() = substrates ++ products // Set[Long] of all substrates and products
  }

  def write_to(fname: String, json: String) {
    val file = new PrintWriter(new File(fname))
    file write json
    file.close()
  }

  def get_set(c: java.util.Collection[java.lang.Long]): Set[Long] = {
    var s = Set[Long]()
    if (c != null) {
      for (l <- c)
        s += l
    }
    s
  }

}
