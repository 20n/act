package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class Reachable {

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

  public String getStructureFilename() {
    return structureFilename;
  }

  public String getWordCloudFilename() {
    return wordCloudFilename;
  }
}
