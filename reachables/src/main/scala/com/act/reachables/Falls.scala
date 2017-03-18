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

import scala.collection.JavaConversions._

trait Falls {

  // map to reachables -> rxns that have as product the reachable
  var upR = Map[Long, Set[ReachRxn]]()

  // terminal nodeMapping (cofactors never show up in ReachRxn):
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

  def higher_in_tree(mm: Long, r: ReachRxn, extraDepth: Int = 0) = {
    def tree_depth_of(a: Long): Int = ActData.instance.ActTree.tree_depth.get(a)
    val ss = cascades.get_set(r.substrates)
    val prod_tree_depth = tree_depth_of(mm)
    val substrate_tree_depths = ss.map(tree_depth_of)
    val max_substrate_tree_depth = substrate_tree_depths.max
    max_substrate_tree_depth < (prod_tree_depth + extraDepth)
  }

}
