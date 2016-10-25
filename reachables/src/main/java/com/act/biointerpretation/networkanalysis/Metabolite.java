package com.act.biointerpretation.networkanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a metabolite in the metabolite network.
 * For now this class only stores an inchi, but in the future it can represent multiple levels of abstraction: a
 * structure in any format, a chemical formula, or only a mass.
 */
public class Metabolite {
  private static AtomicInteger uuidAssigner = new AtomicInteger();

  @JsonProperty("inchi")
  private String inchi;
  @JsonProperty("mass")
  private Double mass;
  @JsonProperty("uuid")
  private Long uuid = (long) uuidAssigner.incrementAndGet();

  @JsonCreator
  public Metabolite(Double mass, String inchi) {
    this.mass = mass;
    this.inchi = inchi;
  }

  @JsonCreator
  public Metabolite(String inchi) {
    this.mass = null;
    this.inchi = inchi;
  }



  @JsonCreator
  public Metabolite(Double mass){
    this(mass, null);
  }

  public String getInchi() {
    return inchi;
  }

  public Long getUUID() {
    return uuid;
  }

  public Double getMass() {
    return mass;
  }


}
