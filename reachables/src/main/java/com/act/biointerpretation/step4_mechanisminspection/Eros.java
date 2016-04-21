package com.act.biointerpretation.step4_mechanisminspection;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Eros {

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

  public Eros() {}

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
}
