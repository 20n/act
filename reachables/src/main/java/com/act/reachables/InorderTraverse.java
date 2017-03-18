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

package com.act.reachables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public abstract class InorderTraverse<T> {
  Tree<T> tree;

  public InorderTraverse() {}

  public InorderTraverse(Tree<T> tree) {
    this.tree = tree;
  }

public void exec(T startNode, HashMap<T, Double> inVal, HashMap<T, Double> outVal) {
  execute(startNode, inVal, outVal);
}

// takes the input values in nodeValues, computes using the function nodeValue and outputs to
public void execute(T node, HashMap<T, Double> inVal, HashMap<T, Double> outVal) {
  List<Double> childVals = new ArrayList<Double>();
  if (tree.getChildren(node) != null) {
    for (T child : tree.getChildren(node)) {
      execute(child, inVal, outVal);
      childVals.add(outVal.get(child));
    }
  }
  Double nodeVal = nodeValue(inVal.get(node), childVals);
  outVal.put(node, nodeVal);
}

  abstract Double nodeValue(Double initVal, List<Double> childrenVals);
}
