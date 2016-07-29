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

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof IonAnalysisInterchangeModel)) return false;
    IonAnalysisInterchangeModel that = (IonAnalysisInterchangeModel) o;

    for (ResultForMZ res : this.getResults()) {
      for (ResultForMZ res2 : that.getResults()) {
        if (!res.getHitSet().equals(res2.getHitSet()) || !res.getMissSet().equals(res2.getMissSet())) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hash = "magic".hashCode();
    if (this.getResults() != null) hash ^= this.getResults().hashCode();
    return hash;
  }

  public static class ResultForMZ {
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    @JsonProperty("_id")
    private Long id;

    @JsonProperty("mass_charge")
    private Double mz;

    @JsonProperty("hits")
    private List<HitOrMiss> hits;

    @JsonProperty("misses")
    private List<HitOrMiss> misses;

    @JsonProperty("plot")
    private String plot;

    // For deserialization.
    protected ResultForMZ() {

    }

    protected ResultForMZ(Long id, Double mz, List<HitOrMiss> hits, List<HitOrMiss> misses, String plot) {
      this.id = id;
      this.mz = mz;
      this.hits = hits;
      this.misses = misses;
      this.plot = plot;
    }

    public ResultForMZ(Double mz, List<HitOrMiss> hits, List<HitOrMiss> misses, String plot) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.hits = hits;
      this.misses = misses;
      this.plot = plot;
    }

    public ResultForMZ(Double mz) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.hits = new ArrayList<>();
      this.misses = new ArrayList<>();
      this.plot = "";
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
      return hits;
    }

    public void addHit(HitOrMiss hit) {
      this.hits.add(hit);
    }

    public void addHits(List<HitOrMiss> hits) {
      this.hits.addAll(hits);
    }

    public List<HitOrMiss> getMisses() {
      return misses;
    }

    public void addMiss(HitOrMiss miss) {
      this.misses.add(miss);
    }

    public void addMisses(List<HitOrMiss> misses) {
      this.misses.addAll(misses);
    }

    protected void setHits(List<HitOrMiss> hits) {
      this.hits = new ArrayList<>(hits); // Copy to ensure sole ownership.
    }

    protected void setMisses(List<HitOrMiss> misses) {
      this.misses = new ArrayList<>(misses); // Copy to ensure sole ownership.
    }

    public Set<HitOrMiss> getHitSet() {
      return new HashSet<>(this.getHits());
    }

    public Set<HitOrMiss> getMissSet() {
      return new HashSet<>(this.getMisses());
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

    @Override
    public int hashCode() {
      int hash = "magic".hashCode();
      if (this.getInchi() != null) hash ^= this.getInchi().hashCode();
      if (this.getIon() != null) hash ^= this.getIon().hashCode();
      if (this.getSNR() != null) hash ^= this.getSNR().hashCode();
      if (this.getTime() != null) hash ^= this.getTime().hashCode();
      if (this.getIntensity() != null) hash ^= this.getIntensity().hashCode();
      return hash;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof HitOrMiss)) return false;
      HitOrMiss that = (HitOrMiss) o;

      return this.getInchi().equals(that.getInchi()) &&
          this.getIntensity().equals(that.getIntensity()) &&
          this.getIon().equals(that.getIon()) &&
          this.getSNR().equals(that.getSNR()) &&
          this.getTime().equals(that.getTime());
    }

  }
}
