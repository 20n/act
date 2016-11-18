package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Reachable {

  private String id;
  @JsonProperty("page_name")
  private String pageName;
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

  public Reachable() {}

  public Reachable(String pageName, String inchi, String smiles, String inchikey, List<String> names) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchikey = inchikey;
    this.structureFilename = null;
    this.names = names;
    this.wordCloudFilename = null;
    this.precursorData = new PrecursorData();
  }

  @JsonCreator
  public Reachable(@JsonProperty("page_name") String pageName,
                   @JsonProperty("inchi") String inchi,
                   @JsonProperty("smiles") String smiles,
                   @JsonProperty("rendering-filename") String structureFilename,
                   @JsonProperty("names") List<String> names,
                   @JsonProperty("usage-wordcloud-filename") String wordCloudFilename,
                   @JsonProperty("precursor") PrecursorData precursors) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = precursors;
  }

  public PrecursorData getPrecursorData() {
    if (this.precursorData == null) {
      return new PrecursorData();
    }
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

  public String getPageName(){
    return pageName;
  }

  public void setPageName(String pageName) {
    this.pageName = pageName;
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

  public void setInchiKey(String inchikey) {
    this.inchikey = inchikey;
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

  public void setSmiles(String smiles) {
    this.smiles = smiles;
  }

  public void setNames(List<String> names) {
    this.names = names;
  }
}
