package com.act.reachables

import java.io.PrintWriter
import java.io.File
import act.shared.Reaction
import act.shared.Reaction.RxnDataSource
import act.shared.Chemical
import act.server.SQLInterface.MongoDB
import act.shared.helpers.MongoDBToJSON
import org.json.JSONArray
import org.json.JSONObject
import collection.JavaConversions._ // for automatically converting to scala collections

object reachables {
  def main(args: Array[String]) {
    if (args.length == 0) {
      println("Usage: run --prefix=PRE --hasSeq=true|false --extra=[semicolon-sep db.chemical fields]")
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
    
    val g = prefix + ".graph.json"
    val t = prefix + ".trees.json"

    write_reachable_tree(g, t, params)
    write_node_cascades(prefix)
  }

  def write_reachable_tree(g: String, t: String, opts: CmdLine) { 
    println("Writing disjoint graphs to " + g + " and forest to " + t)

    val needSeq = opts.get("hasSeq") match { case Some("false") => false; case _ => true }
    ActLayout._actTreeOnlyIncludeRxnsWithSequences = needSeq
    val act = new LoadAct(true) // true = Load with chemicals
    opts.get("extra") match { 
      case Some(fields) => for (field <- fields split ";") 
                          act.setFieldForExtraChemicals(field) 
      case None => ()
    }
    act.run() // actually execute the full fetch of act from the mongodb
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
    var reachableSet = get_set(ActData.ActTree.nids.values())
    val reachables = reachableSet.toList // List(nodesIDs) in nw

    // List(Set(rxnids)) : all outgoing connections to this node
    //    Not just the ones that are in the tree, but all potential children
    //    These potential children are reachable, modulo those whose rxn requires
    //      unreachable other substrate
    val rxnsThatConsume = reachables.map( n => get_set(ActData.rxnsThatConsumeChem.get(n)) ) 
    val downRxns = rxnsThatConsume.map( ridset => ridset.map( r => new RxnAsL2L(r, reachableSet)) )

    // List(Set(rxnids)) : all incoming connections to this node
    //    Not just the ones that are in the tree, but all potential parents that
    //    were rejected as parents (but as still reachable), and those that are
    //    are plain not reachable. 
    val rxnsThatProduce  = reachables.map( n => get_set(ActData.rxnsThatProduceChem.get(n)) ) 
    val upRxns = rxnsThatProduce.map( ridset => ridset.map( r => new RxnAsL2L(r, reachableSet)) )

    // List(parents) : parents of corresponding reachables
    def getp(n: Long): Long = { val p = ActData.ActTree.get_parent(n); if (p == null) -1 else p; }
    val parents = reachables.map( getp )

    val updowns = ((reachables zip parents) zip (upRxns zip downRxns)).map(updowns_json)
    for ((reachid, json) <- updowns) {
      val jsonstr = json.toString(2)
      write_to(dir + "c" + reachid + ".json", jsonstr)
    }

    println("Done: Written node updowns.")

    // construct cascades for each reachable and then convert it to json
    Cascade.init(reachables, upRxns)

    println("Performance: caching bestpath will improve performance slightly. Implement if needed.")
    val pathsets = reachables.map(new Cascade(_)).map(_.json)
    for ((reachid, json) <- pathsets) {
      val jsonstr = json.toString(2)
      write_to(dir + "p" + reachid + ".json", jsonstr)
    }

    println("Done: Written node pathsets/cascades.")

    def merge_lset(a:Set[Long], b:Set[Long]) = a ++ b 
    val rxnids = rxnsThatProduce.reduce(merge_lset) ++ rxnsThatConsume.reduce(merge_lset)
    val rxn_jsons = rxnids.toList.map( rid => rxn_json(ActData.allrxns.get(rid)) )
    for ((rxnid, json) <- rxn_jsons) {
      val jsonstr = json.toString(2)
      write_to(dir + "r" + rxnid + ".json", jsonstr)
    }

    println("Done: Written reactions.")

    // upRxns is List(Set[RxnAsL2L]): need to pull out all chems in each set within each elem of list
    def foldset(s: Set[RxnAsL2L]) = {
      var acc = Set[Long]()
      for (cas <- s)
        for (c <- cas.getReferencedChems()) // some issue with type (conversion bw java and scala) prevents us from using ++
          acc += c
      acc
    }
    def foldlistset(acc: Set[Long], s: Set[RxnAsL2L]) = acc ++ foldset(s) 
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
    write_to(chemlist, torender.reduce( (a,b) => a + "\n" + b ))

    println("Done: Written molecules.")

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
    (id, json)
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

    // return a tuple of (reachable's id, json string of up and down from a node) 
    (chemid, json) 
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

    def describe() = ActData.allrxns.get(rxnid).getReactionName

    def getReferencedChems() = substrates ++ products // Set[Long] of all substrates and products

    override def toString() = "rxnid:" + rid 
  }

  object Cascade {
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
      natives = (for (c <- ActData.natives) yield Long.unbox(c.getUuid)).toList
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
 
    def bestprecursor(m: Long): RxnAsL2L = if (cache_m contains m) cache_m(m) else {
      
      // We only pick rxns that lead monotonically backwards in the tree. 
      // This is conservative to avoid cycles (we could be optimistic and jump 
      // fwd in the tree, if the rxn is really good, but we risk infinite loops then)
      // TreeReachability: HashMap<Integer, Set<Long>> R_by_layers holds
      // the layers of nodes; it should be inverted and put in ActData.ActTree
      def tree_depth_of(m: Long): Int = ActData.ActTree.tree_depth.get(m)
      def higher_in_tree(mm: Long, ss: Set[Long], rid: Long) = { 
          val prod_tree_depth = tree_depth_of(mm)
          val substrate_tree_depths = ss.map(tree_depth_of)
          // println("[" + rid + "] Layer[" + substrate_tree_depths + "] < " + prod_tree_depth + "?")
          val max_substrate_tree_depth = substrate_tree_depths.reduce(math.max) 
          max_substrate_tree_depth < prod_tree_depth
      }

      def has_substrates(r: RxnAsL2L) = {
        // if (r.substrates.isEmpty) println("[" + r.rxnid + "] zero substrates")
        ! r.substrates.isEmpty
      }

      // incoming unreachable rxns ignored 
      val upReach = upR(m).filter(_.isreachable) 
      // println("(1) upReach: " + upReach)

      // we dont want to use reactions that dont have any substrates (most likely bad data)
      val upNonTrivial = upReach.filter(has_substrates) 
      // println("(2) upNonTrivial: " + upNonTrivial)

      // to avoid circular paths, we require the precuror rxn to go towards natives
      val up = upNonTrivial.filter(r => higher_in_tree(m, get_set(r.substrates), r.rxnid)) 
      // println("(3) up: " + up)
    
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
        val reaction: Reaction = ActData.allrxns.get(r)
        val dataSrc: RxnDataSource = reaction.getDataSource 
        val cloningData = reaction.getCloningData
        val exprData = cloningData.map(d => d.reference + ":" + d.organism + ":" + d.notes)
  
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
        val orgs_str = extract_orgs(reaction.getReactionName)
        val orgs_ids = reaction.getOrganismIDs.map("id:" + _.toString)
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

          // println("best rxn back: " + rxnup.describe())

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
        println("\nPicking best path:")
        println("Chemical: " + ActData.chemMetadata.get(m))
        println("IsNative || MarkedReachable: " + is_universal(m))
        println("IsCofactor: " + ActData.cofactors.contains(m))
        println("Tree Depth: " + ActData.ActTree.tree_depth.get(m))
      }
      def brief {
        println("Picking best path: " + m)
      }

      // brief
    }
    
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
      val ar = new JSONArray
      for (s <- allsteps) ar.put(s)
      ar
    }

    override def toString() = rxns.toString

  }

  class Cascade(target: Long) {
    val t = target

    // we cannot just bestpath on target because we dont want to "choose"
    // a reaction between those coming at target; we want all of them
    // but above that we want the best paths
    // 
    // paths is a rxnup -> List[Path]
    // the rxnup maps to a list because it might have multiple relevant 
    // substrates that need to be traced back
    val paths = Map() ++ (for (r <- Cascade.upR(target) if r.isreachable) yield {
                  // for each reachable rxn r that leads upwards
                  // narrow down to r's substrates that make up the target
                  val subs = Cascade.bestprecursor(r, target)

                  // then run backwards to natives for each of those substrates
                  r -> subs.map(Cascade.bestpath(_, 0))
                })

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

      json.put("target", t)

      // each target has many options (OR) of paths back
      // for each entry within, there might be other arrays, but those
      // are for when a hyperedge has multiple substrates (AND)
      val fanin = new JSONArray
      for (p <- ps) fanin.put(p)
      json.put("fan_in", fanin)
      (t, json)
    }

    override def toString = t + " --> " + paths
  }

  def filter_by_edit_dist(substrates: List[Long], prod: Long): List[Long] = {
    def get_inchi(m: Long) = { 
      val meta = ActData.chemMetadata.get(m)
      if (meta != null) Some(meta.getInChI) else None    
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
