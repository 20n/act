package act.installer.reachablesexplorer.l4reachables;

import act.installer.reachablesexplorer.PatentSummary;
import act.installer.reachablesexplorer.PrecursorData;
import act.installer.reachablesexplorer.SynonymData;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class L4Reachable {

  @JsonIgnore
  static final String inchiFieldName = "InChI";

  @JsonProperty("_id")
  private Integer id;
  private String pageName;
  @JsonProperty("InChI")
  private String inchi;
  private String smiles;
  private String inchiKey;
  private String structureFilename;
  private PrecursorData precursorData;
  private SynonymData synonyms;
  private List<PatentSummary> patentSummaries;

  public L4Reachable() {}

  public L4Reachable(Integer id, String pageName, String inchi, String smiles, String inchiKey, String structureFilename, SynonymData synonyms) {
    this(id, pageName, inchi, smiles, inchiKey, structureFilename, null, synonyms, null);
  }

  public List<PatentSummary> getPatentSummaries() {
    return patentSummaries;
  }

  public void setPatentSummaries(List<PatentSummary> patentSummaries) {
    this.patentSummaries = patentSummaries;
  }

  public L4Reachable(Integer id, String pageName, String inchi, String smiles, String inchiKey, String structureFilename, PrecursorData precursorData, SynonymData synonyms, List<PatentSummary> patentSummaries) {
    this.id = id;
    this.pageName = pageName;
    this.inchi = inchi;
    this.smiles = smiles;
    this.inchiKey = inchiKey;
    this.structureFilename = structureFilename;
    this.precursorData = precursorData;
    this.synonyms = synonyms;
    this.patentSummaries = patentSummaries;
  }

  public String getPageName() {
    return pageName;
  }

  @JsonIgnore
  public String getInchi() {
    return inchi;
  }

  public String getSmiles() {
    return smiles;
  }

  public String getInchiKey() {
    return inchiKey;
  }

  public String getStructureFilename() {
    return structureFilename;
  }

  public PrecursorData getPrecursorData() {
    return precursorData;
  }

  public SynonymData getSynonyms() {
    return synonyms;
  }

  public void setPageName(String pageName) {
    this.pageName = pageName;
  }

  @JsonIgnore
  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public void setSmiles(String smiles) {
    this.smiles = smiles;
  }

  public void setInchiKey(String inchiKey) {
    this.inchiKey = inchiKey;
  }

  public void setStructureFilename(String structureFilename) {
    this.structureFilename = structureFilename;
  }

  public void setPrecursorData(PrecursorData precursorData) {
    this.precursorData = precursorData;
  }

  public void setSynonyms(SynonymData synonyms) {
    this.synonyms = synonyms;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }
}
