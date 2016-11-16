package act.installer.reachablesexplorer;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import java.util.List;

public class Reachable {

  public Reachable(String inchi, String smiles, String inchikey, String structureFilename, List<String> names, String wordCloudFilename) {
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchikey = inchikey;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
    this.precursorData = null;
  }

  public Reachable(String inchi, String smiles, String structureFilename, List<String> names, String wordCloudFilename, PrecursorData precursors) {
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

  class PrecursorData {
    private class Precusor {
      @JsonProperty("precursor_inchis")
      private List<String> precursorMolcules;
      @JsonProperty("source")
      private List<String> sources;
    }

    @JsonProperty("prediction_precusors")
    private List<Precusor> precusors;


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
