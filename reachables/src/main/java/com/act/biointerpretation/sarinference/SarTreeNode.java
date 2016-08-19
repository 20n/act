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
    // Don't include Aux info and don't log warnings
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

  public void setPredictionIds(List<Integer> predictionIds) {
    this.predictionIds = new ArrayList<>(predictionIds);
  }

  public Double getRankingScore() {
    return rankingScore;
  }

  @JsonIgnore
  public void setRankingScore(ScoringFunctions function) {
    this.setRankingScore(function.calculateScore(this));
  }

  public void setRankingScore(Double rankingScore) {
    this.rankingScore = rankingScore;
  }

  public enum ScoringFunctions {
    HIT_MINUS_MISS {
      @Override
      public Double calculateScore(SarTreeNode node) {
        return new Double(node.getNumberHits() - node.getNumberMisses());
      }
    },

    HIT_PERCENTAGE {
      @Override
      public Double calculateScore(SarTreeNode node) {
        return new Double(node.getNumberHits()) / (node.getNumberHits() + node.getNumberMisses());
      }
    },

    NORM_HITS {
      @Override
      public Double calculateScore(SarTreeNode node) {
        Double hitPercentage = new Double(node.getNumberHits()) / node.getNumberMisses();
        return node.getNumberHits() * hitPercentage;
      }
    };

    public abstract Double calculateScore(SarTreeNode node);
  }

}
