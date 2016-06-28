package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.biojava.nbio.core.sequence.template.Compound;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenbankSeqEntry extends SequenceEntry {
  private final List<String> accessionSource = Arrays.asList("genbank");
  private final String proteinSeqType = "Protein";
  private final String dnaSeqType = "DNA";

  private AbstractSequence seqObject;
  private MongoDB db;
  private Map<String, List<Qualifier>> cdsQualifierMap;
  private String seqType;
  private DBObject metadata;
  private List<String> accession;
  private List<String> nucleotideAccession;
  private List<JSONObject> pmids;
  private String sequence;
  private String geneName;
  private List<String> productNames;
  private List<String> geneSynonyms;
  private String org;
  private Long org_id;
  private String ec;
  private Set<Long> catalyzedRxns;
  private Set<Long> catalyzedSubstratesDiverse, catalyzedSubstratesUniform;
  private Set<Long> catalyzedProductsDiverse, catalyzedProductsUniform;
  private HashMap<Long, Set<Long>> catalyzedRxnsToSubstrates, catalyzedRxnsToProducts;
  private SAR sar;


  public GenbankSeqEntry(AbstractSequence sequence, MongoDB db) {
    this.seqObject = sequence;
    this.db = db;
    this.seqType = proteinSeqType;
    init();
  }

  public GenbankSeqEntry(AbstractSequence sequence, Map<String, List<Qualifier>> cdsQualifierMap, MongoDB db) {
    this.seqObject = sequence;
    this.db = db;
    this.seqType = dnaSeqType;
    this.cdsQualifierMap = cdsQualifierMap;
    init();
  }

  public void init() {
    this.ec = extractEc();

    if (this.ec != null) {
      this.accession = extractAccession();
      this.geneName = extractGeneName();
      this.geneSynonyms = extractGeneSynonyms();
      this.productNames = extractProductName();
      this.nucleotideAccession = extractNucleotideAccession();
      this.metadata = extractMetadata();
      this.sequence = extractSequence();
      this.org = extractOrg();
      this.org_id = extractOrgId();
      this.pmids = extractPmids();
      extractCatalyzedReactions();
    }
  }

  public DBObject getMetadata() { return this.metadata; }
  public List<String> getAccession() { return this.accession; }
  public List<String> getNucleotideAccession() { return this.nucleotideAccession; }
  public List<String> getAccessionSource() { return this.accessionSource; }
  public String getGeneName() { return this.geneName; }
  public List<String> getGeneSynonyms() { return this.geneSynonyms; }
  public List<String> getProductName() { return this.productNames; }
  public List<JSONObject> getPmids() { return this.pmids; }
  public Long getOrgId() { return this.org_id; }
  public String getOrg() { return this.org; }
  public String getSeq() { return this.sequence; }
  public String getEc() { return this.ec; }
  public Set<Long> getCatalyzedRxns() { return this.catalyzedRxns; }
  public Set<Long> getCatalyzedSubstratesUniform() { return this.catalyzedSubstratesUniform; }
  public Set<Long> getCatalyzedSubstratesDiverse() { return this.catalyzedSubstratesDiverse; }
  public Set<Long> getCatalyzedProductsUniform() { return this.catalyzedProductsUniform; }
  public Set<Long> getCatalyzedProductsDiverse() { return this.catalyzedProductsDiverse; }
  public HashMap<Long, Set<Long>> getCatalyzedRxnsToSubstrates() { return this.catalyzedRxnsToSubstrates; }
  public HashMap<Long, Set<Long>> getCatalyzedRxnsToProducts() { return this.catalyzedRxnsToProducts; }
  public SAR getSar() { return this.sar; }

  private void extractCatalyzedReactions() {
    this.catalyzedRxns = new HashSet<Long>();
    this.catalyzedSubstratesUniform = new HashSet<Long>();
    this.catalyzedSubstratesDiverse = new HashSet<Long>();
    this.catalyzedProductsDiverse = new HashSet<Long>();
    this.catalyzedProductsUniform = new HashSet<Long>();
    this.catalyzedRxnsToSubstrates = new HashMap<Long, Set<Long>>();
    this.catalyzedRxnsToProducts = new HashMap<Long, Set<Long>>();
    this.sar = new SAR();
  }

  private String extractSequence() {
    if (seqType.equals(proteinSeqType)) {
      return seqObject.getSequenceAsString();
    } else if (seqType.equals(dnaSeqType)) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey("translation")) {
        return cdsQualifierMap.get("translation").get(0).getValue();
      }
    }

    return null;
  }

  private String extractEc() {
    Map<String, List<Qualifier>> qualifierMap = null;

    if (seqType.equals(proteinSeqType)) {
      qualifierMap = getQualifierMap(proteinSeqType);
    } else if (seqType.equals(dnaSeqType)) {
      qualifierMap = cdsQualifierMap;
    }

    if (qualifierMap != null && qualifierMap.containsKey("EC_number")) {
      return qualifierMap.get("EC_number").get(0).getValue();
    } else {
      return null;
    }
  }

  private List<JSONObject> extractPmids() {
    List<String> pmids = seqObject.getPMIDS();
    List<JSONObject> references = new ArrayList<>();

    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("val", pmid);
      obj.put("src", "PMID");
      references.add(obj);
    }

    return references;
  }

  private String extractOrg() {
    Map<String, List<Qualifier>> qualifierMap = getQualifierMap("source");

    if (qualifierMap != null && qualifierMap.containsKey("organism")) {
      return qualifierMap.get("organism").get(0).getValue();
    }

    return null;
  }

  private Long extractOrgId() {
    return db.getOrganismId(org);
  }

  private List<String> extractAccession() {
    if (seqType.equals(proteinSeqType)) {
      return Arrays.asList(seqObject.getAccession().getID());
    } else if (seqType.equals(dnaSeqType)) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey("protein_id")) {
        String[] split_id = cdsQualifierMap.get("protein_id").get(0).getValue().split("\\.");
        return Arrays.asList(split_id[0]);
      }
    }

    return null;
  }

  private List<String> extractNucleotideAccession() {
    if (seqType.equals(dnaSeqType)) {
      return Arrays.asList(seqObject.getAccession().getID());
    } else {
      return null;
    }
  }

  List<Seq> getSeqs() {
    return db.getSeqFromGenbank(sequence, ec, org);
  }

  private String extractGeneName() {
    if (seqType.equals(proteinSeqType)) {
      String header = seqObject.getOriginalHeader();
      Pattern r = Pattern.compile("(\\S*)\\s*.*");
      Matcher m = r.matcher(header);
      if (m.find()) {
        // some cases where genbank files have accession id's in the place of the gene name in the header of the file
        if (m.group(1).equals(accession.get(0))) {
          return "";
        }
        return m.group(1);
      }
    } else if (seqType.equals(dnaSeqType)) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey("gene")) {
        return cdsQualifierMap.get("gene").get(0).getValue();
      }
    }

    return "";
  }

  private DBObject extractMetadata() {
    JSONObject obj = new org.json.JSONObject();

    obj.put("proteinExistence", new org.json.JSONObject());
    obj.put("name", geneName);
    obj.put("synonyms", geneSynonyms);
    obj.put("product_names", productNames);
    obj.put("comment", new ArrayList());
    obj.put("accession", accession);
    obj.put("nucleotide_accession", nucleotideAccession);
    obj.put("accession_source", accessionSource);

    return MongoDBToJSON.conv(obj);
  }

  private List<String> extractGeneSynonyms() {
    ArrayList<String> gene_synonyms = new ArrayList<>();

    if (seqType.equals(proteinSeqType)) {
      Map<String, List<Qualifier>> qualifierMap = getQualifierMap("Protein");
      if (qualifierMap != null && qualifierMap.containsKey("gene_synonym")) {
        for (Qualifier qualifier : qualifierMap.get("gene_synonym")) {
          gene_synonyms.add(qualifier.getValue());
        }
      }

      qualifierMap = getQualifierMap("gene");
      if (qualifierMap != null) {
        if (qualifierMap.containsKey("gene")) {
          for (Qualifier qualifier : qualifierMap.get("gene")) {
            gene_synonyms.add(qualifier.getValue());
          }
        }

        if (qualifierMap.containsKey("gene_synonym")) {
          for (Qualifier qualifier : qualifierMap.get("gene_synonym")) {
            gene_synonyms.add(qualifier.getValue());
          }
        }
      }
    }

    return gene_synonyms;
  }

  private List<String> extractProductName() {
    Map<String, List<Qualifier>> qualifierMap = null;

    if (seqType.equals(proteinSeqType)) {
      qualifierMap = getQualifierMap("Protein");
    } else if (seqType.equals(dnaSeqType)) {
      qualifierMap = cdsQualifierMap;
    }

    if (qualifierMap != null && qualifierMap.containsKey("product")) {
      return Arrays.asList(qualifierMap.get("product").get(0).getValue());
    }

    return null;
  }

  private Map<String, List<Qualifier>> getQualifierMap(String feature_type) {
    List<FeatureInterface<AbstractSequence<Compound>, Compound>> features = seqObject.getFeatures();
    for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
      if (feature.getType().equals(feature_type)) {
        return feature.getQualifiers();
      }
    }
    return null;
  }
}
