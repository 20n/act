package com.act.biointerpretation.sars.sartrees;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.function.Consumer;

public class SarTreeBasedCalculator implements Consumer<SarTreeNode> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarTreeBasedCalculator.class);
  Set<String> inchiSet;
  SarTree sarTree;

  public SarTreeBasedCalculator(Set<String> inchiSet, SarTree sarTree) {
    this.inchiSet = inchiSet;
    this.sarTree = sarTree;
  }

  @Override
  public void accept(SarTreeNode sarTreeNode) {
    int hits = 0;
    int misses = 0;

    for (SarTreeNode node : sarTree.traverseSubtree(sarTreeNode)) {
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
