package com.act.biointerpretation.sars.sartrees;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.Sar;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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

  public void scoreSars(Function<Sar, Double> confidenceCalculator) {
    for (SarTreeNode node: nodeMap.values()) {
      node.setConfidence(confidenceCalculator.apply(node.getSar()));
    }
  }

  public List<Pair<Sar, Double>> getScoredSars() {
    List<Pair<Sar, Double>> results = new ArrayList<>();

    for (SarTreeNode node : getNodes()) {
      results.add(new ImmutablePair<>(node.getSar(), node.getConfidence()));
    }

    return results;
  }

  public List<Sar> getExplanatorySars(double thresholdConfidence) {
    Queue<SarTreeNode> nodes = new LinkedList<>(getRootNodes());
    List<Sar> sarResults = new ArrayList<>();

    while (!nodes.isEmpty()) {
      SarTreeNode nextNode = nodes.remove();
      if (nextNode.getConfidence() > thresholdConfidence) {
        sarResults.add(nextNode.getSar());
      } else {
        for (SarTreeNode childNode : getChildren(nextNode)) {
          nodes.add(childNode);
        }
      }
    }

    return sarResults;
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

