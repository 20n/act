package com.act.reachables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Tree<T> {
  private HashMap<T, T> parents;
  private HashMap<T, Set<T>> children;
  private Set<T> roots;
  private Set<T> allNodes;
  public HashMap<T, Object> nodeAttributes;

  Tree(Set<T> roots, HashMap<T, T> parents, HashMap<T, Set<T>> children, HashMap<T, Object> nodeAttr) {
    this.parents = parents;
    this.roots = roots;
    this.children = children;
    this.nodeAttributes = nodeAttr;

    this.allNodes = new HashSet<T>();
    this.allNodes.addAll(this.roots);
    this.allNodes.addAll(parents.keySet());
  }

  Set<T> getChildren(T node) {
    return this.children.get(node);
  }

  public Set<T> roots() {
    return roots;
  }

  public Set<T> allNodes() {
    return allNodes;
  }

  public T getParent(T n) {
    return this.parents.get(n);
  }

  public void ensureForest() {
    // none of the roots should have parents
    for (T root : this.roots)
      if(this.parents.containsKey(root))
        new RuntimeException(String.format("Root %d has a parent %d\n", root, this.parents.get(root)));

    // for all nodes N that have children, its
    // children's parent pointer should be to N
    for (T parent : this.children.keySet()) {
      for (T child : this.children.get(parent))
        if (!parent.equals(this.parents.get(child)))
          new RuntimeException(String.format("Child %d has parent %d, but other parent %d claims it also owns child.\n", child, this.parents.get(child), parent));
    }
  }

}
