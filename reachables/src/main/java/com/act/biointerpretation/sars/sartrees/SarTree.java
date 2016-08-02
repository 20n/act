package com.act.biointerpretation.sars.sartrees;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.Sar;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SarTree {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarTree.class);

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
    return getSubtreeNodes().stream().map(n -> n.getSubstructure()).collect(Collectors.toList());
  }

  public void scoreSars(Consumer<SarTreeNode> confidenceCalculator, Integer minSubtreeSize) throws IOException {
    Collection<SarTreeNode> nodesToProcess = getNodesAboveThresholdDescendants(minSubtreeSize);

    for (SarTreeNode node : nodesToProcess) {
      confidenceCalculator.accept(node);
      LOGGER.info("Num hits, misses: %d,%d", node.getNumberHits(), node.getNumberMisses());
    }
  }

  private List<SarTreeNode> getNodesAboveThresholdDescendants(Integer minSubtreeSize) {
    List<SarTreeNode> result = new ArrayList<>();

    for (SarTreeNode node : getSubtreeNodes()) {
      if (getSubtreeSize(node) >= minSubtreeSize) {
        result.add(node);
      }
    }

    return result;
  }

  public List<Pair<Sar, Double>> getScoredSars() {
    List<Pair<Sar, Double>> results = new ArrayList<>();

    for (SarTreeNode node : getSubtreeNodes()) {
      results.add(new ImmutablePair<>(node.getSar(), node.getPercentageHits()));
    }

    return results;
  }

  public List<SarTreeNode> getExplanatoryNodes(int subtreeThreshold, double thresholdConfidence) {
    Queue<SarTreeNode> nodes = new LinkedList<>(getRootNodes());
    List<SarTreeNode> sarResults = new ArrayList<>();

    while (!nodes.isEmpty()) {
      SarTreeNode nextNode = nodes.remove();
      if (nextNode.getPercentageHits() > thresholdConfidence && getSubtreeSize(nextNode) >= subtreeThreshold) {
        sarResults.add(nextNode);
      } //else {
        for (SarTreeNode childNode : getChildren(nextNode)) {
          nodes.add(childNode);
        //}
      }
    }

    return sarResults;
  }

  public Integer getSubtreeSize(SarTreeNode node) {
    if (getChildren(node).isEmpty()) {
      return 1;
    }

    int size = 0;
    for (SarTreeNode child : getChildren(node)) {
      size += getSubtreeSize(child);
    }
    return size;
  }

  public Collection<SarTreeNode> getSubtreeNodes() {
    List<SarTreeNode> nodes = new ArrayList<>();
    Stack<SarTreeNode> nodeStack = new Stack<>();
    nodeStack.push(new SarTreeNode(new Molecule(), "DUMMY"));

    for (SarTreeNode node : getRootNodes()) {
      nodes.addAll(getSubtreeNodes(node, nodeStack));
    }

    return nodes;
  }

  public Collection<SarTreeNode> getSubtreeNodes(SarTreeNode subtreeRoot) {
    Stack<SarTreeNode> nodeStack = new Stack<>();
    nodeStack.push(new SarTreeNode(new Molecule(), "DUMMY"));
    return getSubtreeNodes(subtreeRoot, nodeStack);
  }


  private Collection<SarTreeNode> getSubtreeNodes(SarTreeNode subtreeRoot, Stack<SarTreeNode> stack) {
    List<SarTreeNode> nodes = new ArrayList<>();

    SarTreeNode priorTop = stack.peek();

    stack.push(subtreeRoot);

    while (stack.peek() != priorTop) {
      SarTreeNode nextNode = stack.pop();
      nodes.add(nextNode);
      for (SarTreeNode childNode : getChildren(nextNode)) {
        nodes.addAll(getSubtreeNodes(childNode, stack));
      }
    }
    return nodes;
  }



  private String getChildHierarchyId(SarTreeNode node, int index) {
    return new StringBuilder(node.getHierarchyId())
        .append(".")
        .append(Integer.toString(index))
        .toString();
  }
}

