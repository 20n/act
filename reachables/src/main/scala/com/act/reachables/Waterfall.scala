package com.act.reachables

import act.shared.Reaction.RxnDataSource
import org.json.{JSONArray, JSONObject}

object Waterfall extends Falls {
  // cache of computed best precusors from a node

  // the best precursor reaction
  // : given a mol that is a product of many rxns
  //   which one is the best `precursing` rxns
  var cache_bestpre_rxn = Map[Long, ReachRxn]()

  // the best precursor substrates
  // : given a reaction and one of its products
  //   which one of the substrates best matches the product
  var cache_bestpre_substr = Map[(ReachRxn, Long), List[Long]]()

  // the best path backwards from a node
  // : given a mol what is the full single string of
  //   molecules that goes all the way back to natives
  var cache_bestpath = Map[Long, Path]()

  def bestprecursor(rxn: ReachRxn, prod: Long): List[Long] =
    if (cache_bestpre_substr contains (rxn, prod)) cache_bestpre_substr((rxn, prod)) else {
      // picks the substrates of the rxn that are most similar to prod
      // if the rxn is "join" (CoA + acetyl) | "exchange" (transaminase)
      // then it is allowed to return multiple substrates as needed for the rxn
      // so basically, all it does is remove all cofactors

      val precursors = cascades.get_set(rxn.substrates).toList
      // val precursors = filter_by_edit_dist(subtrates, prod)

      cache_bestpre_substr = cache_bestpre_substr + ((rxn, prod) -> precursors)

      precursors
    }

  // We only pick rxns that lead monotonically backwards in the tree.
  // This is conservative to avoid cycles (we could be optimistic and jump
  // fwd in the tree, if the rxn is really good, but we risk infinite loops then)

  def bestprecursor(m: Long): ReachRxn = if (cache_bestpre_rxn contains m) cache_bestpre_rxn(m) else {
    // incoming unreachable rxns ignored
    val upReach = upR(m).filter(_.isreachable)

    // we dont want to use reactions that dont have any substrates (most likely bad data)
    val upNonTrivial = upReach.filter(has_substrates)

    // to avoid circular paths, we require the precuror rxn to go towards natives
    val up = upNonTrivial.filter(higher_in_tree(m, _))
    if (up.isEmpty) {
      println(s"$m has no lower in tree places to go.")
    }
    // ***************************************************************************
    // The reachable tree construction is much more heuristic than we need here.
    // The reason we need harsh heuristics there is because it *ensures* the tree
    // property of a single parent. Here, we can pick many parents and so want to
    // be conservative about elimination.
    // ***************************************************************************
    // val parent = { val p = ActData.instance.ActTree.get_parent(prod); if (p == null) -1 else p }
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
    class rmeta(val r: ReachRxn,
                val datasrc: Set[RxnDataSource],
                val subs: Set[Long],
                val orgs: Set[String],
                val expr: Set[String]) {
      override def toString() = "rxnid:" + r.rxnid + "; orgs:" + orgs + "; src:" + datasrc
    }

    def initmeta(r: ReachRxn) = {
      val subs = cascades.get_set(r.substrates)
      val (src, orgs, expr) = get_rxn_metadata(r.rxnid)
      new rmeta(r, src, subs, orgs, expr)
    }

    def get_rxn_metadata(r: Long) = {
      val dataSrc: RxnDataSource = {
        ReachRxnDescs.rxnDataSource(r) match {
          case None => RxnDataSource.BRENDA
          case Some(ds) => ds
        }
      }

      val unimplemented_msg = "UNIMPLEMENTED: get_rxn_metadata"
      val cloningData = unimplemented_msg // rxn.getCloningData
      val exprData = Set[String](unimplemented_msg) // cloningData.map(d => d.reference + ":" + d.organism + ":" + d.notes)
      val orgs_ids = {
        ReachRxnDescs.rxnOrganismNames(r) match {
          case None => Array()
          case Some(orgs) => orgs.toArray
        }
      }

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
      val org_str_raw = ReachRxnDescs.rxnEasyDesc(r)
      val orgs_str = org_str_raw match {
        case None => Array[String]()
        case Some(o) => extract_orgs(o)
      }
      val orgs = if (orgs_ids.size > orgs_str.size) orgs_ids.toSet else orgs_str.toSet

      (Set(dataSrc), orgs, exprData.toSet)
    }

    // 1. ---------
    val rxns: Map[Long, rmeta] = up.map(r => r.rxnid -> initmeta(r)).toMap

    val collapsed_rxns = {
      if (GlobalParams.USE_RXN_CLASSES) {
        rxns
      } else {
        val msg = "You almost always want to use RXN_CLASSES. " +
          " The reachables/actdata is probably all calculated" +
          " with USE_RXN_CLASSES = true. If not there is old" +
          " code at https://github.com/20n/act/commit/fe8ca11f0" +
          " which computes rxns that are subsumed by others"
        // throw new UnsupportedFlag(msg)
        rxns
      }
    }

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
    val precursor_rxn = sorted.head._2.r

    cache_bestpre_rxn = cache_bestpre_rxn + (m -> precursor_rxn)

    // output. ------
    precursor_rxn
  }

  def bestpath(m: Long): Path = {

    if (cache_bestpath contains m) {

      // our cache has the path, return it
      cache_bestpath(m)

    } else {
      // construct a path all the way back to natives, starting with step 0

      // debugging, print molecule being tranversed
      print_step(m)

      val path_built = {
        if (is_universal(m)) {
          // base case
          new Path(Map())
        } else {
          // lookup the step_back
          val rxnup = bestprecursor(m)
          // the paths we accumulate from precursors are one step back
          val step_back = 1

          // compute the set accumulation of paths taken back and the current_step
          val path = new Path(step_back, rxnup) ++ {
            // set of substrates that we need to follow backwards until we hit natives
            val precursors = bestprecursor(rxnup, m)
            val pathsback = precursors.map(bestpath(_, step_back))

            // return the combination all paths back, except if no precursors
            if (pathsback.isEmpty) new Path(Map()) else pathsback.reduce(_ ++ _)
          }

          // return path from m all the way back to natives
          path
        }
      }

      // add to cache
      cache_bestpath = cache_bestpath + (m -> path_built)

      // return this new path
      path_built
    }
  }

  def bestpath(m: Long, step: Int): Path = {
    // get the path with offset 0
    val path: Path = bestpath(m)

    // offset the path with `step`
    path.offsetBy(step)
  }

  def print_step(m: Long) {
    // set one of these to true if you need diagnostics
    val print_brief = false
    val print_detailed = false

    def detailed {
      println("\nPicking best path for molecule:" + m)
      println("IsNative || MarkedReachable: " + is_universal(m))
      println("IsCofactor: " + ActData.instance.cofactors.contains(m))
      println("Tree Depth: " + ActData.instance.ActTree.tree_depth.get(m))
    }
    def brief {
      println("Picking best path for molecule: " + m)
    }

    if (print_brief)
      brief
    else if (print_detailed)
      detailed
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
  def upNreach(r: ReachRxn) = r.isreachable && r.substrates.size>0 && Waterfall.higher_in_tree(t, r)

  def get_bestpath_foreach_uprxn(trgt: Long) = {
    for (r <- Waterfall.upR(trgt) if upNreach(r)) yield {

      // for each reachable rxn r that leads upwards
      // narrow down to r's substrates that make up the target
      val subs = Waterfall.bestprecursor(r, trgt)

      // then run backwards to natives for each of those substrates
      val mapping = r -> subs.map(Waterfall.bestpath)

      // return the mapping
      mapping
    }
  }

  def is_target_reachable = Waterfall.upR contains t

  // If the target isn't a reachable, we return an empty path.  Otherwise we look for the path
  val paths = if (!is_target_reachable) Map() else { Map() ++ get_bestpath_foreach_uprxn(target) }

  val allref_rxns = paths.flatMap({
    case (rxn_up, paths_up) => Set(rxn_up) ++ paths_up.foldLeft(Set[ReachRxn]())( (acc, p) => acc ++ p.rxnset )
  })

  def path2json(x: (ReachRxn, List[Path])) = x match {
    case (rxn_up, paths_up) => {
      // paths_up is list coz multiple substrates might be traced back to natives
      val path_opt = new JSONObject
      path_opt.put("rxn", rxn_up.rxnid)
      // the hyperedge may have many substrates so an AND array
      val hyperpath = new JSONArray
      for (s <- paths_up.map(_.json())) hyperpath.put(s)
      path_opt.put("best_path", hyperpath)
    }
  }

  val json_of_this = {
    val json = new JSONObject

    json.put("target", t)

    // each target has many options (OR) of paths back
    // for each entry within, there might be other arrays, but those
    // are for when a hyperedge has multiple substrates (AND)
    val fanin = new JSONArray
    val paths2json = paths.map(path2json)
    for (p <- paths2json) fanin.put(p)
    json.put("fan_in", fanin)

    val meta = new JSONArray
    for (r <- allref_rxns) meta.put(rxnmeta2json(r))
    json.put("rxn_meta", meta)

    json
  }

  private def rxnmeta2json(r: ReachRxn) = {
    val rm = new JSONObject
    rm.put("id", r.rxnid)
    rm.put("txt", ReachRxnDescs.rxnEasyDesc(r.rxnid))
    rm.put("seq", new JSONArray)
    rm.put("substrates", new JSONArray(r.substrates))
    rm.put("products", new JSONArray(r.products))
    rm
  }

  def json() = json_of_this

  override def toString = t + " --> " + paths
}
