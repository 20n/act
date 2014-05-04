package com.act.reachables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

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
		Set<Double> childVals = new HashSet<Double>();
		if (tree.getChildren(node) != null)
			for (T child : tree.getChildren(node)) {
				execute(child, inVal, outVal);
				childVals.add(outVal.get(child));
			}
		outVal.put(node, nodeValue(inVal.get(node), childVals));
	}

	abstract Double nodeValue(Double initVal, Set<Double> childrenVals);
}
