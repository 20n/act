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

package com.act.biointerpretation.sarinference;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import chemaxon.struc.Molecule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Provides a nice interface over the results of a LibMCS clustering run.
 */
public class SarTree {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarTree.class);
  private static transient final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final Integer FIRST_ID = 1;
  private static final Boolean ALL_NODES = false; // Tell LibMCS to return all nodes in the tree in its clustering
  private static final String HIERARCHY_ID = "HierarchyID";

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

  /**
   * Builds this SAR tree by running LibraryMCS clustering on the given set of molecules.
   *
   * @param libMcs The clusterer.
   * @param molecules The molecules.
   * @throws InterruptedException
   */
  public void buildByClustering(LibraryMCS libMcs, Collection<Molecule> molecules) throws InterruptedException {
    if (molecules.isEmpty()) {
      LOGGER.error("Tried to build clustering on no molecules! Aborting call");
      // Log error but don't throw error. This could be a piece in a workflow where we try to build SARs on a prediction
      // corpus with no valid predictions. In that case we should not kill the workflow, but let it run and produce no
      // results.
      return;
    }

    for (Molecule mol : molecules) {
      libMcs.addMolecule(mol);
    }
    LOGGER.info("Beginning libMCS clustering.");
    libMcs.search();

    LibraryMCS.ClusterEnumerator enumerator = libMcs.getClusterEnumerator(ALL_NODES);
    this.buildFromEnumerator(enumerator);
  }

  /**
   * Builds this SAR tree based on the results of a LibMCS clustering.
   *
   * @param enumerator An enumerator for the LibMCS clusters.
   */
  private void buildFromEnumerator(LibraryMCS.ClusterEnumerator enumerator) {

    Molecule molecule;

    // Sometimes, even when enumerator.hasNext() returns true, enumerator.next() returns, not a null value, but
    // a NullPointerException. This might merit further investigation as it is totally bizarre.
    while (enumerator.hasNext()) {
      try {
        molecule = enumerator.next();
      } catch (NullPointerException e) {
        LOGGER.error("Null pointer exception thrown internally by enumerator.next() : %s", e.getMessage());
        // Log but don't throw error. This doesn't seem to indicate a real problem, but just a quirk of LibMCS.
        return;
      }

      try {
        this.addNode(getSarTreeNodeFromEnumeratorOutput(molecule));
      } catch (MolFormatException e) {
        // This error only seems to happen when the enumerator has in fact run out of valid molecules.
        // For that reason, we return instead of continuing or throwing an exception.
        LOGGER.info("Exception on getting SarTreeNode, stopping SarTree build: %s", e.getMessage());
        return;
      }
    }
  }

  /**
   * Builds a SarTreeNode from the corresponding molecule output from the enumerator. Reads the HierarchyID as well as
   * the prediction ID property from the molecule, if it exists.
   *
   * @param molecule A molecule spit out by the ClusterEnumerator.
   * @return A SarTreeNode.
   */
  public SarTreeNode getSarTreeNodeFromEnumeratorOutput(Molecule molecule) throws MolFormatException {
    if (molecule.getPropertyObject(HIERARCHY_ID) == null) {
      throw new MolFormatException("Bizarre error: Libmcs returned us a molecule with no hierarchy ID.");
    }

    String hierId = molecule.getPropertyObject(HIERARCHY_ID).toString();
    List<Integer> predictionIds = new ArrayList<>();

    // A molecule has a RecoveryID iff it's a leaf node, by LibraryMCS's API. Return with no prediction IDs,
    // as a higher level SAR node does not correspond to any particular predictions.
    if (molecule.getPropertyObject("RecoveryID") == null) {
      return new SarTreeNode(molecule, hierId, predictionIds);
    }

    // If it is a leaf node, it should always have the PREDICTION_ID_KEY property object- we added it earlier!
    // Thus we should add those prediction IDs to the SarTreeNode.
    Object propertyObject = molecule.getPropertyObject(SarTreeNode.PREDICTION_ID_KEY);
    if (propertyObject != null) {
      try {
        predictionIds = Arrays.asList(OBJECT_MAPPER.readValue(propertyObject.toString(), Integer[].class));
      } catch (IOException e) {
        LOGGER.info("Couldn't deserialize %s into list : %s", propertyObject, e.getMessage());
        throw new RuntimeException(e);
      }
    }

    return new SarTreeNode(molecule, hierId, predictionIds);
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

  /**
   * Pulls the SARs we care about out of a SarTree. These are chosen to be above a certain confidence level, and
   * explain at least a certain number of nodes. Additionally no SAR with less than one direct child is chosen:
   * no direct child indicates a leaf node, while one direct child means we can throw this node out and use its
   * child instead of it, to avoid duplicating SARs along a non-branching path.
   *
   * @param subtreeThreshold The min size of the subtree explained by a relevant SAR.
   * @param thresholdConfidence The min confidence score for a relevant SAR.
   * @return A list of SarTreeNodes
   */
  public SarTreeNodeList getExplanatoryNodes(int subtreeThreshold, double thresholdConfidence) {
    SarTreeNodeList results = new SarTreeNodeList();

    for (SarTreeNode node : getNodes()) {
      if (getChildren(node).size() > 1) {
        if (node.getPercentageHits() > thresholdConfidence && getSubtreeSize(node) >= subtreeThreshold) {
          results.addNode(node);
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

