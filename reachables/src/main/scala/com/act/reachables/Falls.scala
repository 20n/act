package com.act.reachables

import scala.collection.JavaConversions._

trait Falls {

  // map to reachables -> rxns that have as product the reachable
  var upR = Map[Long, Set[ReachRxn]]()

  // terminal nodes (cofactors never show up in ReachRxn):
  // natives
  // markedReachables (list in MongoDB.java)
  var natives = List[Long]()
  var cofactors = List[Long]()

  def init(reachables: List[Long], upRxns: List[Set[ReachRxn]]) {
    upR = (reachables zip upRxns).toMap

    // We consider both the normal natives as well as products added because they had only cofactor
    // substrates as valid native sources.  This might cause interesting
    natives = ActData.instance.natives.map(Long.unbox(_)).toList
    cofactors = ActData.instance.cofactors.map(Long.unbox(_)).toList
  }

  def is_universal(m: Long) = natives.contains(m) || cofactors.contains(m)

  def has_substrates(r: ReachRxn) = ! r.substrates.isEmpty

  def higher_in_tree(mm: Long, r: ReachRxn) = {
    def tree_depth_of(a: Long): Int = ActData.instance.ActTree.tree_depth.get(a)
    val ss = cascades.get_set(r.substrates)
    val prod_tree_depth = tree_depth_of(mm)
    val substrate_tree_depths = ss.map(tree_depth_of)
    val max_substrate_tree_depth = substrate_tree_depths.reduce(math.max)
    max_substrate_tree_depth < prod_tree_depth
  }

}
