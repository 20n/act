package com.act.biointerpretation.sars.sartrees;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.Sar;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;

public class SarTreeNode {

  @JsonProperty
  String hierarchyId;

  Molecule substructure;

  @JsonProperty
  Double confidence;

  private SarTreeNode(){}

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

  @JsonIgnore
  public Molecule getSubstructure() {
    return substructure;
  }

  @JsonProperty
  public String getSubstructureInchi() throws IOException {
    return MolExporter.exportToFormat(substructure, "inchi:AuxNone");
  }

  public void setSubstructureInchi(String substructure) throws IOException {
    this.substructure = MolImporter.importMol(substructure, "inchi");
  }

  @JsonIgnore
  public Sar getSar() {
    return new OneSubstrateSubstructureSar(substructure);
  }

  public Double getConfidence() {
    return confidence;
  }
}
