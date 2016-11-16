package act.installer.reachablesexplorer;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

import java.util.List;

public class Reachable {



  public Reachable(String inchi, String smiles, String structureFilename, List<String> names, String wordCloudFilename) {
    this.inchi = inchi;
    this.smiles = smiles;
    this.structureFilename = structureFilename;
    this.names = names;
    this.wordCloudFilename = wordCloudFilename;
  }

  private class WikipediaData {

    @JsonProperty("url")
    private String url;

    @JsonProperty("text")
    private String text;
  }

  private class SynonymData {
    @JsonProperty("pubchem")
    private List<String> pubchemSynonyms;

    @JsonProperty("mesh")
    private List<String> meshSynonyms;
  }

  @ObjectId
  @JsonProperty("inchi")
  private String inchi;

  @JsonProperty("smiles")
  private String smiles;

  @JsonProperty("rendering-filename")
  private String structureFilename;

  @JsonProperty("names")
  private List<String> names;

  @JsonProperty("wikipedia-data")
  private WikipediaData wikipediaData;

  @JsonProperty("usage-wordcloud-filename")
  private String wordCloudFilename;
}
