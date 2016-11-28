package com.act.reachables

import org.json.{JSONArray, JSONObject}

class Path(val rxns: Map[Int, Set[ReachRxn]]) {
  // a hypergraph path: The transformations are listed out in step order
  // there might be multiple rxns at a step because at a previous step
  // a hyperedge might exist that requires two or more precursors

  def this(step: Int, r: ReachRxn) = this(Map(step -> Set(r)))

  def ++(other: Path) = new Path(cascades.mergeMaps(other.rxns, this.rxns))

  def rxnset() = rxns.foldLeft(Set[ReachRxn]())( (acc, t) => acc ++ t._2 )

  def offsetBy(steps: Int) = new Path(rxns.map{ case (k, v) => (k + steps, v) })

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
