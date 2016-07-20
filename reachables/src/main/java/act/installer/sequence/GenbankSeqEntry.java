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

  private AbstractSequence seqObject;
  private MongoDB db;
  private Map<String, List<Qualifier>> cdsQualifierMap;
  private String seq_type;
  private DBObject metadata;
  private List<String> accession;
  private List<String> nucleotideAccession;
  private List<String> pmids;
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
    this.seq_type = "Protein";
    init();
  }

  public GenbankSeqEntry(AbstractSequence sequence, Map<String, List<Qualifier>> cdsQualifierMap, MongoDB db) {
    this.seqObject = sequence;
    this.db = db;
    this.seq_type = "DNA";
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
  public List<String> getPmids() { return this.pmids; }
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

  void extractCatalyzedReactions() {
    this.catalyzedRxns = new HashSet<Long>();
    this.catalyzedSubstratesUniform = new HashSet<Long>();
    this.catalyzedSubstratesDiverse = new HashSet<Long>();
    this.catalyzedProductsDiverse = new HashSet<Long>();
    this.catalyzedProductsUniform = new HashSet<Long>();
    this.catalyzedRxnsToSubstrates = new HashMap<Long, Set<Long>>();
    this.catalyzedRxnsToProducts = new HashMap<Long, Set<Long>>();
    this.sar = new SAR();
  }

  public String extractSequence() {
    if (seq_type.equals("Protein"))
      return seqObject.getSequenceAsString();
    else if (seq_type.equals("DNA")) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey("translation"))
        return cdsQualifierMap.get("translation").get(0).getValue();
    }

    return null;
  }

  public String extractEc() {
    Map<String, List<Qualifier>> qualifierMap = null;

    if (seq_type.equals("Protein"))
      qualifierMap = getQualifierMap("Protein");
    else if (seq_type.equals("DNA"))
      qualifierMap = cdsQualifierMap;

    if (qualifierMap != null && qualifierMap.containsKey("EC_number")) {
      return qualifierMap.get("EC_number").get(0).getValue();
    } else {
      return null;
    }
  }

  public String extractOrg() {
    Map<String, List<Qualifier>> qualifierMap = getQualifierMap("source");

    if (qualifierMap != null && qualifierMap.containsKey("organism"))
      return qualifierMap.get("organism").get(0).getValue();

    return null;
  }

  public Long extractOrgId() {
    return db.getOrganismId(org);
  }

  public List<String> extractAccession() {
    if (seq_type.equals("Protein"))
      return Arrays.asList(seqObject.getAccession().getID());
    else if (seq_type.equals("DNA")){
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey("protein_id")) {
        String[] split_id = cdsQualifierMap.get("protein_id").get(0).getValue().split("\\.");
        return Arrays.asList(split_id[0]);
      }
    }

    return null;
  }

  public List<String> extractNucleotideAccession() {
    if (seq_type.equals("DNA"))
      return Arrays.asList(seqObject.getAccession().getID());
    else
      return null;
  }

  public List<Seq> getSeqs() {
    return db.getSeqFromGenbank(sequence, ec, org);
  }

  public String extractGeneName() {
    if (seq_type.equals("Protein")) {
      String header = seqObject.getOriginalHeader();
      Pattern r = Pattern.compile("(\\S*)\\s*.*");
      Matcher m = r.matcher(header);
      if (m.find()) {
        if (m.group(1).equals(accession.get(0)))
          return "";
        return m.group(1);
      }
    } else if (seq_type.equals("DNA")) {
      if (cdsQualifierMap != null && cdsQualifierMap.containsKey("gene")) {
        return cdsQualifierMap.get("gene").get(0).getValue();
      }
    }

    return "";
  }

  public DBObject extractMetadata() {
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

  public List<String> extractGeneSynonyms() {
    ArrayList<String> gene_synonyms = new ArrayList<>();

    if (seq_type.equals("Protein")) {
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

  public List<String> extractProductName() {
    Map<String, List<Qualifier>> qualifierMap = null;

    if (seq_type.equals("Protein"))
      qualifierMap = getQualifierMap("Protein");
    else if (seq_type.equals("DNA"))
      qualifierMap = cdsQualifierMap;

    if (qualifierMap != null && qualifierMap.containsKey("product"))
      return Arrays.asList(qualifierMap.get("product").get(0).getValue());

    return null;
  }

  private Map<String, List<Qualifier>> getQualifierMap(String feature_type) {
    List<FeatureInterface<AbstractSequence<Compound>, Compound>> features = seqObject.getFeatures();
    for (FeatureInterface<AbstractSequence<Compound>, Compound> feature : features) {
      if (feature.getType().equals(feature_type))
        return feature.getQualifiers();
    }
    return null;
  }
}
