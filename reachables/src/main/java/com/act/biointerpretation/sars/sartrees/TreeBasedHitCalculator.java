package com.act.biointerpretation.sars.sartrees;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

public class TreeBasedHitCalculator implements Consumer<SarTreeNode> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(TreeBasedHitCalculator.class);
  Set<String> inchiSet;
  SarTree sarTree;

  public TreeBasedHitCalculator(Set<String> inchiSet, SarTree sarTree) {
    this.inchiSet = inchiSet;
    this.sarTree = sarTree;
  }

  @Override
  public void accept(SarTreeNode sarTreeNode) {
    int hits = 0;
    int misses = 0;

    for (SarTreeNode node : sarTree.getSubtreeNodes(sarTreeNode)) {
      // Only calculate on leaves
      if (sarTree.getChildren(node).isEmpty()) {
        if (node.getSubstructure().getPropertyObject("in_lcms").equals("true")) {
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
