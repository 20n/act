package com.act.lcms.db.analysis;

import com.act.biointerpretation.l2expansion.L2PredictionChemical;
import com.act.biointerpretation.l2expansion.L2PredictionRo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class LCMSMoleculeHit {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("_id")
  Integer id;

  @JsonProperty("ro")
  L2PredictionRo ro;

  @JsonProperty("products")
  List<L2PredictionChemical> products;

  // Necessary for JSON reading
  private LCMSMoleculeHit() {}

  public LCMSMoleculeHit(LCMSMoleculeHit template) {
    this.id = template.id;

    this.ro = new L2PredictionRo(template.ro);

    this.products = new ArrayList<>(template.products.size());
    for (L2PredictionChemical product : template.products) {
      this.products.add(new L2PredictionChemical(product));
    }
  }

  public LCMSMoleculeHit(Integer id, L2PredictionRo ro, List<L2PredictionChemical> products) {
    this.id = id;
    this.products = products;
    this.ro = ro;
  }

  @JsonIgnore
  public List<String> getProductInchis() {
    List<String> inchis = new ArrayList<>();
    for (L2PredictionChemical chemical : getProducts()) {
      inchis.add(chemical.getInchi());
    }
    return inchis;
  }

  @JsonIgnore
  public List<Long> getProductIds() {
    List<Long> ids = new ArrayList<>();
    for (L2PredictionChemical chemical : getProducts()) {
      if (chemical.hasId()) {
        ids.add(chemical.getId());
      }
    }
    return ids;
  }

  @JsonIgnore
  public List<String> getProductNames() {
    List<String> names = new ArrayList<>();
    for (L2PredictionChemical chemical : getProducts()) {
      if (chemical.hasName()) {
        names.add(chemical.getName());
      }
    }
    return names;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public List<L2PredictionChemical> getProducts() {
    return products;
  }

  public void setProducts(List<L2PredictionChemical> products) {
    this.products = products;
  }

  public L2PredictionRo getRo() {
    return ro;
  }
}
