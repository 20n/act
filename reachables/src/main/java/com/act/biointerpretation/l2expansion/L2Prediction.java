package com.act.biointerpretation.l2expansion;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Represents a single predicted reaction from the L2 expansion
 */
public class L2Prediction {

  @JsonProperty("_id")
  Integer id;

  @JsonProperty("substrate_inchis")
  List<String> substrateInchis;

  @JsonProperty("ro")
  Ero ro;

  @JsonProperty("product_inchis")
  List<String> productInchis;

  @JsonProperty("substrate_ids")
  Map<String, Long> substrateIds;

  @JsonProperty("product_ids")
  Map<String, Long> productIds;

  @JsonProperty("product_names")
  List<String> productNames;

  @JsonProperty("reactions_ro_match")
  List<Long> reactionsRoMatch;

  @JsonProperty("reactions_no_ro_match")
  List<Long> reactionsNoRoMatch;

  // Necessary for JSON reading
  private L2Prediction() {
  }

  public L2Prediction(Integer id, List<String> substrateInchis, Ero ro, List<String> productInchis) {
    this.id = id;
    this.substrateInchis = substrateInchis;
    this.ro = ro;
    this.productInchis = productInchis;
    this.reactionsRoMatch = new ArrayList<Long>();
    this.reactionsNoRoMatch = new ArrayList<Long>();
    this.substrateIds = new HashMap<>();
    this.productIds = new HashMap<>();
    this.productNames = new ArrayList<String>();
  }

  @JsonIgnore
  public int getReactionCount() {
    return reactionsRoMatch.size() + reactionsNoRoMatch.size();
  }

  @JsonIgnore
  public RxnMolecule getChemicalsRxnMolecule()
          throws MolFormatException {

    RxnMolecule renderedReactionMolecule = new RxnMolecule();

    // Add substrates and products to molecule.
    for (String substrate : substrateInchis) {
      renderedReactionMolecule.addComponent(MolImporter.importMol(substrate), RxnMolecule.REACTANTS);
    }
    for (String product : productInchis) {
      renderedReactionMolecule.addComponent(MolImporter.importMol(product), RxnMolecule.PRODUCTS);
    }

    // Calculate coordinates with a 2D coordinate system.
    Cleaner.clean(renderedReactionMolecule, 2, null);

    // Change the reaction arrow type.
    renderedReactionMolecule.setReactionArrowType(RxnMolecule.REGULAR_SINGLE);

    return renderedReactionMolecule;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public List<String> getSubstrateInchis() {
    return substrateInchis;
  }

  public Ero getRO() {
    return ro;
  }

  public List<String> getProductInchis() {
    return productInchis;
  }

  public List<Long> getReactionsRoMatch() {
    return reactionsRoMatch;
  }

  public void setReactionsRoMatch(List<Long> reactionsRoMatch) {
    this.reactionsRoMatch = reactionsRoMatch;
  }

  public List<Long> getReactionsNoRoMatch() {
    return reactionsNoRoMatch;
  }

  public void setReactionsNoRoMatch(List<Long> reactionsNoRoMatch) {
    this.reactionsNoRoMatch = reactionsNoRoMatch;
  }

  public Map<String, Long> getSubstrateIds() {
    return substrateIds;
  }

  public void addSubstrateId(String inchi, Long substrateId) {
    this.substrateIds.put(inchi, substrateId);
  }

  public Map<String, Long> getProductIds() {
    return productIds;
  }

  public void addProductId(String inchi, Long productId) {
    this.productIds.put(inchi, productId);
  }

  public boolean matchesRo() {
    return !reactionsRoMatch.isEmpty();
  }

  public List<String> getProductNames() {
    return productNames;
  }

  public void addProductName(String productName) {
    this.productNames.add(productName);
  }
}
