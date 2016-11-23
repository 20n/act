package act.installer.reachablesexplorer;


import act.shared.Chemical;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.BasicDBObject;
import org.mongojack.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Reachable {
  @JsonIgnore
  static final String inchiName = "InChI";

  private String id;
  @JsonProperty("pathway_vis")
  private String pathwayVisualization;
  @JsonProperty("is_native")
  private Boolean isNative;
  @JsonProperty("InChI")
  private String inchi;
  @JsonProperty("smiles")
  private String smiles;
  @JsonProperty("inchikey")
  private String inchikey;
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

  public Reachable(String pageName, String inchi, String smiles, String inchikey, List<String> names, Boolean isNative, Map<Chemical.REFS, BasicDBObject> xref) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchikey = inchikey;
    this.structureFilename = null;
    this.names = names;
    this.wordCloudFilename = null;
    this.precursorData = new PrecursorData();
    this.xref = xref;
    this.isNative = isNative;
    this.pathwayVisualization = null;
  }

  public Reachable(String pageName, String inchi, String smiles, String inchikey, List<String> names, Map<Chemical.REFS, BasicDBObject> xref) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchikey = inchikey;
    this.structureFilename = null;
    this.names = names;
    this.wordCloudFilename = null;
    this.precursorData = new PrecursorData();
    this.xref = xref;
    this.isNative = false;
    this.pathwayVisualization = null;
  }

  public Reachable(String pageName, String inchi, String smiles, String inchikey, String structureFilename, List<String> names, String wordCloudFilename, Map<Chemical.REFS, BasicDBObject> xref) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchikey = inchikey;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = new PrecursorData();
    this.xref = xref;
    this.isNative = false;
    this.pathwayVisualization = null;
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

  @ObjectId
  @JsonProperty("_id")
  public String getId() {
    return id;
  }

  @ObjectId
  @JsonProperty("_id")
  public void setId(String id) {
    this.id = id;
  }

  @JsonIgnore
  public String getPageName(){
    return pageName;
  }

  @JsonIgnore
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

  /* -------- Getters must have JSON ignore if not a unique field. ------- */
  @JsonIgnore
  public String getInchi(){
    return inchi;
  }

  @JsonIgnore
  public String getInchiKey(){
    return inchikey;
  }

  @JsonIgnore
  public void setPathwayVisualization(String graphName) {
    this.pathwayVisualization = graphName;
  }

  @JsonIgnore
  public String getStructureFilename() {
    return structureFilename;
  }

  @JsonIgnore
  public void setStructureFilename(String structureFilename) {
    this.structureFilename = structureFilename;
  }

  @JsonIgnore
  public String getWordCloudFilename() {
    return wordCloudFilename;
  }

  @JsonIgnore
  public void setWordCloudFilename(String wordCloudFilename) {
    this.wordCloudFilename = wordCloudFilename;
  }

  @JsonIgnore
  public SynonymData getSynonyms() {
    return synonyms;
  }

  @JsonIgnore
  public void setSynonyms(SynonymData synonyms) {
    this.synonyms = synonyms;
  }
}
