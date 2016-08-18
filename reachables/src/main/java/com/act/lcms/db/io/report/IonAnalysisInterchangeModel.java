package com.act.lcms.db.io.report;

/**
 * This class represents the results of an Ion analyis without provenance information or supporting data for negative
 * results.  It should primarily be used to communicate positive LCMS findings with downstream modules.
 *
 * Example:
 * <pre>
  {
    "results" : [ {
      "_id" : 0,
      "mass_charge": 10.0,
      "hits" : [
      {
        "inchi" : "InChI=1S/C5H6O3/c1-3-5(7)4(6)2-8-3/h7H,2H2,1H3",
        "ion" : "M+H",
        "SNR" : 10.1,
        "time" : 15.2
      },
      {
        "inchi" : "InChI=1S/C6H6O3/c1-3-5(7)4(6)2-8-3/h7H,2H2,1H3",
        "ion" : "M+Na",
       "SNR" : 11,
        "time" : 135.2
      }]
    }]
  }
 </pre>
 */
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class IonAnalysisInterchangeModel {
  @JsonProperty("results")
  private List<ResultForMZ> results;

  public IonAnalysisInterchangeModel() {
    results = new ArrayList<>();
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public IonAnalysisInterchangeModel(List<ResultForMZ> results) {
    this.results = results;
  }

  public List<ResultForMZ> getResults() {
    return results;
  }

  protected void setResults(List<ResultForMZ> results) {
    this.results = results;
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  public static class ResultForMZ {
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    @JsonProperty("_id")
    private Long id;

    @JsonProperty("mass_charge")
    private Double mz;

    @JsonProperty("valid")
    private Boolean isValid;

    @JsonProperty("molecules")
    private List<HitOrMiss> molecules;

    @JsonProperty("plot")
    private String plot;

    // For deserialization.
    protected ResultForMZ() {

    }

    protected ResultForMZ(Long id, Double mz, List<HitOrMiss> molecules, String plot, Boolean hit) {
      this.id = id;
      this.mz = mz;
      this.molecules = molecules;
      this.plot = plot;
      this.isValid = hit;
    }

    public ResultForMZ(Double mz, List<HitOrMiss> molecules, String plot, Boolean hit) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.molecules = molecules;
      this.plot = plot;
      this.isValid = hit;
    }

    public ResultForMZ(Double mz) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.molecules = new ArrayList<>();
      this.plot = "";
      this.isValid = false;
    }

    public Long getId() {
      return id;
    }

    protected void setId(Long id) {
      this.id = id;
    }

    public Double getMz() {
      return mz;
    }

    protected void setMz(Double mz) {
      this.mz = mz;
    }

    public String getPlot() {
      return plot;
    }

    public void setPlot(String plot) {
      this.plot = plot;
    }

    public List<HitOrMiss> getHits() {
      return molecules;
    }

    public void addHit(HitOrMiss hit) {
      this.molecules.add(hit);
    }

    public void addHits(List<HitOrMiss> hits) {
      this.molecules.addAll(hits);
    }

    protected void setHits(List<HitOrMiss> hits) {
      this.molecules = new ArrayList<>(hits); // Copy to ensure sole ownership.
    }

    public Set<HitOrMiss> getHitSet() {
      return new HashSet<>(this.getHits());
    }

    public Boolean getIsValid() {
      return isValid;
    }

    public void setIsValid(Boolean hit) {
      isValid = hit;
    }
  }

  public static class HitOrMiss {
    @JsonProperty("inchi")
    private String inchi;

    @JsonProperty("ion")
    private String ion;

    @JsonProperty("SNR")
    private Double SNR;

    @JsonProperty("time")
    private Double time;

    @JsonProperty("intensity")
    private Double intensity;

    // For deserialization.
    protected HitOrMiss() {

    }

    public HitOrMiss(String inchi, String ion, Double SNR, Double time, Double intensity) {
      this.inchi = inchi;
      this.ion = ion;
      this.SNR = SNR;
      this.time = time;
      this.intensity = intensity;
    }

    public String getInchi() {
      return inchi;
    }

    protected void setInchi(String inchi) {
      this.inchi = inchi;
    }

    public String getIon() {
      return ion;
    }

    protected void setIon(String ion) {
      this.ion = ion;
    }

    public Double getSNR() {
      return SNR;
    }

    protected void setSNR(Double SNR) {
      this.SNR = SNR;
    }

    public Double getTime() {
      return time;
    }

    protected void setTime(Double time) {
      this.time = time;
    }

    public Double getIntensity() {
      return intensity;
    }

    protected void setIntensity(Double intensity) {
      this.intensity = intensity;
    }
  }
}
