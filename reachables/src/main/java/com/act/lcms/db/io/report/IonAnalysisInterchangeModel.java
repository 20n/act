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

import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.sarinference.SarTreeNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class IonAnalysisInterchangeModel {

  // An LCMS result.
  // The idea of NO_DATA is to indicate if we query on a molecule with a mass on which no analysis was done on, to
  // distinguish this case from an actual calculated MISS.
  public enum LCMS_RESULT {
    HIT,
    MISS,
    NO_DATA
  }

  @JsonProperty("results")
  private List<ResultForMZ> results;
  private Map<String, Boolean> inchiToIsHit;

  public IonAnalysisInterchangeModel() {
    results = new ArrayList<>();
    inchiToIsHit = new HashMap<>();
  }

  public void loadResultsFromFile(File inputFile) throws IOException {
    this.results = OBJECT_MAPPER.readValue(inputFile, IonAnalysisInterchangeModel.class).getResults();
    this.populateInchiToIsHit();
  }

  /**
   * Populates a map from all the inchis analyzed in the corpus to true if they are and LCMS hit, or false if not.
   */
  private void populateInchiToIsHit() {
    this.inchiToIsHit = new HashMap<>();

    for (ResultForMZ resultForMZ : results) {
      Boolean isHit = resultForMZ.isValid;
      for (HitOrMiss molecule : resultForMZ.getMolecules()) {

        // If the inchi is already a hit, then we do not want to override
        // it with a possible miss result.
        if (this.inchiToIsHit.get(molecule.getInchi()) == null ||
            !this.inchiToIsHit.get(molecule.getInchi())) {
          this.inchiToIsHit.put(molecule.getInchi(), isHit);
        }
      }
    }
  }

  public void writeToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(predictionWriter, this);
    }
  }

  /**
   * Returns HIT or MISS if the inchi is in the precalculated inchi->hit map, or NO_DATA if the inchi is not.
   * This will let us know if there has been any change in the inchi's form since the initial calculation, instead of
   * just silentlyl returning a miss.
   *
   * @param inchi The inchi of the molecule.
   * @return The LCMS result.
   */
  public LCMS_RESULT isMoleculeAHit(String inchi) {
    if (this.inchiToIsHit.get(inchi) == null) {
      return LCMS_RESULT.NO_DATA;
    }
    return this.inchiToIsHit.get(inchi) ? LCMS_RESULT.HIT : LCMS_RESULT.MISS;
  }

  /**
   * Calculate whether a given prediction is an LCMS hit or not.
   *
   * TODO: think through our general approach to multiple substrate reactions when necessary.
   * We'll need to balance the possibilities of false positives and false negatives- one idea would be to return
   * a score based on the number of confirmed products of the reaction.
   *
   * @param prediction The prediction from the corpus.
   * @return True if all products are LCMS hits.
   */
  public LCMS_RESULT getLcmsDataForPrediction(L2Prediction prediction) {
    List<String> productInchis = prediction.getProductInchis();
    for (String product : productInchis) {
      // If any of the results have no data, return NO_DATA. Such results shouldn't happen for now, so the caller will
      // likely throw an exception if this happens.
      if (this.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.NO_DATA)) {
        return LCMS_RESULT.NO_DATA;
      }
      // Otherwise, if a miss is found among the prediction's products, return it as a miss.  This implements an
      // AND among the products of the prediction- all must be present to register as a hit. This is motivated by the
      // fact that our only current multiple-product reaction produces one significant product, and one constant
      // cofactor. We verified that in both urine and saliva, the cofactor is present in our samples, so
      // an OR approach here would return a HIT for every prediction of that RO.
      if (this.isMoleculeAHit(product).equals(IonAnalysisInterchangeModel.LCMS_RESULT.MISS)) {
        return LCMS_RESULT.MISS;
      }
    }
    // If every prediction is a HIT, return HIT.
    return LCMS_RESULT.HIT;
  }

  /**
   * This function takes in multiple LCMS mining results  (in the IonAnalysisInterchangeModel format), which happens
   * when we have multiple positive control replicates and extracts all the molecule hits from each file and makes
   * sure they pass the input thresholds for SNR, time and intensity. If the molecule hit passes these thresholds for
   * ALL the positive replicates, then the inchi is added to the result set.
   * @param filepaths The list of files to be analyzed
   * @param snrThreshold The snr threshold
   * @param intensityThreshold The intensity threshold
   * @param timeThreshold The time threshold
   * @return A list of inchis that are valid molecule hits in all the input files and pass all the thresholds.
   * @throws IOException
   */
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

    /**
     * Each element in deserializedResultsForPositiveReplicates now contains a list of mass charges to a list of
     * molecule+ion combinations for each mass charge. We consider a molecule "valid", ie a hit, if the mass charge
     * it is under for every element in deserializedResultsForPositiveReplicates is above the thresholds we have set.
     */

    // Iterate through every mass charge
    for (int i = 0; i < totalNumberOfMassCharges; i++) {

      int totalNumberOfMoleculesInMassChargeResult =
          deserializedResultsForPositiveReplicates.get(0).getResults().get(i).getMolecules().size();

      // For each mass charge, iterate through each molecule under the mass charge
      for (int j = 0; j < totalNumberOfMoleculesInMassChargeResult; j++) {
        Boolean moleculePassedThresholdsForAllPositiveReplicates = true;

        // For each molecule, make sure it passes the threshold we set across every elem in deserializedResultsForPositiveReplicates,
        // ie across each positive replicate + neg control experiment results
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

  /**
   * This function is used to get the superset inchis from various files representing the different lcms ion runs for
   * a given chemical on a single replicate
   * @param filepaths File paths that represent each ionic variant file
   * @param snrThreshold The snr threshold
   * @param intensityThreshold The intensity threshold
   * @param timeThreshold The time threshold
   * @return The superset of all inchis in each ionic variant file.
   * @throws IOException
   */
  public static Set<String> getSupersetOfIonicVariants(List<String> filepaths,
                                       Double snrThreshold,
                                       Double intensityThreshold,
                                       Double timeThreshold) throws IOException {

    Set<String> inchis = new HashSet<>();
    List<IonAnalysisInterchangeModel> deserializedResultsForPositiveReplicates = new ArrayList<>();
    for (String filePath : filepaths) {
      IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
      model.loadResultsFromFile(new File(filePath));
      deserializedResultsForPositiveReplicates.add(model);
    }

    for (IonAnalysisInterchangeModel analysisInterchangeModel : deserializedResultsForPositiveReplicates) {
      inchis.addAll(analysisInterchangeModel.getAllMoleculeHits(snrThreshold, intensityThreshold, timeThreshold));
    }

    return inchis;
  }

  /**
   * This function is used for getting all inchis that are hits in the corpus
   * @param snrThreshold The snr threshold
   * @param intensityThreshold The intensity threshold
   * @param timeThreshold The time threshold
   * @return A set of inchis
   */
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
    this.populateInchiToIsHit();
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
