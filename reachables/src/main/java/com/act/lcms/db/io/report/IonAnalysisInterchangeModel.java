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
import com.act.lcms.db.analysis.HitOrMissFilterAndTransformer;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class IonAnalysisInterchangeModel {

  // An LCMS result.
  // The idea of NO_DATA is to indicate if we query on a molecule with a mass on which no analysis was done on, to
  // distinguish this case from an actual calculated MISS.
  public enum LCMS_RESULT {
    HIT,
    MISS,
    NO_DATA
  }

  public enum METRIC {
    SNR,
    INTENSITY,
    TIME
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
   * An inchi is considered a hit if any considered ion of that inchi has a MZ value that is a hit.
   */
  private void populateInchiToIsHit() {
    this.inchiToIsHit = new HashMap<>();

    for (ResultForMZ resultForMZ : results) {
      Boolean isHit = resultForMZ.isValid;
      for (HitOrMiss molecule : resultForMZ.getMolecules()) {
        // If the inchi is already a hit, then we do not want to override
        // its hit entry with a possible miss on a different adduct ion for
        // the same molecule. We check multiple metlin ions for each
        // molecules and some may show and others not. In an ideal world
        // with high concentrations "most" adduct ions would show and we
        // could take an AND (see https://github.com/20n/act/issues/383 and
        // https://github.com/20n/act/issues/370#issuecomment-240289674)
        // but when low concentrations exist we would rather take an OR and
        // be conservative, avoiding false negatives.
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
   * This function is used to compute log frequency distribution of the ion model vs a metric.
   * @param metric The metric on which the frequency distribution is plotted
   * @return A map of a range to the count of molecules that get bucketed in that range
   */
  public Map<Pair<Double, Double>, Integer> computeLogFrequencyDistributionOfMoleculeCountToMetric(METRIC metric) {
    Map<Pair<Double, Double>, Integer> rangeToHitCount = new HashMap<>();

    // This variable represents the total number of statistics that have zero values.
    Integer countOfZeroStats = 0;

    // This statistic represents the log value of the min statistic.
    Double minLogValue = Double.MAX_VALUE;

    for (ResultForMZ resultForMZ : this.getResults()) {
      for (HitOrMiss molecule : resultForMZ.getMolecules()) {

        Double power = 0.0;

        switch (metric) {
          case TIME:
            power = Math.log10(molecule.getTime());
            break;
          case INTENSITY:
            power = Math.log10(molecule.getIntensity());
            break;
          case SNR:
            power = Math.log10(molecule.getSnr());
            break;
        }

        if (power.equals(Double.NEGATIVE_INFINITY)) {
          // We know the statistic was 0 here.
          countOfZeroStats++;
          break;
        }

        Double floor = Math.floor(power);
        Double lowerBound = Math.pow(10.0, floor);
        Double upperBound = Math.pow(10.0, floor + 1);

        minLogValue = Math.min(minLogValue, lowerBound);
        Pair<Double, Double> key = Pair.of(lowerBound, upperBound);
        rangeToHitCount.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
      }

      // We count the total number of zero statistics and put them in the 0 to minLog metric bucket.
      if (countOfZeroStats > 0) {
        Pair<Double, Double> key = Pair.of(0.0, minLogValue);
        rangeToHitCount.put(key, countOfZeroStats);
      }
    }

    return rangeToHitCount;
  }

  /**
   * Returns HIT or MISS if the inchi is in the precalculated inchi->hit map, or NO_DATA if the inchi is not.
   * This will let us know if there has been any change in the inchi's form since the initial calculation, instead of
   * just silently returning a miss.
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
   * This function results all the inchis from the model.
   * @return A set of inchis
   */
  @JsonIgnore
  public Set<String> getAllInchis() {
    Set<String> result = new HashSet<>();
    for (ResultForMZ resultForMZ : this.getResults()) {
      result.addAll(resultForMZ.getMolecules().stream().map(molecule -> molecule.getInchi()).collect(Collectors.toList()));
    }
    return result;
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
   * when we have multiple positive control replicates, extracts all the molecule hits from each file and applies
   * a filter function across the replicate hits. The filter function provide two features: it is used to transform results
   * from multiple replicate to a single HitOrMiss molecule, like a min function across replicates. Second, it is
   * used to filter in/out molecules based on the logic of the filter function.
   * @param replicateModels The list of IonAnalysisInterchangeModels to be analyzed
   * @param hitOrMissFilterAndTransformer This filter function takes in single/multiple HitOrMiss objects from replicates and
   *                                   performs a transformation operation on them to produce one HitOrMiss object
   *                                   and a boolean to keep the transformed molecule in the resulting model.
   * @return A list of inchis that are valid molecule hits in all the input files and pass all the thresholds.
   * @throws IOException
   */
  public static IonAnalysisInterchangeModel filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
      List<IonAnalysisInterchangeModel> replicateModels,
      HitOrMissFilterAndTransformer hitOrMissFilterAndTransformer)
      throws IOException {

    // Since all replicates have the same number of peak results, we can use the first model as a representative model
    // for the total num of mass charges.
    int totalNumberOfMassCharges = replicateModels.get(0).getResults().size();
    IonAnalysisInterchangeModel resultModel = new IonAnalysisInterchangeModel();
    List<ResultForMZ> resultsForMZs = new ArrayList<>();

    /**
     * Each element in deserializedResultsForPositiveReplicates now contains a list of mass charges to a list of
     * molecule+ion combinations for each mass charge. We consider a molecule "valid", ie a hit, if the mass charge
     * it is under for every element in deserializedResultsForPositiveReplicates is above the thresholds we have set.
     */

    // Iterate through every mass charge
    // TODO: Consider using a parallel stream here
    for (int i = 0; i < totalNumberOfMassCharges; i++) {
      ResultForMZ representativeMZ = replicateModels.get(0).getResults().get(i);
      Double representativeMassCharge = representativeMZ.getMz();
      int totalNumberOfMoleculesInMassChargeResult = representativeMZ.getMolecules().size();

      ResultForMZ resultForMZ = new ResultForMZ(representativeMassCharge);
      resultForMZ.setId(representativeMZ.getId());

      // TODO: Take out the isValid field since it does not convey useful information for such post processing files.
      resultForMZ.setIsValid(representativeMZ.getIsValid());

      // For each mass charge, iterate through each molecule under the mass charge
      for (int j = 0; j < totalNumberOfMoleculesInMassChargeResult; j++) {

        Pair<HitOrMiss, Boolean> transformedAndIsRetainedMolecule;

        if (replicateModels.size() == 1) {

          // If there is only one replicate, get the molecule corresponding to that mass charge and index in the molecules
          // list for that mass charge.
          HitOrMiss molecule = replicateModels.get(0).getResults().get(i).getMolecules().get(j);
          transformedAndIsRetainedMolecule = hitOrMissFilterAndTransformer.apply(molecule);
        } else {
          List<HitOrMiss> moleculesFromReplicates = new ArrayList<>();

          // For each molecule, make sure it passes the threshold we set across every elem in deserializedResultsForPositiveReplicates,
          // ie across each positive replicate + neg control experiment results
          for (int k = 0; k < replicateModels.size(); k++) {
            ResultForMZ sampleRepresentativeMz = replicateModels.get(k).getResults().get(i);

            // Since we are comparing across replicate files, we expect each ResultForMZ element in the each replicate's
            // IonAnalysisInterchangeModel to be in the same order as other replicates. We check if the mass charges are the
            // same across the samples to make sure the replicates aligned correctly.
            if (!sampleRepresentativeMz.getMz().equals(representativeMassCharge)) {
              throw new RuntimeException("The replicates are not ordered similarly. Please verify if the correct " +
                  "replicates are being used.");
            }

            HitOrMiss molecule = sampleRepresentativeMz.getMolecules().get(j);
            moleculesFromReplicates.add(molecule);
          }

          transformedAndIsRetainedMolecule = hitOrMissFilterAndTransformer.apply(moleculesFromReplicates);
        }

        // Check if the filter function  wants to throw out the molecule. If not, then add the molecule to the final result.
        if (transformedAndIsRetainedMolecule.getRight()) {
          resultForMZ.addMolecule(transformedAndIsRetainedMolecule.getLeft());
        }
      }

      resultsForMZs.add(resultForMZ);
    }

    resultModel.setResults(resultsForMZs);
    return resultModel;
  }

  /**
   * This function loads in multiple serialized IonAnalysisInterchangeModels and deserializes them
   * @param filepaths File paths to the serialized IonAnalysisInterchangeModels
   * @return A list of IonAnalysisInterchangeModels corresponding to the files.
   * @throws IOException
   */
  public static List<IonAnalysisInterchangeModel> loadMultipleIonAnalysisInterchangeModelsFromFiles(List<String> filepaths)
      throws IOException {

    List<IonAnalysisInterchangeModel> deserializedResultsForPositiveReplicates = new ArrayList<>();
    for (String filePath : filepaths) {
      IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
      model.loadResultsFromFile(new File(filePath));
      deserializedResultsForPositiveReplicates.add(model);
    }
    return deserializedResultsForPositiveReplicates;
  }

  /**
   * This function is used to get the superset inchis from various models representing the different lcms ion runs for
   * a given chemical on a single replicate
   * @param models IonAnalysisInterchangeModels for each ionic variant
   * @param snrThreshold The snr threshold
   * @param intensityThreshold The intensity threshold
   * @param timeThreshold The time threshold
   * @return The superset of all inchis in each ionic variant file.
   * @throws IOException
   */
  public static IonAnalysisInterchangeModel getSupersetOfIonicVariants(List<IonAnalysisInterchangeModel> models,
                                       Double snrThreshold,
                                       Double intensityThreshold,
                                       Double timeThreshold) throws IOException {

    IonAnalysisInterchangeModel resultModel = new IonAnalysisInterchangeModel();
    List<ResultForMZ> resultForMZList = new ArrayList<>();

    for (IonAnalysisInterchangeModel analysisInterchangeModel : models) {

      for (ResultForMZ resultForMZ : analysisInterchangeModel.getResults()) {
        List<HitOrMiss> allMoleculesThatPass = new ArrayList<>();

        for (HitOrMiss molecule : resultForMZ.getMolecules()) {
         if (molecule.getIntensity() > intensityThreshold &&
             molecule.getSnr() > snrThreshold &&
             molecule.getTime() > timeThreshold) {
           allMoleculesThatPass.add(molecule);
         }
        }

        if (allMoleculesThatPass.size() > 0) {
          ResultForMZ newResultForMZ = new ResultForMZ(resultForMZ.getMz());
          newResultForMZ.addMolecules(allMoleculesThatPass);
          resultForMZList.add(newResultForMZ);
        }
      }
    }

    resultModel.setResults(resultForMZList);
    return resultModel;
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

    public ResultForMZ(Double mz, Boolean hit) {
      this.id = ID_COUNTER.incrementAndGet();
      this.mz = mz;
      this.isValid = hit;
      this.molecules = new ArrayList<>();
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
    public HitOrMiss() {

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

    public void setInchi(String inchi) {
      this.inchi = inchi;
    }

    public String getIon() {
      return ion;
    }

    public void setIon(String ion) {
      this.ion = ion;
    }

    public Double getSnr() {
      return snr;
    }

    public void setSnr(Double snr) {
      this.snr = snr;
    }

    public Double getTime() {
      return time;
    }

    public void setTime(Double time) {
      this.time = time;
    }

    public Double getIntensity() {
      return intensity;
    }

    public void setIntensity(Double intensity) {
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
