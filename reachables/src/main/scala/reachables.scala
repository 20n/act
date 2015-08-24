package com.act.reachables

import java.io.PrintWriter
import java.io.File
import java.io.FileOutputStream
import act.shared.Reaction
import act.shared.Reaction.RxnDataSource
import act.shared.Chemical
import act.server.SQLInterface.MongoDB
import act.shared.helpers.MongoDBToJSON
import org.json.JSONArray
import org.json.JSONObject
import collection.JavaConversions._ // for automatically converting to scala collections
import scala.io.Source

object reachables {
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("Usage: run --prefix=PRE --hasSeq=true|false --regressionSuiteDir=path --extra=[semicolon-sep db.chemical fields] --writeGraphToo")
      println("Example: run --prefix=r")
      println("         will create reachables tree with prefix r and by default with only enzymes that have seq")
      println("Example: run --prefix=r --extra=xref.CHEBI;xref.DEA;xref.DRUGBANK")
      println("         will make sure all CHEBI/DEA/DRUGBANK chemicals are included. Those that are already reachable will be in the normal part of the tree and those that are not will have parent_id < -1 ")
      System.exit(-1);
    } 

    val params = new CmdLine(args)
    val prefix = params.get("prefix") match { 
                    case Some(x) => x
                    case None => println("Need --prefix. Abort"); System.exit(-1); ""
                 }
    
    write_reachable_tree(prefix, params)
    write_node_cascades(prefix)
  }

  def write_reachable_tree(prefix: String, opts: CmdLine) { 
    val g = prefix + ".graph.json" // output file for json of graph
    val t = prefix + ".trees.json" // output file for json of tree
    val rdir = prefix + ".regressions/" // regression output directory

    println("Writing disjoint graphs to " + g + " and forest to " + t)

    val needSeq = 
      opts.get("hasSeq") match { 
        case Some("false") => false
        case _ => true 
      }

    val universal_natives = 
      opts.get("useNativesFile") match { 
        case Some(file) => {
          val data = Source.fromFile(file).getLines
          val inchis = data.filter{ x => x.length > 0 && x.charAt(0) != '#' }
          collection.mutable.Set(inchis.toSeq:_*)
        }
        case _ => null
      }

    val regression_suite_files = 
      opts.get("regressionSuiteDir") match { 
        case Some(dir) => {
          val files = new File(dir).listFiles
          val testfiles = files
                            .map(n => n.getAbsolutePath)
                            .filter(_.endsWith(".test.txt"))
          testfiles.toSet
        }
        case _ => Set()
      }

    val fields = 
      opts.get("extra") match { 
        case Some(fields) => fields split ";"
        case None => null
      }

    val write_graph_too = opts.get("writeGraphToo") != None

    // set parameter for whether we want to exclude rxns that dont have seq
    GlobalParams._actTreeOnlyIncludeRxnsWithSequences = needSeq

    // compute the reachables tree!
    val tree = LoadAct.getReachablesTree(universal_natives, false, fields)
    println("Done: Computing L2 reachables: "  + tree.nodesAndIds.size)

    // create output directory for regression test reports, if not already exists
    mk_regression_test_reporting_dir(rdir)
    // run regression suites if provided
    regression_suite_files.foreach(test => run_regression(tree, test, rdir))

    // dump the tree to directory
    val disjointtrees = tree.disjointTrees() // a JSONObject
    val treejson = disjointtrees.toString(2) // serialized using indent = 2
    write_to(t, treejson)
    println("Done: writing disjoint trees");

    if (write_graph_too) {
      println("scala/reachables.scala: You asked to write graph, in addition to default tree.")
      println("scala/reachables.scala: This will most likely run out of memory")
      val disjointgraphs = tree.disjointGraphs() // a JSONArray
      val graphjson = disjointgraphs.toString(2) // serialized using indent = 2
      write_to(g, graphjson)
      println("scala/reachables.scala: Done writing disjoint graphs");
    }

    println("Done: Written reachables to trees (and graphs, if requested).")
  }

  def run_regression(nw: Network, test_file: String, output_report_dir: String) {
    val testlines: List[String] = Source.fromFile(test_file).getLines.toList
    val testcols: List[List[String]] = testlines.map(line => line.split("\t").toList)

    val hdr = List("inchi", "name", "plausibility", "comment", "reference")
    if (testcols.length == 0 || !testcols(0).equals(hdr)) {
      println("Invalid test file: " + test_file)
      println("\tExpected: " + hdr.toString)
      println("\tFound: " + testcols(0).toString)
    } else {
  
      // get inchis for all the reachables
      // Network.nodesAndIds(): Map[Node, Long] i.e., maps nodes -> ids
      // chemId2Inchis: Map[Long, String] i.e., maps ids -> inchis
      // so we take the Map[Node, Long] and map each of the key_value pairs
      // by looking up their ids (using n_ids._2) in chemId2Inchis
      // then get a nice little set from that Iterable
      val reachable_inchis: Set[String] = nw.nodesAndIds.map(n_ids => ActData.chemId2Inchis.get(n_ids._2)).toSet

      // delete the header from the data set, leaving behind only the test rows
      val rows = testcols.drop(1)

      // partition test rows based on whether this reachables set passes or fails them
      val (passed, failed) = rows.partition(testrow => run_regression(testrow, reachable_inchis))

      val report = generate_report(test_file, passed, failed)
      write_to(output_report_dir + "/" + new File(test_file).getName, report)
      println("Regression file: " + test_file)
      println("Total test: " + rows.length + " (passed, failed): (" + passed.length + ", " + failed.length + ")")
    }
  }

  def mk_regression_test_reporting_dir(dir: String) {
    val dirl = new File(dir)
    if (dirl exists) {
      if (dirl.isFile) {
        println(dir + " already exists as a file. Need it as dir for regression output. Abort.")
        System.exit(-1)
      }
    } else {
      dirl.mkdir()
    }
  }

  def generate_report(f: String, passed: List[List[String]], failed:List[List[String]]) = {
    val total = passed.length + failed.length
    val write_successes = false
    val write_failures = true

    val lines = 
      // add summary to head of report file
      List(
        "** Regression test result for " + f,
        "\tTOTAL: " + total + " PASSED: " + passed.length,
        "\tTOTAL: " + total + " FAILED: " + failed.length
      ) ++ (
        // add details of cases that succeeded
        if (write_successes) 
          passed.map("\t\tPASSED: " + _).toList
        else
          List()
      ) ++ (
        // add details of cases that failed
        if (write_failures) 
          failed.map("\t\tFAILED: " + _).toList
        else
          List()
      )

    // make the report as a string of lines
    val report = lines reduce (_ + "\n" + _)

    // return the report
    report
  }

  def run_regression(row: List[String], reachable_inchis: Set[String]): Boolean = {
    val inchi = row(0) // inchi column
    val should_exist = row(2).toBoolean // plausibility column

    val exists = reachable_inchis.contains(inchi)

    if (should_exist)
      exists // if should-exist then output whether it exists
    else
      !exists // if should-not-exist then output negation of it-exists in reachable
  }

  def write_node_cascades(p: String) {
    var dir = p + "-data/"
    var chemlist = p + ".chemicals.tsv"
    val dirl = new File(dir)
    if (dirl exists) {
      if (dirl.isFile) {
        println(dir + " already exists as a file. Need to dump data to that dir")
        System.exit(-1)
      }
    } else {
      dirl.mkdir()
    }
    // we write the cascades for reachables back into the db, so need a connection
    val backendDB = ("localhost", 27017, "actv01")
    val db = new MongoDB(backendDB._1, backendDB._2, backendDB._3)

    // Set(nodeIDs) = nids from the tree minus those artificially asserted as reachable
    val reachableSet = get_set(ActData.ActTree.nids.values()) diff 
                        get_set(ActData.chemicalsWithUserField_treeArtificial)
    // List(nodesIDs) = nids as a List
    val reachables = reachableSet.toList 

    // do we use Classes of rxns or all unbinned rxns? Based on flag.
    val producers = if (GlobalParams.USE_RXN_CLASSES) ActData.rxnClassesThatProduceChem else ActData.rxnsThatProduceChem 
    val consumers = if (GlobalParams.USE_RXN_CLASSES) ActData.rxnClassesThatConsumeChem else ActData.rxnsThatConsumeChem 

    // List(Set(rxnids)) : all outgoing connections to this node
    //    Not just the ones that are in the tree, but all potential children
    //    These potential children are reachable, modulo those whose rxn requires
    //      unreachable other substrate
    val rxnsThatConsume = reachables.map( n => get_set(consumers.get(n)) ) 
    val downRxns = rxnsThatConsume.map( ridset => ridset.map( r => new RxnAsL2L(r, reachableSet)) )

    // List(Set(rxnids)) : all incoming connections to this node
    //    Not just the ones that are in the tree, but all potential parents that
    //    were rejected as parents (but as still reachable), and those that are
    //    are plain not reachable. 
    val rxnsThatProduce  = reachables.map( n => get_set(producers.get(n)) ) 
    val upRxns = rxnsThatProduce.map( ridset => ridset.map( r => new RxnAsL2L(r, reachableSet)) )

    // List(parents) : parents of corresponding reachables
    def getp(n: Long): Long = { val p = ActData.ActTree.get_parent(n); if (p == null) -1 else p; }
    val parents = reachables.map( getp )

    val reach_neighbors = (reachables zip parents) zip (upRxns zip downRxns)
    for (tuple <- reach_neighbors) {
      val reachid = tuple._1._1
      val json = updowns_json(tuple)
      val jsonstr = json.toString(2)
      write_to(dir + "c" + reachid + ".json", jsonstr)
    }

    println("Done: Written node updowns.")

    // construct cascades for each reachable and then convert it to json
    Waterfall.init(reachables, upRxns)

    println("Performance: caching bestpath will slightly improve perf. Implement if needed.")
    for (reachid <- reachables) {
      val waterfall = new Waterfall(reachid)
      val json    = waterfall.json
      val jsonstr = json.toString(2)
      // write to disk for front end explorer to use
      write_to(dir + "p" + reachid + ".json", jsonstr)

      // reachid == -1 and -2 are proxy nodes and not real chemicals
      if (reachid >= 0) {
        // install into db.waterfall so that front end query-er can use
        val mongo_json = MongoDBToJSON.conv(json)
        db.submitToActWaterfallDB(reachid, mongo_json)
      }
    }

    println("Done: Written node pathsets/waterfalls.")

    def merge_lset(a:Set[Long], b:Set[Long]) = a ++ b 
    val rxnids = rxnsThatProduce.reduce(merge_lset) ++ rxnsThatConsume.reduce(merge_lset)
    for (rxnid <- rxnids) {
      // val json = rxn_json(ActData.allrxns.get(rxnid))
      val json = rxn_json(db.getReactionFromUUID(rxnid))
      val jsonstr = json.toString(2)
      write_to(dir + "r" + rxnid + ".json", jsonstr)
    }

    println("Done: Written reactions.")

    // upRxns is List(Set[RxnAsL2L]): need to pull out all chems in each set within each elem of list
    def foldset(s: Set[RxnAsL2L]) = {
      var acc = Set[Long]()
      for (cas <- s)
        for (c <- cas.getReferencedChems()) 
          acc += c
      acc
    }
    def foldlistset(acc: Set[Long], s: Set[RxnAsL2L]) = acc ++ foldset(s) 
    val upmols = upRxns.foldLeft(Set[Long]())( foldlistset )
    val downmols = downRxns.foldLeft(Set[Long]())( foldlistset )
    val molecules = (reachables ++ parents).toSet ++ upmols ++ downmols
    for ( mid <- molecules ) {
      val mjson = mol_json(db.getChemicalFromChemicalUUID(mid))
      val jsonstr = mjson.toString(2)
      write_to(dir + "m" + mid + ".json", jsonstr)
    }

    println("Done: Written molecules.")

    // now write a big tab-sep file with the "id smiles inchi synonyms" 
    // of all chemicals referenced so that later we can run a process 
    // to render each one of those chemicals.
    val chemfile = to_append_file(chemlist)
    for (mid <- molecules) {
      val torender = torender_meta(db.getChemicalFromChemicalUUID(mid))
      append_to(chemfile, torender)
    }
    chemfile.close()

    println("Done: Written chemicals.tsv.")

    def hr() = println("*" * 80)
    hr
    println("Now run the following to get svg images for each molecule:")
    println("./src/main/resources/mksvgs.sh " + chemlist + " " + dir + ": takes about 88 mins.")
    hr
    println("After that you may set your webserver's document root to: <act.git loc>/api/www/html")
    println("And then go to http://yourserver/nw/clades-tree-collapse.html")
    println("If the page cannot locate the reachables tree, you may have to ")
    println("ln -s ./" + p + "{-data, .graph.json, .trees.json} <act.git loc>/api/www/html/nw/")
    hr
  }

  def mol_json(c: Chemical) = {
    if (c == null) {
      new JSONObject
    } else {
      val mongo_moljson = MongoDB.createChemicalDoc(c, c.getUuid())
      val json = MongoDBToJSON.conv(mongo_moljson)
      json 
    }
  }

  def torender_meta(c: Chemical) = {
    if (c == null) {
      "(null)"
    } else {
      val inchi = c getInChI
      val smiles = c getSmiles
      val id = c getUuid
      // various names: canon: String, synonyms: List[String], brendaNames: List[String], 
      // not queried: (pubchem) names: Map[String, String[]]
      val names = (c.getSynonyms() ++ c.getBrendaNames()) + c.getCanon()

      id + "\t" + smiles + "\t" + inchi + "\t" + names.toString()
    }
  }

  def rxn_json(r: Reaction) = {
    val id = r.getUUID()
    val mongo_json = MongoDB.createReactionDoc(r, id)
    val json = MongoDBToJSON.conv(mongo_json)
    json
  }

  def updowns_json(c: ((Long, Long), (Set[RxnAsL2L], Set[RxnAsL2L]))) = {
    val chemid = c._1._1
    val parent = c._1._2
    val uprxns = c._2._1
    val downrxns = c._2._2
    val up = new JSONArray
    for (r <- uprxns) up.put(r.json)
    val down = new JSONArray
    for (r <- downrxns) down.put(r.json)

    val json = new JSONObject
    json.put("chemid", chemid)
    json.put("parent", parent)
    json.put("upstream", up)
    json.put("downstream", down)

    // return a tuple of json string of up and down from a node
    json
  }

  class RxnAsL2L(rid: Long, reachables: Set[Long]) { 
    val rxnid = rid
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
      // as this object will go into updowns for many reachables
      val json = new JSONObject
      json.put("rxnid", rxnid)
      json.put("reachable", isreachable)
      json.put("substrates", new JSONArray(substrates))
      json.put("products", new JSONArray(products))
      json
    }

    def describe() = ActData.rxnEasyDesc.get(rxnid)

    def getReferencedChems() = substrates ++ products // Set[Long] of all substrates and products

    override def toString() = "rxnid:" + rid 
  }

  object Waterfall {
    // cache of computed best precusors from a node
    var cache_m = Map[Long, RxnAsL2L]()
    var cache_mr = Map[(RxnAsL2L, Long), List[Long]]()

    // map to reachables -> rxns that have as product the reachable
    var upR = Map[Long, Set[RxnAsL2L]]() 

    // terminal nodes (cofactors never show up in RxnAsL2L): 
    // natives
    // markedReachables (list in MongoDB.java)
    var natives = List[Long]()
    var curated_availables = List[Long]()

    def init(reachables: List[Long], upRxns: List[Set[RxnAsL2L]]) {
      upR = (reachables zip upRxns).toMap
      natives = ActData.natives.map(Long.unbox(_)).toList
      curated_availables = ActData.markedReachable.keys.map(Long.unbox(_)).toList
    }

    def is_universal(m: Long) = natives.contains(m) || curated_availables.contains(m)

    def bestprecursor(rxn: RxnAsL2L, prod: Long): List[Long] = 
    if (cache_mr contains (rxn, prod)) cache_mr((rxn, prod)) else {
      // picks the substrates of the rxn that are most similar to prod
      // if the rxn is "join" (CoA + acetyl) | "exchange" (transaminase)
      // then it is allowed to return multiple substrates as needed for the rxn
      // so basically, all it does is remove all cofactors

      val precursors = get_set(rxn.substrates).toList
      // val precursors = filter_by_edit_dist(subtrates, prod)

      cache_mr = cache_mr + ((rxn, prod) -> precursors)

      precursors
    }

    // We only pick rxns that lead monotonically backwards in the tree. 
    // This is conservative to avoid cycles (we could be optimistic and jump 
    // fwd in the tree, if the rxn is really good, but we risk infinite loops then)

    def higher_in_tree(mm: Long, r: RxnAsL2L) = {
      def tree_depth_of(a: Long): Int = ActData.ActTree.tree_depth.get(a)
      val ss = get_set(r.substrates)
      val prod_tree_depth = tree_depth_of(mm)
      val substrate_tree_depths = ss.map(tree_depth_of)
      val max_substrate_tree_depth = substrate_tree_depths.reduce(math.max) 
      max_substrate_tree_depth < prod_tree_depth
    }
 
    def bestprecursor(m: Long): RxnAsL2L = if (cache_m contains m) cache_m(m) else {
      
      def has_substrates(r: RxnAsL2L) = {
        // if (r.substrates.isEmpty) println("[" + r.rxnid + "] zero substrates")
        ! r.substrates.isEmpty
      }

      // incoming unreachable rxns ignored 
      val upReach = upR(m).filter(_.isreachable) 
      // println("(1) upR(m)       : " + upR(m))
      // println("(1) upReach      : " + upReach)

      // we dont want to use reactions that dont have any substrates (most likely bad data)
      val upNonTrivial = upReach.filter(has_substrates) 
      // println("(2) upNonTrivial : " + upNonTrivial)

      // to avoid circular paths, we require the precuror rxn to go towards natives
      val up = upNonTrivial.filter(higher_in_tree(m, _)) 
      // println("(3) up           : " + up)
    
      // ***************************************************************************
      // The reachable tree construction is much more heuristic than we need here.
      // The reason we need harsh heuristics there is because it *ensures* the tree
      // property of a single parent. Here, we can pick many parents and so want to
      // be conservative about elimination.
      // ***************************************************************************
      // val parent = { val p = ActData.ActTree.get_parent(prod); if (p == null) -1 else p }
      // get_parent(_) gets populated during tree construction in 
      // ../java/com/act/reachables/TreeReachability.java:pickParentsForNewReachables
      // which uses the delta between carbons (pickMostSimilar by counting C|c's)
      // and topology heuristics to come up with the most appropriate parent
      // (parent needs to be in layer above; and rich-get-richer amongst parent options)
      // Here we split it two ways: first pick a rxn properly; then picking the relevant
      // substrate of the rxn to follow backwards is a much simpler task (C-counting)
      // ***************************************************************************

      // selects the reaction from reachable rxns in "up" which is the best opt for product m
      // This consists of two steps:
      // 1. collapse replicated reaction entries to the truly orthogonal substrate sets
      // 2. sort on (datasrc, |org|, |expr|) and output the first

      // HELPERS for 1. ---------
      class rmeta(val r: RxnAsL2L,
                  val datasrc: Set[RxnDataSource], 
                  val subs: Set[Long], 
                  val orgs: Set[String], 
                  val expr: Set[String]) {
        override def toString() = "rxnid:" + r.rxnid + "; orgs:" + orgs + "; src:" + datasrc 
      }
      def initmeta(r: RxnAsL2L) = { 
        val subs = get_set(r.substrates)
        val (src, orgs, expr) = get_rxn_metadata(r.rxnid)
        new rmeta(r, src, subs, orgs, expr)
      }

      def get_rxn_metadata(r: Long) = {
        val dataSrc: RxnDataSource = ActData.rxnDataSource.get(r) 

        val unimplemented_msg = "UNIMPLEMENTED: get_rxn_metadata"
        val cloningData = unimplemented_msg // rxn.getCloningData
        val exprData = Set[String](unimplemented_msg) // cloningData.map(d => d.reference + ":" + d.organism + ":" + d.notes)
        val orgs_ids = Array[String](unimplemented_msg) // rxn.getOrganismIDs.map("id:" + _.toString)
  
        // the organism data is a mess: while there are organismIDs/organismData fields
        // that hold structured information; they sometimes do not have all the organisms
        // that appear in the easy_desc field! So lets do the following:
        // Lets pick the field from which we get the max (organismIDs or easy_desc)
        def between(s: Char, e: Char, str: String) = { 
          val ss = str.indexOf(s)
          val ee = str.indexOf(e, ss)
          str.slice(ss + 1, ee) 
        }
        def extract_orgs(desc: String) = between('{', '}', desc).split(", ")
        val orgs_str = extract_orgs(ActData.rxnEasyDesc.get(r))
        val orgs = if (orgs_ids.size > orgs_str.size) orgs_ids.toSet else orgs_str.toSet

        (Set(dataSrc), orgs, exprData.toSet)
      }

      def subsumed_by(rxns: Map[Long, rmeta]): Map[Long, Set[Long]] = {
        // construct acyclic graph of subsumption
        def subsumes(large: (Long, rmeta), small: (Long, rmeta)) = {
          val subsumes = small._2.subs subsetOf large._2.subs
          val equal = subsumes && small._2.subs.size == large._2.subs.size
          
          // if the sets are equal then we break ties on rxn_ids
          if (equal)
            small._1 < large._1
          else
            subsumes
        }
        def idsThatSubsume(small: (Long, rmeta)) = {
          val subsuming_rxns = rxns.filter(large => subsumes(large, small))
          val subsuming_ids = subsuming_rxns.map(_._1).toSet
          subsuming_ids
        }
        rxns.map(small => small._1 -> idsThatSubsume(small)).toMap
      }

      def collapse_subsumed(rxns: Map[Long, rmeta], in_edges: Map[Long, Int], topo: Map[Long, Set[Long]]): Map[Long, rmeta] = {
        // topological order collapse of rxns using the acyclic graph in subsumed

        if (in_edges.isEmpty)
          rxns
        else {
          val idz = in_edges.find{ case (i, sz) => sz == 0 }
          idz match { 
            case Some((id, sz)) => {
              val absorbing_parents = topo(id)
              var new_in_edges = in_edges - id
              var new_rxns = rxns
              if (absorbing_parents.size > 0) {
                // if absorbing_parents == 0 then we are at the terminal node; dont remove it
                val consumed = rxns(id)
                new_rxns = new_rxns - id // remove the consumed rxn map
                for (p <- absorbing_parents) {
                  new_rxns = new_rxns + (p -> absorb(consumed, new_rxns(p)))
                  new_in_edges = new_in_edges + (p -> (new_in_edges(p) - 1))
                }
              }
              collapse_subsumed(new_rxns, new_in_edges, topo)
            }
            case None => {
              throw new Exception("cannot happen! in_edges.isNotEmpty and no 0 found.")
            }
          }
        }
      }

      def absorb(r: rmeta, R: rmeta) = {
        if (!(r.subs subsetOf R.subs)) throw new Exception("expected subset!")
        new rmeta(R.r, r.datasrc ++ R.datasrc, R.subs, r.orgs ++ R.orgs, r.expr ++ R.expr)
      }

      def invert[X](m: Map[X, Set[X]]): Map[X, Set[X]] = {
        if (m.isEmpty) 
          Map[X, Set[X]]()
        else {
          def invertelem(e: (X, Set[X])) = unionMapValues(
                Map(e._1 -> Set[X]()), // nodes with no incoming get left out as keys if 
                e._2.map(_ -> Set(e._1)).toMap
          )
          unionMapValues(invertelem(m head), invert(m drop 1))
        }
      }

      // 1. ---------
      val rxns = up.map(r => r.rxnid -> initmeta(r)).toMap
      // println("(3) rxns: " + rxns)

      // Collapse replicated entries (that exist e.g., coz brenda has different rows)
      // If the entire substrate set of a reaction is subsumed by another, it is a replica
      // Copy its organism set to the subsuming reactions. Do this in O(n) using a topo sort
      val subsumed_map = subsumed_by(rxns)
      val in_edges = invert(subsumed_map)
      def graph(map: Map[Long, Set[Long]]) = {
        val edges = for ((k,v) <- map; vv <- v) yield { k + " -> " + vv + ";" }
        "digraph gr {\n" + edges.mkString("\n") + "\n}"
      }
      // println("subsumed_by: " + graph(subsumed_map))
      // println("subsumes: " + graph(in_edges))
      val in_counts = in_edges.map{case (id, incom) => (id, incom.size)}.toMap
      val collapsed_rxns = collapse_subsumed(rxns, in_counts, subsumed_map)
      // println("(4) collapsed_rxns: " + collapsed_rxns)

      // 2. ---------
      def rxn_compare(A: (Long, rmeta), B: (Long, rmeta)) = {
        // prioritizes reactions by
        // - By data source (metacyc > brenda > uniprot > kegg) 
        // - By # organisms that witness the reaction
        // - By expression data
        val src_confidence = Map(RxnDataSource.METACYC -> 100, 
                                RxnDataSource.BRENDA -> 50, 
                                RxnDataSource.KEGG -> 10)
        def best_src(x: rmeta) = x.datasrc.map(src_confidence(_)).reduce(math.max)
        val a = A._2
        val b = B._2
        val a_before_b = ( 
            (best_src(a) > best_src(b)) // either the rxn is mentioned in a better datasrc
         || (a.orgs.size > b.orgs.size) // or the number of witness organisms is greater
         || (a.expr.size > b.expr.size) // or the number of expression entries is greater
        )

        a_before_b
      }
      // collapsed_rxns: Map[Long, rmeta] 
      // keyed on the representative rxnid (might have absorbed many other's meta)
      val sorted = collapsed_rxns.toList.sortWith(rxn_compare)
      
      // pick the most prominent reaction in the sort above
      val precursor_rxn = sorted(0)._2.r

      cache_m = cache_m + (m -> precursor_rxn)

      // output. ------
      precursor_rxn
    }

    def bestpath(m: Long, step: Int): Path = {
      // construct a path all the way back to natives
      print_step(m)

      is_universal(m) match {
        case true => {
          // base case
          new Path(Map()) 
        }
        case false => {
          // lookup the step back
          val rxnup = bestprecursor(m)
          val current_step = step + 1

          // println("picked best precursor: " + rxnup + " = " + rxnup.describe())

          // compute the set accumulation of paths taken back and the current step
          val path = new Path(current_step, rxnup) ++ {
            // set of substrates that we need to follow backwards until we hit natives
            val precursors = bestprecursor(rxnup, m)
            val pathsback = precursors.map(bestpath(_, current_step))

            // return the combination all paths back, except if no precursors
            if (pathsback.isEmpty) new Path(Map()) else pathsback.reduce(_ ++ _) 
          }

          // return path from m all the way back to natives
          path
        }
      }
    }

    def print_step(m: Long) {
      def detailed {
        println("\nPicking best path for molecule:" + m)
        println("IsNative || MarkedReachable: " + is_universal(m))
        println("IsCofactor: " + ActData.cofactors.contains(m))
        println("Tree Depth: " + ActData.ActTree.tree_depth.get(m))
      }
      def brief {
        println("Picking best path for molecule: " + m)
      }

      // run one of the following if you need diagnostics
      // brief or detailed
    }
    
  }

  class Waterfall(target: Long) {
    val t = target

    // we cannot just bestpath on target because we dont want to "choose"
    // a reaction between those coming at target; we want all of them
    // (filtered to those that are reachable; and those that go up)
    // but above that we want the best paths
    // 
    // paths is a rxnup -> List[Path]
    // the rxnup maps to a list because it might have multiple relevant 
    // substrates that need to be traced back
    def upNreach(r: RxnAsL2L) = r.isreachable && r.substrates.size>0 && Waterfall.higher_in_tree(t, r)

    def get_bestpath_foreach_uprxn(trgt: Long) = {
      for (r <- Waterfall.upR(trgt) if upNreach(r)) yield {
        // for each reachable rxn r that leads upwards
        // narrow down to r's substrates that make up the target
        val subs = Waterfall.bestprecursor(r, trgt)

        // println("(0) Target: " + trgt + " upRxn: " + r + " substrates to follow back" + subs)
        // then run backwards to natives for each of those substrates
        r -> subs.map(Waterfall.bestpath(_, 0))
      }
    }

    def is_target_reachable = Waterfall.upR contains t

    val paths = if (!is_target_reachable) Map() else { Map() ++ get_bestpath_foreach_uprxn(target) }

    def json() = {
      val json = new JSONObject
      val ps = paths.map{ case (rxn_up, paths_up) => {
                            // rxn_up: RxnAsL2L
                            // paths_up:List[Path]: list coz multiple substrates 
                            //                      might be traced back to natives
                            val path_opt = new JSONObject
                            path_opt.put("rxn", rxn_up.rxnid)
                            // the hyperedge may have many substrates so an AND array
                            val hyperpath = new JSONArray
                            for (s <- paths_up.map(_.json)) hyperpath.put(s)
                            path_opt.put("best_path", hyperpath)
                        }}

      val allref_rxns = paths.map{ case (rxn_up, paths_up) => {
                          Set(rxn_up) ++ paths_up.foldLeft(Set[RxnAsL2L]())( (acc, p) => acc ++ p.rxnset )
                        }}.flatten

      json.put("target", t)

      // each target has many options (OR) of paths back
      // for each entry within, there might be other arrays, but those
      // are for when a hyperedge has multiple substrates (AND)
      val fanin = new JSONArray
      for (p <- ps) fanin.put(p)
      json.put("fan_in", fanin)

      val meta = new JSONArray
      def rxnmeta(r: RxnAsL2L) = { 
        val rm = new JSONObject
        rm.put("id", r.rxnid)
        rm.put("txt", ActData.rxnEasyDesc.get(r.rxnid))
        rm.put("seq", new JSONArray)
        rm.put("substrates", new JSONArray(r.substrates))
        rm.put("products", new JSONArray(r.products))
        rm
      }
      for (r <- allref_rxns) meta.put(rxnmeta(r))
      json.put("rxn_meta", meta)

      json
    }

    override def toString = t + " --> " + paths
  }

  def unionMapValues[X,Y](m1: Map[X, Set[Y]], m2: Map[X, Set[Y]]) = {
    // merge the maps of this and other; taking care to union 
    // value sets rather than overwrite
    var keys = List[X]() ++ m1.keys ++ m2.keys
    val kvs = for (s <- keys) yield { 
      val a = if (m2 contains s) m2(s) else Set[Y]()
      val b = if (m1 contains s) m1(s) else Set[Y]()
      s -> (a ++ b)
    }

    Map() ++ kvs
  }

  class Path(val rxns: Map[Int, Set[RxnAsL2L]]) {
    // a hypergraph path: The transformations are listed out in step order 
    // there might be multiple rxns at a step because at a previous step
    // a hyperedge might exist that requires two or more precursors

    def this(step: Int, r: RxnAsL2L) = this(Map(step -> Set(r)))

    def ++(other: Path) = new Path(unionMapValues(other.rxns, this.rxns))

    def rxnset() = rxns.foldLeft(Set[RxnAsL2L]())( (acc, t) => acc ++ t._2 )

    def json() = {
      // an array of stripes
      // coz of this being a hypergraph path, there might be branching when 
      // an edge has multiple substrates need to be followed back
      // we dissect this structure as stripes going levels back
      val allsteps = rxns.map { case (i, rs) => { 
                        val pstep = new JSONObject
                        pstep.put("stripe", i)
                        val rxns_in_stripe = new JSONArray
                        for (r <- rs.map(_.rxnid)) rxns_in_stripe.put(r)
                        pstep.put("rxns", rxns_in_stripe)
                        pstep
                    }}
      val stripes = new JSONArray
      for (s <- allsteps) stripes.put(s)
      stripes
    }

    override def toString() = rxns.toString

  }

  def filter_by_edit_dist(substrates: List[Long], prod: Long): List[Long] = {
    def get_inchi(m: Long) = { 
      val inchi = ActData.chemId2Inchis.get(m)
      if (inchi != null) Some(inchi) else None    
    }

    val basis = List('C', 'N', 'O')
    val emptybasis = List(0, 0, 0)

    def get_atom_counts(formula: String) = Map() ++ (for (atom <- basis) yield {
      val regex = (atom + """([0-9]+)""").r
      atom -> ((regex findFirstMatchIn formula) match { 
        case Some(m) => m.group(1).toInt; 
        case None => if (formula contains atom) 1 else 0;
      })
    })

    def vectorize(m: Option[String]) = m match {
                                          case None => emptybasis
                                          case Some(inchi) => {
                                            val spl = inchi.split('/')
                                            if (spl.size <= 2)
                                              emptybasis 
                                            else {
                                              val formula = spl(1) 
                                              val atomcounts = get_atom_counts(formula)
                                              basis.map(atom => atomcounts(atom))
                                            }
                                          }
                                       } 
    def edit_dist(s: Option[String], p: Option[String]) = {
      val pvec = vectorize(p)
      val svec = vectorize(s)
      if (pvec == emptybasis || svec == emptybasis) 
        Int.MaxValue
      else
        // compute the \sum_{b=basis_elem} \abs(diff on b)
        (pvec zip svec).map{ case (pv, sv) => math.abs(pv-sv) }.reduce(_ + _)
    }
    val distances = substrates.map(s => get_inchi(s)).map(edit_dist(_, get_inchi(prod)))
    val min_dist = distances.reduce(math.min)
    val keep = filterByAuxList(substrates, distances, (d:Int) => d == min_dist)
    keep
  }

  def filterByAuxList[A, B](a: List[A], b: List[B], bpredicate: B=>Boolean) = {
    (a zip b).filter{ case (ae, be) => bpredicate(be) }.unzip._1
  }

  def to_append_file(fname: String) = {
    new PrintWriter(new FileOutputStream(new File(fname)), true)
  }

  def append_to(file: PrintWriter, line: String) {
    file append (line + "\n")
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
