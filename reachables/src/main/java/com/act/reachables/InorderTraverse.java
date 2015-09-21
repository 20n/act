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
