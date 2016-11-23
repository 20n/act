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
  private WikipediaData wikipediaData;
  private String wordCloudFilename;
  private PrecursorData precursorData;
  private Map<Chemical.REFS, BasicDBObject> xref;
  private SynonymData synonyms;

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

  public Reachable(String pageName,
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



  public void setIsNative(Boolean isNative) {
    this.isNative = isNative;
  }

  public PrecursorData getPrecursorData() {
    return this.precursorData;
  }

  public void setPrecursorData(PrecursorData precursorData) {
    this.precursorData = precursorData;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
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
