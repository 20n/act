package act.installer.reachablesexplorer;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class Reachable {

  public Reachable() {}

  public Reachable(String pageName, String inchi, String smiles, String inchikey, String structureFilename, List<String> names, String wordCloudFilename) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchikey = inchikey;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = null;
  }

  public Reachable(String pageName, String inchi, String smiles, String structureFilename, List<String> names, String wordCloudFilename, PrecursorData precursors) {
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = precursors;
  }

  private class WikipediaData {

    @JsonProperty("url")
    private String url;

    @JsonProperty("text")
    private String text;
  }

  public void setPrecursorData(PrecursorData precursorData) {
    this.precursorData = precursorData;
  }

  static class PrecursorData implements Serializable {

    private class Precusor implements Serializable {

      public Precusor() {}

      public Precusor(List<String> precursorMolecules, List<String> sources) {
        this.precursorMolecules = precursorMolecules;
        this.sources = sources;
      }

      @JsonProperty("precursor_inchis")
      private List<String> precursorMolecules;
      @JsonProperty("source")
      private List<String> sources;
    }

    @JsonProperty("prediction_precursors")
    private List<Precusor> precursors;


  }

  private class SynonymData {
    @JsonProperty("pubchem")
    private List<String> pubchemSynonyms;

    @JsonProperty("mesh")
    private List<String> meshSynonyms;
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
}
