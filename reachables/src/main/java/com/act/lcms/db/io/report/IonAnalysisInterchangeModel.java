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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class IonAnalysisInterchangeModel {
  @JsonProperty("results")
  private List<ResultForMZ> results;

  public IonAnalysisInterchangeModel() {
    results = new ArrayList<>();
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

  public static class ResultForMZ {
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    @JsonProperty("_id")
    private Long id;

    @JsonProperty("mass_charge")
    private Long mz;

    @JsonProperty("hits")
    private List<Hit> hits;

    // For deserialization.
    protected ResultForMZ() {

    }

    protected ResultForMZ(Long id, Long mz, List<Hit> hits) {
      this.id = id;
      this.mz = mz;
      this.hits = hits;
    }

    public ResultForMZ(Long mz, List<Hit> hits) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.hits = hits;
    }

    public ResultForMZ(Long mz) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.hits = new ArrayList<>();
    }

    public Long getId() {
      return id;
    }

    protected void setId(Long id) {
      this.id = id;
    }

    public Long getMz() {
      return mz;
    }

    protected void setMz(Long mz) {
      this.mz = mz;
    }

    public List<Hit> getHits() {
      return hits;
    }

    public void addHit(Hit hit) {
      this.hits.add(hit);
    }

    public void addHits(List<Hit> hits) {
      this.hits.addAll(hits);
    }

    protected void setHits(List<Hit> hits) {
      this.hits = new ArrayList<>(hits); // Copy to ensure sole ownership.
    }
  }

  public static class Hit {
    @JsonProperty("InChI")
    private String inchi;

    @JsonProperty("ion")
    private String ion;

    @JsonProperty("SNR")
    private Double SNR;

    @JsonProperty("time")
    private Double time;

    @JsonProperty("plot")
    private String plot;

    // For deserialization.
    protected Hit() {

    }

    public Hit(String inchi, String ion, Double SNR, Double time, String plot) {
      this.inchi = inchi;
      this.ion = ion;
      this.SNR = SNR;
      this.time = time;
      this.plot = plot;
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

    public String getPlot() {
      return plot;
    }

    protected void setPlot(String plot) {
      this.plot = plot;
    }
  }
}
