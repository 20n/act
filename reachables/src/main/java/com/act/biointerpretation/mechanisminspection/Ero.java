package com.act.biointerpretation.mechanisminspection;

import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;

public class Ero implements Serializable {
  private static final long serialVersionUID = 5679370596050943302L;
  private static final Logger LOGGER = LogManager.getLogger(Ero.class);

  @JsonProperty("id")
  private Integer id;

  @JsonProperty("category")
  private String category;

  @JsonProperty("is_trim")
  private Boolean is_trim = false;

  @JsonProperty("name")
  private String name = "";

  @JsonProperty("ro")
  private String ro;

  @JsonProperty("autotrim")
  private Boolean autotrim;

  @JsonProperty("note")
  private String note = "";

  @JsonProperty("confidence")
  private String confidence = "";

  @JsonProperty("manual_validation")
  private Boolean manual_validation = false;

  @JsonProperty("substrate_count")
  private Integer substrate_count;

  @JsonProperty("product_count")
  private Integer product_count;

  @JsonIgnore
  private transient ThreadLocal<Reactor> reactor = new ThreadLocal<Reactor>() {
    @Override
    protected Reactor initialValue() {
      Reactor r = new Reactor();
      try {
        r.setReactionString(getRo());
        return r;
      } catch (ReactionException e) {
        LOGGER.warn(String.format("Unable to set reaction string %s for RO %d.  " +
                "Please verify validity of the reaction string for this RO.", getRo(), getId()));
        return null;
      }
    }
  };

  public Ero() {}

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Boolean getIs_trim() {
    return is_trim;
  }

  public void setIs_trim(Boolean is_trim) {
    this.is_trim = is_trim;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getRo() {
    return ro;
  }

  public void setRo(String ro) {
    this.ro = ro;
  }

  public Boolean getAutotrim() {
    return autotrim;
  }

  public void setAutotrim(Boolean autotrim) {
    this.autotrim = autotrim;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public String getConfidence() {
    return confidence;
  }

  public void setConfidence(String confidence) {
    this.confidence = confidence;
  }

  public Boolean getManual_validation() {
    return manual_validation;
  }

  public void setManual_validation(Boolean manual_validation) {
    this.manual_validation = manual_validation;
  }

  public Integer getSubstrate_count() {
    return substrate_count;
  }

  public void setSubstrate_count(Integer substrate_count) {
    this.substrate_count = substrate_count;
  }

  public Integer getProduct_count() {
    return product_count;
  }

  public void setProduct_count(Integer product_count) {
    this.product_count = product_count;
  }

  @JsonIgnore
  public Reactor getReactor() throws ReactionException {
    // We don't cache the reactor every time as by caching
    // it we disallow concurrency operations on a single RO.
    // We also don't want to reconstruct it each time as this is an expensive operation.
    // Therefore, we only reconstruct it for each thread.
    return reactor.get();
  }
}
