/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
