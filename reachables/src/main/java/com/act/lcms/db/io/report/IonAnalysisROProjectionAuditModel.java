package com.act.lcms.db.io.report;

import com.act.biointerpretation.l2expansion.L2Prediction;
import com.act.biointerpretation.l2expansion.L2PredictionRo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class represents the results of an ion analysis with provenance information and supporting data for all
 * results.  It can be used either by humans (for auditing/validation) or by machines when a complete record of the
 * ion analysis produced from RO projections is required.  Example:
 * <pre>
{
  "results": [
    {
      "l2_prediction" {
          #L2Prediction serialization, as defined in L2PredictionCorpus currently
          #This includes substrates, RO, product, SAR if exists
          #Note that already contains the prediction ID so we don't need to add it separately>
      }
      "predicted_products": {
        "InChI=...": {
          "found": "true",
          "best_ion": "M+H",
          "best_ion_SNR": 1000.0,
          "best_ion_max_peak_intensity": 100000.0,
          "best_ion_time": 45.0,
          "ionToAnalysis": {
            "M+H": {
              "SNR": 1000.0,
              "max_peak_intensity": 100000.0,
              "time": 45.0,
              "plot_file": "foobarbaz.pdf"
            },
            "M+Na": {
              "SNR": 10.0,
              "max_peak_intensity": 1000.0,
              "time": 15.0,
              "plot_file": "quxquxxquxxx.pdf"
            }
          }
        }
      }
    }
  ]
}
 </pre>
 */
public class IonAnalysisROProjectionAuditModel {
  @JsonProperty("results")
  private List<PredictionAndResults> results = new ArrayList<>();

  public IonAnalysisROProjectionAuditModel() {

  }

  public IonAnalysisROProjectionAuditModel(List<PredictionAndResults> results) {
    this.results.addAll(results);
  }

  public List<PredictionAndResults> getResults() {
    return results;
  }

  protected void setResults(List<PredictionAndResults> results) {
    this.results = results;
  }

  public void addResult(PredictionAndResults predictionAndResults) {
    this.results.add(predictionAndResults);
  }

  public void addResults(List<PredictionAndResults> predictionAndResults) {
    this.results.addAll(predictionAndResults);
  }

  public void addResult(L2Prediction prediction, IonSearchResults results) {
    this.results.add(new PredictionAndResults(prediction, results));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IonAnalysisROProjectionAuditModel that = (IonAnalysisROProjectionAuditModel) o;

    return results != null ? results.equals(that.results) : that.results == null;

  }

  @Override
  public int hashCode() {
    return results != null ? results.hashCode() : 0;
  }

  /**
   * A combined L2 prediction and its ion search analysis results.
   */
  public static class PredictionAndResults {
    @JsonProperty("l2_prediction")
    private L2Prediction l2Prediction;

    @JsonProperty("predicted_products") // TODO: need custom deserializer
    private Map<String, IonSearchResults> inchiToIonSearchResults = new HashMap<>();

    // For deserialization.
    protected PredictionAndResults() {

    }

    public PredictionAndResults(L2Prediction l2Prediction,
                                Map<String, IonSearchResults> inchiToIonSearchResults) {
      this.l2Prediction = l2Prediction;
      this.inchiToIonSearchResults.putAll(inchiToIonSearchResults);
    }

    protected PredictionAndResults(L2Prediction l2Prediction,
                                   IonSearchResults ionSearchResults) {
      this.l2Prediction = l2Prediction;
      this.inchiToIonSearchResults.put(ionSearchResults.getInchi(), ionSearchResults);
    }

    public L2Prediction getL2Prediction() {
      return l2Prediction;
    }

    protected void setL2Prediction(L2Prediction l2Prediction) {
      this.l2Prediction = l2Prediction;
    }

    public Map<String, IonSearchResults> getInchiToIonSearchResults() {
      return inchiToIonSearchResults;
    }

    protected void setInchiToIonSearchResults(Map<String, IonSearchResults> inchiToIonSearchResults) {
      this.inchiToIonSearchResults = inchiToIonSearchResults;
    }

    public void addIonSearchResults(IonSearchResults ionSearchResults) {
      this.inchiToIonSearchResults.put(ionSearchResults.getInchi(), ionSearchResults);
    }

    public void addIonSearchResults(List<IonSearchResults> ionSearchResults) {
      ionSearchResults.forEach(r -> this.inchiToIonSearchResults.put(r.getInchi(), r));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PredictionAndResults that = (PredictionAndResults) o;

      if (l2Prediction != null ? !l2Prediction.equals(that.l2Prediction) : that.l2Prediction != null) return false;
      return inchiToIonSearchResults != null ? inchiToIonSearchResults.equals(that.inchiToIonSearchResults) :
          that.inchiToIonSearchResults == null;
    }

    @Override
    public int hashCode() {
      int result = l2Prediction != null ? l2Prediction.hashCode() : 0;
      result = 31 * result + (inchiToIonSearchResults != null ? inchiToIonSearchResults.hashCode() : 0);
      return result;
    }

  }

  /**
   * Ion search analysis results for a single InChI.
   */
  public static class IonSearchResults {
    @JsonIgnore
    String inchi;

    @JsonProperty("monoisotopic_mass")
    Double mass;

    @JsonProperty("found")
    Boolean found;

    @JsonProperty("best_ion")
    String bestIon;

    @JsonProperty("best_ion_SNR")
    Double bestIonSNR;

    @JsonProperty("best_ion_max_peak_intensity")
    Double bestIonMaxPeakIntensity;

    @JsonProperty("best_ion_peak_retention_time")
    Double bestIonPeakRetentionTime;

    @JsonProperty("analysis") // TODO: need custom deserializer for this map.
    @JsonDeserialize(using = AnalysisFeaturesDeserializer.class)
    Map<String, AnalysisFeatures> ionToAnalysis = new HashMap<>();

    // For deserialization.
    protected IonSearchResults() {

    }

    public IonSearchResults(String inchi, Double mass, Boolean found, String bestIon,
                            Map<String, AnalysisFeatures> ionToAnalysis) {
      this.inchi = inchi;
      this.mass = mass;
      this.found = found;
      this.bestIon = bestIon;
      this.ionToAnalysis.putAll(ionToAnalysis);
      setBestIonFeatures();
    }

    public IonSearchResults(String inchi, Double mass, Boolean found) {
      this.inchi = inchi;
      this.mass = mass;
      this.found = found;
    }

    private void setBestIonFeatures() {
      if (!ionToAnalysis.containsKey(bestIon)) {
        throw new RuntimeException(String.format(
            "Data integrity error, best ion '%s' does not appear in ionToAnalysis map (keys are %s)",
            bestIon, StringUtils.join(this.ionToAnalysis.keySet(), ", "))
        );
      }
      AnalysisFeatures bestFeatures = this.ionToAnalysis.get(this.bestIon);
      this.bestIonSNR = bestFeatures.getSNR();
      this.bestIonMaxPeakIntensity = bestFeatures.getMaxPeakIntensity();
      this.bestIonPeakRetentionTime = bestFeatures.getPeakRetentionTime();
    }

    public String getInchi() {
      return inchi;
    }

    protected void setInchi(String inchi) {
      this.inchi = inchi;
    }

    public Double getMass() {
      return mass;
    }

    protected void setMass(Double mass) {
      // TODO: consider computing the mass if one is not specified.
      this.mass = mass;
    }

    public Boolean getFound() {
      return found;
    }

    protected void setFound(Boolean found) {
      this.found = found;
    }

    public String getBestIon() {
      return bestIon;
    }

    // Made protected because this can be called without setting the best ion features during deserialization.
    protected void setBestIon(String bestIon) {
      this.bestIon = bestIon;
      if (this.ionToAnalysis.containsKey(this.bestIon)) {
        setBestIonFeatures();
      }
    }

    public Double getBestIonSNR() {
      return bestIonSNR;
    }

    public Double getBestIonMaxPeakIntensity() {
      return bestIonMaxPeakIntensity;
    }

    public Double getBestIonPeakRetentionTime() {
      return bestIonPeakRetentionTime;
    }

    public Map<String, AnalysisFeatures> getIonToAnalysis() {
      return ionToAnalysis;
    }

    protected void setIonToAnalysis(Map<String, AnalysisFeatures> ionToAnalysis) {
      this.ionToAnalysis = ionToAnalysis;
      if (this.bestIon != null) {
        setBestIonFeatures();
      }
    }

    public void addIonAnalysis(AnalysisFeatures analysisFeatures, boolean isBest) {
      this.ionToAnalysis.put(analysisFeatures.getIon(), analysisFeatures);
      if (isBest) {
        this.bestIon = analysisFeatures.getIon();
        setBestIonFeatures();
      }
    }

    public void addIonAnalyses(List<AnalysisFeatures> analysisFeatures, String bestIon) {
      analysisFeatures.forEach(a -> this.ionToAnalysis.put(a.getIon(), a));
      if (bestIon != null) {
        // Note: does not handle case where specified bestIon argument is not present in list.
        this.bestIon = bestIon;
        setBestIonFeatures();
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IonSearchResults that = (IonSearchResults) o;

      if (inchi != null ? !inchi.equals(that.inchi) : that.inchi != null) return false;
      if (found != null ? !found.equals(that.found) : that.found != null) return false;
      if (bestIon != null ? !bestIon.equals(that.bestIon) : that.bestIon != null) return false;
      if (bestIonSNR != null ? !bestIonSNR.equals(that.bestIonSNR) : that.bestIonSNR != null) return false;
      if (bestIonMaxPeakIntensity != null ? !bestIonMaxPeakIntensity.equals(that.bestIonMaxPeakIntensity) :
          that.bestIonMaxPeakIntensity != null)
        return false;
      if (bestIonPeakRetentionTime != null ? !bestIonPeakRetentionTime.equals(that.bestIonPeakRetentionTime) :
          that.bestIonPeakRetentionTime != null)
        return false;
      return ionToAnalysis != null ? ionToAnalysis.equals(that.ionToAnalysis) : that.ionToAnalysis == null;
    }

    @Override
    public int hashCode() {
      int result = inchi != null ? inchi.hashCode() : 0;
      result = 31 * result + (found != null ? found.hashCode() : 0);
      result = 31 * result + (bestIon != null ? bestIon.hashCode() : 0);
      result = 31 * result + (bestIonSNR != null ? bestIonSNR.hashCode() : 0);
      result = 31 * result + (bestIonMaxPeakIntensity != null ? bestIonMaxPeakIntensity.hashCode() : 0);
      result = 31 * result + (bestIonPeakRetentionTime != null ? bestIonPeakRetentionTime.hashCode() : 0);
      result = 31 * result + (ionToAnalysis != null ? ionToAnalysis.hashCode() : 0);
      return result;
    }
  }

  /**
   * Analysis results for a single ion.
   */
  public static class AnalysisFeatures {
    @JsonIgnore
    private String ion;

    @JsonProperty("mass_charge")
    private Double mz;

    @JsonProperty("SNR")
    private Double SNR;

    @JsonProperty("max_peak_intensity")
    private Double maxPeakIntensity;

    @JsonProperty("peak_retention_time")
    private Double peakRetentionTime;

    @JsonProperty("plot_file")
    private String plotFile;

    // For deserialization.
    protected AnalysisFeatures() {

    }

    public AnalysisFeatures(String ion, Double mz, Double SNR,
                            Double maxPeakIntensity, Double peakRetentionTime, String plotFile) {
      this.ion = ion;
      this.mz = mz;
      this.SNR = SNR;
      this.maxPeakIntensity = maxPeakIntensity;
      this.peakRetentionTime = peakRetentionTime;
      this.plotFile = plotFile;
    }

    public String getIon() {
      return ion;
    }

    protected void setIon(String ion) {
      this.ion = ion;
    }

    public Double getMz() {
      return mz;
    }

    protected void setMz(Double mz) {
      this.mz = mz;
    }

    public Double getSNR() {
      return SNR;
    }

    protected void setSNR(Double SNR) {
      this.SNR = SNR;
    }

    public Double getMaxPeakIntensity() {
      return maxPeakIntensity;
    }

    protected void setMaxPeakIntensity(Double maxPeakIntensity) {
      this.maxPeakIntensity = maxPeakIntensity;
    }

    public Double getPeakRetentionTime() {
      return peakRetentionTime;
    }

    protected void setPeakRetentionTime(Double peakRetentionTime) {
      this.peakRetentionTime = peakRetentionTime;
    }

    public String getPlotFile() {
      return plotFile;
    }

    protected void setPlotFile(String plotFile) {
      this.plotFile = plotFile;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AnalysisFeatures that = (AnalysisFeatures) o;

      if (ion != null ? !ion.equals(that.ion) : that.ion != null) return false;
      if (SNR != null ? !SNR.equals(that.SNR) : that.SNR != null) return false;
      if (maxPeakIntensity != null ? !maxPeakIntensity.equals(that.maxPeakIntensity) : that.maxPeakIntensity != null)
        return false;
      if (peakRetentionTime != null ? !peakRetentionTime.equals(that.peakRetentionTime) :
          that.peakRetentionTime != null)
        return false;
      return plotFile != null ? plotFile.equals(that.plotFile) : that.plotFile == null;

    }

    @Override
    public int hashCode() {
      int result = ion != null ? ion.hashCode() : 0;
      result = 31 * result + (SNR != null ? SNR.hashCode() : 0);
      result = 31 * result + (maxPeakIntensity != null ? maxPeakIntensity.hashCode() : 0);
      result = 31 * result + (peakRetentionTime != null ? peakRetentionTime.hashCode() : 0);
      result = 31 * result + (plotFile != null ? plotFile.hashCode() : 0);
      return result;
    }
  }

  private abstract static class StringKeyedMapDeserializer<V> extends JsonDeserializer<Map<String, V>> {
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Modify the value in some way using the key.
    protected abstract V transform(String key, V val);

    /* Thanks to type erasure, TypeReference<V> and Class<V> don't get <V> rewritten to correctly bind to the type
     * specified by the subclass.  Instead, we let the subclass convert the JsonNode to its proper type using an
     * ObjectMapper and the actual class of its value type.  Sigh. */
    protected abstract V treeToValue(JsonNode node) throws JsonProcessingException;

    @Override
    public Map<String, V> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      ObjectNode node = p.readValueAsTree();
      Map<String, V> results = new HashMap<>(node.size());

      Iterator<Map.Entry<String, JsonNode>> iter = node.fields();
      while (iter.hasNext()) {
        Map.Entry<String, JsonNode> entry = iter.next();
        V originalVal = treeToValue(entry.getValue());
        V val = transform(entry.getKey(), originalVal);
        results.put(entry.getKey(), val);
      }
      return results;
    }
  }

  /* Custom deserializer grabs InChI keys and sets them as fields in values for convenience.
   * These must be static classes, which means they must appear at the top level.  See the very helpful
   * http://stackoverflow.com/questions/7625783/jsonmappingexception-no-suitable-constructor-found-for-type-simple-type-class#comment20840974_7626872
   */
  public static class InchiResultsDeserializer
      extends StringKeyedMapDeserializer<IonSearchResults> {

    @Override
    protected IonSearchResults treeToValue(JsonNode node) throws JsonProcessingException {
      return OBJECT_MAPPER.treeToValue(node, IonSearchResults.class);
    }

    @Override
    protected IonSearchResults transform(String key, IonSearchResults val) {
      val.setInchi(key);
      return val;
    }
  }

  public static class AnalysisFeaturesDeserializer extends StringKeyedMapDeserializer<AnalysisFeatures> {
    @Override
    protected AnalysisFeatures treeToValue(JsonNode node) throws JsonProcessingException {
      return OBJECT_MAPPER.treeToValue(node, AnalysisFeatures.class);
    }

    @Override
    protected AnalysisFeatures transform(String key, AnalysisFeatures val) {
      val.setIon(key);
      return val;
    }
  }

  public static void main(String[] args) throws Exception {
    IonAnalysisROProjectionAuditModel v = new IonAnalysisROProjectionAuditModel(new ArrayList<PredictionAndResults>() {{
      add(new PredictionAndResults(
          new L2Prediction(0, Collections.emptyList(), new L2PredictionRo(0, "[C,c]>>[C,c]"), Collections.emptyList()),
          new HashMap<String, IonSearchResults>() {{
            put("InChI=1S/FAKE1", new IonSearchResults("InChI=1S/FAKE1", 123.0, true, "M+H",
                new HashMap<String, AnalysisFeatures>(){{
                  put("M+H", new AnalysisFeatures("M+H", 124.0, 1000.0, 100000.0, 45.0, "foobarbaz.pdf"));
                  put("M+Na", new AnalysisFeatures("M+Na", 130.0, 10.0, 1000.0, 15.0, "quxquxxquxxx.pdf"));
                }}));
          }}));
    }});

    ObjectMapper objectMapper = new ObjectMapper();
    String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(v);
    System.out.format("%s\n", json);

    IonAnalysisROProjectionAuditModel v2 = objectMapper.readValue(json, new TypeReference<IonAnalysisROProjectionAuditModel>() {});

    System.out.format("Two objects match? %s\n", v.equals(v2));

    String json2 = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(v2);
    System.out.format("%s\n", json2);

    System.out.format("Two JSON strings match? %s\n", json.equals(json2));
  }
}
