package com.act.biointerpretation.sarinference;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Calculates a SARs hit percentage score by seeing which of the leaves of its subtree are LCMS hits,
 * and which are LCMS misses.
 */
public class SarTreeBasedCalculator implements Consumer<SarTreeNode> {

  SarTree sarTree;

  public SarTreeBasedCalculator(SarTree sarTree) {
    this.sarTree = sarTree;
  }

  @Override
  public void accept(SarTreeNode sarTreeNode) {
    int hits = 0;
    int misses = 0;

    for (SarTreeNode node : sarTree.traverseSubtree(sarTreeNode)) {
      // Only calculate on leaves
      if (sarTree.getChildren(node).isEmpty()) {
        if (node.getSubstructure().getPropertyObject(SarTreeNode.IN_LCMS_PROPERTY).equals(SarTreeNode.IN_LCMS_TRUE)) {
          hits++;
        } else {
          misses++;
        }
      }
    }

    sarTreeNode.setNumberHits(hits);
    sarTreeNode.setNumberMisses(misses);
  }
}
