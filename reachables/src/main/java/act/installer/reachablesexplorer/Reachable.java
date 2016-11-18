package act.installer.reachablesexplorer;


import act.shared.Chemical;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.mongojack.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Reachable {

  public Reachable() {}

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
  }

  @JsonCreator
  public Reachable(@JsonProperty("page_name") String pageName,
                   @JsonProperty("inchi") String inchi,
                   @JsonProperty("smiles") String smiles,
                   @JsonProperty("rendering-filename") String structureFilename,
                   @JsonProperty("names") List<String> names,
                   @JsonProperty("usage-wordcloud-filename") String wordCloudFilename,
                   @JsonProperty("precursor") PrecursorData precursors,
                   @JsonProperty("xref") Map<Chemical.REFS, BasicDBObject> xref) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = precursors;
    this.xref = xref;
  }


  public void setPrecursorData(PrecursorData precursorData) {
    this.precursorData = precursorData;
  }

  public PrecursorData getPrecursorData() {
    return this.precursorData;
  }

  private String id;
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

  @JsonProperty("page_name")
  private String pageName;

  @JsonIgnore
  public String getPageName(){
    return pageName;
  }

  @JsonProperty("inchi")
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


  @JsonIgnore
  public Map<Chemical.REFS, BasicDBObject> getXREFS() {
    return this.xref;
  }

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

  public void setWordCloudFilename(String wordCloudFilename) {
    this.wordCloudFilename = wordCloudFilename;
  }

  public void setStructureFilename(String structureFilename) {
    this.structureFilename = structureFilename;
  }

  @JsonIgnore
  public String getStructureFilename() {
    return structureFilename;
  }

  @JsonIgnore
  public String getWordCloudFilename() {
    return wordCloudFilename;
  }
}
