package com.act.biointerpretation.sarinference;

import java.util.function.Consumer;

/**
 * Calculates a SARs hit percentage score by seeing which of the leaves of its subtree are LCMS hits,
 * and which are LCMS misses. This is not a perfect scoring across an entire prediction corpus; it is essentially
 * a much faster, heuristic approach to simulating what SarHitPercentageCalculator does.  The key assumption here is
 * that we don't have to look outside a given SAR's subtree when we're trying to assign it a score, since there is
 * probably almost nothing outside its subtree (in the clustering hierarchy) which will match it anyway.  However,
 * as LibMCS clustering is not always perfect, there may in fact be molecules that match a SAR, which do not get
 * clustered under that SAR's subtree.  In such cases, this heuristic score diverges from the full score calculated
 * across the entire corpus.
 */
public class SarTreeBasedCalculator implements Consumer<SarTreeNode> {

  SarTree sarTree;

  public SarTreeBasedCalculator(SarTree sarTree) {
    this.sarTree = sarTree;
  }

  /**
   * Set the hits and missess on the given sarTreeNode based on which of the leaves in its subtree are hits,
   * and which are misses.
   *
   * @param sarTreeNode The node to score.
   */
  @Override
  public void accept(SarTreeNode sarTreeNode) {
    int hits = 0;
    int misses = 0;

    for (SarTreeNode node : sarTree.traverseSubtree(sarTreeNode)) {
      // Only calculate on leaves
      if (sarTree.getChildren(node).isEmpty()) {
        if (node.getLcmsProperty().equals(SarTreeNode.IN_LCMS_TRUE)) {
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
