package act.installer.reachablesexplorer;


import act.shared.Chemical;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.BasicDBObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Reachable {
  @JsonIgnore
  static final String inchiFieldName = "InChI";

  private String id;

  public static String getInchiFieldName() {
    return inchiFieldName;
  }

  public String getPathwayVisualization() {
    return pathwayVisualization;
  }

  public Boolean getNative() {
    return isNative;
  }

  public void setNative(Boolean aNative) {
    isNative = aNative;
  }

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

  public List<String> getNames() {
    return names;
  }

  public void setNames(List<String> names) {
    this.names = names;
  }

  public WikipediaData getWikipediaData() {
    return wikipediaData;
  }

  public void setWikipediaData(WikipediaData wikipediaData) {
    this.wikipediaData = wikipediaData;
  }

  public Map<Chemical.REFS, BasicDBObject> getXref() {
    return xref;
  }

  public void setXref(Map<Chemical.REFS, BasicDBObject> xref) {
    this.xref = xref;
  }

  public void setPageName(String pageName) {
    this.pageName = pageName;
  }

  @JsonProperty("pathway_vis")
  private String pathwayVisualization;
  @JsonProperty("is_native")
  private Boolean isNative;
  @JsonProperty("InChI")
  private String inchi;
  @JsonProperty("smiles")
  private String smiles;
  @JsonProperty("inchikey")
  private String inchiKey;
  @JsonProperty("rendering-filename")
  private String structureFilename;
  @JsonProperty("names")
  private List<String> names;
  @JsonProperty("wikipedia-data")
  private WikipediaData wikipediaData;
  @JsonProperty("usage-wordcloud-filename")
  private String wordCloudFilename;
  @JsonProperty("precursor")
  private PrecursorData precursorData;
  @JsonProperty("xref")
  private Map<Chemical.REFS, BasicDBObject> xref;
  @JsonProperty("page_name")
  private String pageName;
  @JsonProperty("synonyms")
  private SynonymData synonyms;

  public Reachable() {}

  public Reachable(String pageName, String inchi, String smiles, String inchiKey, List<String> names, Boolean isNative, Map<Chemical.REFS, BasicDBObject> xref) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchiKey = inchiKey;
    this.structureFilename = null;
    this.names = names;
    this.wordCloudFilename = null;
    this.precursorData = new PrecursorData();
    this.xref = xref;
    this.isNative = isNative;
    this.pathwayVisualization = null;
  }

  public Reachable(String pageName, String inchi, String smiles, String inchiKey, List<String> names, Map<Chemical.REFS, BasicDBObject> xref) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchiKey = inchiKey;
    this.structureFilename = null;
    this.names = names;
    this.wordCloudFilename = null;
    this.precursorData = new PrecursorData();
    this.xref = xref;
    this.isNative = false;
    this.pathwayVisualization = null;
  }

  public Reachable(String pageName, String inchi, String smiles, String inchiKey, String structureFilename, List<String> names, String wordCloudFilename, Map<Chemical.REFS, BasicDBObject> xref) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchiKey = inchiKey;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = new PrecursorData();
    this.xref = xref;
    this.isNative = false;
    this.pathwayVisualization = null;
  }

  public SynonymData getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(SynonymData synonyms) {
    this.synonyms = synonyms;
  }

  public void setPathwayVisualization(String pathwayVisualization) {
    this.pathwayVisualization = pathwayVisualization;
  }

  public String getInchi() {
    return inchi;
  }

  public String getStructureFilename() {
    return structureFilename;
  }

  public void setStructureFilename(String structureFilename) {
    this.structureFilename = structureFilename;
  }

  public String getWordCloudFilename() {
    return wordCloudFilename;
  }

  public void setWordCloudFilename(String wordCloudFilename) {
    this.wordCloudFilename = wordCloudFilename;
  }

  public Reachable(String pageName,
                   String inchi,
                   String smiles,
                   String structureFilename,
                   List<String> names,
                   String wordCloudFilename,
                   PrecursorData precursors,
                   Boolean isNative,
                   String pathwayVisualization,
                   Map<Chemical.REFS, BasicDBObject> xref,
                   SynonymData synonyms) {
    this.pageName = pageName;

    this.inchi = inchi;
    this.smiles = smiles;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = precursors;
    this.xref = xref;
    this.isNative = isNative;
    this.pathwayVisualization = pathwayVisualization;
    this.synonyms = synonyms;
  }

  public void setIsNative(Boolean isNative) {
    this.isNative = isNative;
  }

  public PrecursorData getPrecursorData() {
    return this.precursorData;
  }

  public void setPrecursorData(PrecursorData precursorData) {
    this.precursorData = precursorData;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getPageName(){
    return pageName;
  }

  public Map<Chemical.REFS, BasicDBObject> getXREFS() {
    return this.xref;
  }

  // This is used only when loading xrefs separately from the rest
  // Let's leave it here for now!
  @JsonIgnore
  public void setXREFS(BasicDBObject xrefs) {
    this.xref = new HashMap<>();
    if (xrefs == null) {
      return;
    }
    for (String typ : xrefs.keySet()) {
      if (typ.equals("pubchem"))
        continue;
      this.xref.put(Chemical.REFS.valueOf(typ), (BasicDBObject) xrefs.get(typ));
    }
  }
}
