package com.act.biointerpretation.sars.sartrees;

import com.act.biointerpretation.sars.Sar;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SarTree {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarTree.class);

  private static final Integer FIRST_ID = 1;

  private Map<String, SarTreeNode> nodeMap;

  public SarTree() {
    nodeMap = new HashMap<>();
  }

  public Collection<SarTreeNode> getNodes() {
    return nodeMap.values();
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

  public void applyToNodes(Consumer<SarTreeNode> consumer, Integer minSubtreeSize) throws IOException {
    Collection<SarTreeNode> nodesToProcess = getNodesAboveThresholdDescendants(minSubtreeSize);
    LOGGER.info("%d sars to score.", nodesToProcess.size());
    nodesToProcess.forEach(consumer);
  }

  private List<SarTreeNode> getNodesAboveThresholdDescendants(Integer minSubtreeSize) {
    List<SarTreeNode> result = new ArrayList<>();

    for (SarTreeNode node : getNodes()) {
      if (getSubtreeSize(node) >= minSubtreeSize) {
        result.add(node);
      }
    }

    return result;
  }

  public List<SarTreeNode> getExplanatoryNodes(int subtreeThreshold, double thresholdConfidence) {
    List<SarTreeNode> results = new ArrayList<>();

    for (SarTreeNode node : getNodes()) {
      if (getChildren(node).size() > 1) {
        if (node.getPercentageHits() > thresholdConfidence && getSubtreeSize(node) >= subtreeThreshold) {
          results.add(node);
        }
      }
    }

    return results;
  }

  public Integer getSubtreeSize(SarTreeNode node) {
    return traverseSubtree(node).size();
  }

  /**
   * Depth first traversal of the subtree with a given root.
   *
   * @param subtreeRoot The root.
   * @return The list of SarTreeNodes in the given subtree.
   */
  public List<SarTreeNode> traverseSubtree(SarTreeNode subtreeRoot) {
    Stack<SarTreeNode> nodeStack = new Stack<>();
    return traverseSubtree(subtreeRoot, nodeStack);
  }

  /**
   * Utility function to implement a depth first traversal.
   * test().
   *
   * @param subtreeRoot The root of the subtree to search.
   * @param stack A stack to use to facilitate the search.
   * @return
   */
  private List<SarTreeNode> traverseSubtree(SarTreeNode subtreeRoot,
                                            Stack<SarTreeNode> stack) {
    List<SarTreeNode> nodes = new ArrayList<>();
    Integer initialSize = stack.size();
    stack.push(subtreeRoot);

    while (stack.size() > initialSize) {
      SarTreeNode nextNode = stack.pop();
      nodes.add(nextNode);
      for (SarTreeNode childNode : getChildren(nextNode)) {
        nodes.addAll(traverseSubtree(childNode, stack));
      }
    }
    return nodes;
  }

  /**
   * Gets the hierarchyId of the child of node with a particular index.
   *
   * @param node The parent node.
   * @param index A child index. Children are numbered from 1.
   * @return The child's hierarchy ID.
   */
  private String getChildHierarchyId(SarTreeNode node, int index) {
    if (index == 0) {
      throw new IllegalArgumentException("HierarchyIDs only use positive integer indices.");
    }
    return new StringBuilder(node.getHierarchyId())
        .append(".")
        .append(Integer.toString(index))
        .toString();
  }
}

