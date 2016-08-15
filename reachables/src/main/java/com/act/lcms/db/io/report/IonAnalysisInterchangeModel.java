package com.act.lcms.db.io.report;

/**
 * This class represents the results of an Ion analyis without provenance information or supporting data for negative
 * results.  It should primarily be used to communicate positive LCMS findings with downstream modules.
 *
 * Example:
 * <pre>
  {
     "results" : [ {
      "_id" : 1,
      "mass_charge" : 331.13876999999997,
      "valid" : false,
      "molecules" : [ {
       "inchi" : "InChI=1S/C15H22O8/c1-20-7-11-12(17)13(18)14(19)15(23-11)22-6-8-3-4-9(16)10(5-8)21-2/h3-5,11-19H,6-7H2,1-2H3/t11-,12-,13+,14-,15-/m1/s1",
       "ion" : "M+H",
       "plot" : "331.13876999999997_37-1669-1670-_CHEM_6170.pdf",
       "snr" : 224.9610985335781,
       "time" : 208.54700088500977,
       "intensity" : 6954.61328125
      }]
     }]
 }
 </pre>
 */
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

  public void loadResultsFromFile(File inputFile) throws IOException {
    this.results = OBJECT_MAPPER.readValue(inputFile, IonAnalysisInterchangeModel.class).getResults();
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  public static Set<String> getAllMoleculeHitsFromMultiplePositiveReplicateFiles(List<String> filepaths,
                                                                                               Double snrThreshold,
                                                                                               Double intensityThreshold,
                                                                                               Double timeThreshold) throws IOException {


    List<IonAnalysisInterchangeModel> deserializedResultsForPositiveReplicates = new ArrayList<>();
    for (String filePath : filepaths) {
      IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
      model.loadResultsFromFile(new File(filePath));

      // Sort by mass charge for consistent comparisons across files
      Collections.sort(model.getResults(), new Comparator<ResultForMZ>() {
        @Override
        public int compare(ResultForMZ o1, ResultForMZ o2) {
          return o1.getMz().compareTo(o2.getMz());
        }
      });

      deserializedResultsForPositiveReplicates.add(model);
    }

    int totalNumberOfMassCharges = deserializedResultsForPositiveReplicates.get(0).getResults().size();

    Set<String> resultSet = new HashSet<>();

    for (int i = 0; i < totalNumberOfMassCharges; i++) {
      int totalNumberOfMoleculesInMassChargeResult =
          deserializedResultsForPositiveReplicates.get(0).getResults().get(i).getMolecules().size();

      for (int j = 0; j < totalNumberOfMoleculesInMassChargeResult; j++) {
        Boolean moleculePassedThresholdsForAllPositiveReplicates = true;

        for (int k = 0; k < deserializedResultsForPositiveReplicates.size(); k++) {
          HitOrMiss molecule = deserializedResultsForPositiveReplicates.get(k).getResults().get(i).getMolecules().get(j);

          if (molecule.getIntensity() < intensityThreshold ||
              molecule.getSnr() < snrThreshold ||
              molecule.getTime() < timeThreshold) {
           moleculePassedThresholdsForAllPositiveReplicates = false;
          }
        }

        if (moleculePassedThresholdsForAllPositiveReplicates) {
          HitOrMiss molecule = deserializedResultsForPositiveReplicates.get(0).getResults().get(i).getMolecules().get(j);
          resultSet.add(molecule.getInchi());
        }
      }
    }

    return resultSet;
  }

  public Set<String> getAllMoleculeHits(Double snrThreshold, Double intensityThreshold, Double timeThreshold) {
    Set<String> resultSet = new HashSet<>();
    for (ResultForMZ resultForMZ : results) {
      for (HitOrMiss hitOrMiss : resultForMZ.getMolecules()) {
        if (hitOrMiss.getIntensity() > intensityThreshold && hitOrMiss.getSnr() > snrThreshold &&
            hitOrMiss.getTime() > timeThreshold) {
          resultSet.add(hitOrMiss.getInchi());
        }
      }
    }
    return resultSet;
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

    // For deserialization.
    protected ResultForMZ() {

    }

    protected ResultForMZ(Long id, Double mz, List<HitOrMiss> molecules, Boolean hit) {
      this.id = id;
      this.mz = mz;
      this.molecules = molecules;
      this.isValid = hit;
    }

    public ResultForMZ(Double mz, List<HitOrMiss> molecules, Boolean hit) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.molecules = molecules;
      this.isValid = hit;
    }

    public ResultForMZ(Double mz) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.molecules = new ArrayList<>();
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

    public List<HitOrMiss> getMolecules() {
      return molecules;
    }

    protected void setMolecules(List<HitOrMiss> hits) {
      this.molecules = new ArrayList<>(hits); // Copy to ensure sole ownership.
    }

    @JsonIgnore
    public void addMolecule(HitOrMiss hit) {
      this.molecules.add(hit);
    }

    @JsonIgnore
    public void addMolecules(List<HitOrMiss> hits) {
      this.molecules.addAll(hits);
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

    @JsonProperty("plot")
    private String plot;

    @JsonProperty("snr")
    private Double snr;

    @JsonProperty("time")
    private Double time;

    @JsonProperty("intensity")
    private Double intensity;

    // For deserialization.
    protected HitOrMiss() {

    }

    public HitOrMiss(String inchi, String ion, Double snr, Double time, Double intensity, String plot) {
      this.inchi = inchi;
      this.ion = ion;
      this.snr = snr;
      this.time = time;
      this.intensity = intensity;
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

    public Double getSnr() {
      return snr;
    }

    protected void setSnr(Double snr) {
      this.snr = snr;
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

    public String getPlot() {
      return plot;
    }

    public void setPlot(String plot) {
      this.plot = plot;
    }
  }
}
