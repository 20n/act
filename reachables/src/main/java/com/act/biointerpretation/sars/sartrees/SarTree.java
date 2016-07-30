package com.act.biointerpretation.sars.sartrees;

import chemaxon.struc.Molecule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SarTree {

  private static final Integer FIRST_ID = 1;

  private Map<String, SarTreeNode> nodeMap;

  public SarTree() {
    nodeMap = new HashMap<>();
  }

  public void addNode(SarTreeNode node) {
    nodeMap.put(node.getHierarchyId(), node);
  }

  public List<SarTreeNode> getRootNodes() {
    SarTreeNode node;
    int clusterNumber = FIRST_ID;

    List<SarTreeNode> result = new ArrayList<>();
    while ((node = nodeMap.get(Integer.toString(clusterNumber))) != null) {
      result.add(node);
      clusterNumber++;
    }

    return result;
  }

  public List<SarTreeNode> getChildren(SarTreeNode node) {
    int childNumber = FIRST_ID;
    SarTreeNode child;
    List<SarTreeNode> result = new ArrayList<>();

    while ((child = nodeMap.get(getChildHierarchyId(node, childNumber))) != null) {
      result.add(child);
      childNumber++;
    }

    return result;
  }

  public List<Molecule> getAllSarSubstructures() {
    return nodeMap.values().stream().map(n -> n.getSubstructure()).collect(Collectors.toList());
  }

  public void scoreSars(Function<Molecule, Double> confidenceCalculator) {
    for (SarTreeNode node: nodeMap.values()) {
      node.setConfidence(confidenceCalculator.apply(node.getSubstructure()));
    }
  }

  public Collection<SarTreeNode> getNodes() {
    return nodeMap.values();
  }

  private String getChildHierarchyId(SarTreeNode node, int index) {
    return new StringBuilder(node.getHierarchyId())
        .append(".")
        .append(Integer.toString(index))
        .toString();
  }
}

