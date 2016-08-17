package com.act.biointerpretation.sarinference;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.Sar;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A single node in a SarTree, which corresponds to a substructure pulled out by LibMCS clustering.
 */
public class SarTreeNode {

  public static final String PREDICTION_ID_KEY = "prediction_id";

  // TODO: this enum should live in the LCMS module once Vijay and Gil merge their pieces
  // The idea of NO_DATA is to indicate if we query on a molecule who's mass no analysis was done on, to distinguish
  // this case from an actual calculated MISS.
  public enum LCMS_RESULT {
    HIT,
    MISS,
    NO_DATA
  }


  @JsonProperty("hierarchy_id")
  String hierarchyId;

  Molecule substructure;

  @JsonProperty("number_misses")
  Integer numberMisses;

  @JsonProperty("number_hits")
  Integer numberHits;

  @JsonProperty("prediction_ids")
  List<Integer> predictionIds;

  @JsonProperty
  Double rankingScore;

  private SarTreeNode() {
  }

  public SarTreeNode(Molecule substructure, String hierarchyId, List<Integer> predictionIds) {
    this.substructure = substructure;
    this.hierarchyId = hierarchyId;
    this.predictionIds = predictionIds;
    this.numberMisses = 0;
    this.numberHits = 0;
    this.rankingScore = 0D;
  }

  public void setNumberMisses(Integer numberMisses) {
    this.numberMisses = numberMisses;
  }

  public void setNumberHits(Integer numberHits) {
    this.numberHits = numberHits;
  }

  public Integer getNumberHits() {
    return numberHits;
  }

  public Integer getNumberMisses() {
    return numberMisses;
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
    return MolExporter.exportToFormat(substructure, "inchi:AuxNone,Woff");
  }

  public void setSubstructureInchi(String substructure) throws IOException {
    this.substructure = MolImporter.importMol(substructure, "inchi");
  }

  @JsonIgnore
  public Sar getSar() {
    return new OneSubstrateSubstructureSar(substructure);
  }

  @JsonIgnore
  public Double getPercentageHits() {
    return new Double(numberHits) / new Double(numberHits + numberMisses);
  }

  public List<Integer> getPredictionIds() {
    return predictionIds;
  }

  public void setPredictionId(List<Integer> predictionIds) {
    this.predictionIds = new ArrayList<>(predictionIds);
  }

  public Double getRankingScore() {
    return rankingScore;
  }

  public void setRankingScore(Double rankingScore) {
    this.rankingScore = rankingScore;
  }
}
