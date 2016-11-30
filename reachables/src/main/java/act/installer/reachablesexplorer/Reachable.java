package act.installer.reachablesexplorer;


import act.shared.Chemical;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Reachable {
  @JsonIgnore
  static final String inchiFieldName = "InChI";


  @JsonProperty("_id")
  private Long id;
  private String pageName;
  private String pathwayVisualization;
  private Boolean isNative;
  @JsonProperty("InChI")
  private String inchi;
  private String smiles;
  private String inchiKey;
  private String structureFilename;
  private List<String> names;
  private String wordCloudFilename;
  private PrecursorData precursorData;
  private Map<Chemical.REFS, BasicDBObject> xref;
  private SynonymData synonyms;
  private List<PatentSummary> patentSummaries;

  public Reachable() {}

  public Reachable(Long id,
                   String pageName,
                   String inchi,
                   String smiles,
                   String inchiKey,
                   List<String> names,
                   PrecursorData precursors,
                   SynonymData synonyms,
                   Boolean isNative,
                   String structureFilename,
                   String wordCloudFilename,
                   String pathwayVisualization,
                   Map<Chemical.REFS, BasicDBObject> xref) {
    this.id = id;
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchiKey = inchiKey;
    this.names = names;
    this.precursorData = precursors;
    this.synonyms = synonyms;
    this.isNative = isNative;
    this.structureFilename = structureFilename;
    this.wordCloudFilename = wordCloudFilename;
    this.pathwayVisualization = pathwayVisualization;
    this.xref = xref;
  }

  public Reachable(Long id, String pageName, String inchi, String smiles, String inchiKey, List<String> names,
                   SynonymData synonymData, String structureFilename, String wordCloudFilename, Map<Chemical.REFS, BasicDBObject> xref) {
    this(id, pageName, inchi, smiles, inchiKey, names, new PrecursorData(), synonymData, null, structureFilename, wordCloudFilename, null, xref);
  }

  public String getPageName() {
    return pageName;
  }

  public void setPageName(String pageName) {
    this.pageName = pageName;
  }

  public String getPathwayVisualization() {
    return pathwayVisualization;
  }

  public void setPathwayVisualization(String pathwayVisualization) {
    this.pathwayVisualization = pathwayVisualization;
  }

  public Boolean getNative() {
    return isNative;
  }

  public void setNative(Boolean aNative) {
    isNative = aNative;
  }

  @JsonIgnore
  public String getInchi() {
    return inchi;
  }

  @JsonIgnore
  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public String getSmiles() {
    return smiles;
  }

  public void setSmiles(String smiles) {
    this.smiles = smiles;
  }

  public String getInchiKey() {
    return inchiKey;
  }

  public void setInchiKey(String inchiKey) {
    this.inchiKey = inchiKey;
  }

  public String getStructureFilename() {
    return structureFilename;
  }

  public void setStructureFilename(String structureFilename) {
    this.structureFilename = structureFilename;
  }

  public List<String> getNames() {
    return names;
  }

  public void setNames(List<String> names) {
    this.names = names;
  }

  public String getWordCloudFilename() {
    return wordCloudFilename;
  }

  public void setWordCloudFilename(String wordCloudFilename) {
    this.wordCloudFilename = wordCloudFilename;
  }

  public PrecursorData getPrecursorData() {
    return precursorData;
  }

  public void setPrecursorData(PrecursorData precursorData) {
    this.precursorData = precursorData;
  }

  public Map<Chemical.REFS, BasicDBObject> getXref() {
    return xref;
  }

  public void setXref(Map<Chemical.REFS, BasicDBObject> xref) {
    this.xref = xref;
  }

  public SynonymData getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(SynonymData synonyms) {
    this.synonyms = synonyms;
  }

  public List<PatentSummary> getPatentSummaries() {
    return this.patentSummaries;
  }

  public void setPatentSummaries(List<PatentSummary> patentSummaries) {
    this.patentSummaries = patentSummaries;
  }

  public void addPatentSummaries(List<PatentSummary> patentSummaries) {
    if (this.patentSummaries == null) {
      this.patentSummaries = new ArrayList<>();
    }
    this.patentSummaries.addAll(patentSummaries);
  }

}
