package com.act.biointerpretation.sars.sartrees;

import chemaxon.struc.Molecule;

public class SarTreeNode {
  final String hierarchyId;
  final Molecule substructure;
  Double confidence;

  public SarTreeNode(Molecule substructure, String hierarchyId) {
    this.substructure = substructure;
    this.hierarchyId = hierarchyId;
    this.confidence = 0D;
  }

  public void setConfidence(Double confidence) {
    this.confidence = confidence;
  }

  public String getHierarchyId() {
    return hierarchyId;
  }

  public Molecule getSubstructure() {
    return substructure;
  }

  public Double getConfidence() {
    return confidence;
  }
}
