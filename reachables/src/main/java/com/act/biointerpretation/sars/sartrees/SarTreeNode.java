package com.act.biointerpretation.sars.sartrees;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.Sar;

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

  public Sar getSar() {
    return new OneSubstrateSubstructureSar(substructure);
  }

  public Double getConfidence() {
    return confidence;
  }
}
