package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.biojava.nbio.core.sequence.AccessionID;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenbankSeqEntry extends SequenceEntry {
  private ProteinSequence seqObject;
  private MongoDB db;
  private DBObject metadata;
  private String accession;
  private List<String> pmids;
  private String sequence;
  private String geneName;
  private String productName;
  private List<String> geneSynonyms;
  private String org;
  private Long org_id;
  private String ec;
  private Set<Long> catalyzedRxns;
  private Set<Long> catalyzedSubstratesDiverse, catalyzedSubstratesUniform;
  private Set<Long> catalyzedProductsDiverse, catalyzedProductsUniform;
  private HashMap<Long, Set<Long>> catalyzedRxnsToSubstrates, catalyzedRxnsToProducts;
  private SAR sar;


  public GenbankSeqEntry(ProteinSequence sequence, MongoDB db) {
    this.db = db;
    this.seqObject = sequence;
    this.ec = extractEc();

    if (this.ec != null) {
      this.accession = extractAccession();
      this.geneName = extractGeneName();
      this.geneSynonyms = extractGeneSynonyms();
      this.productName = extractProductName();
      this.metadata = extractMetadata();
      this.sequence = extractSequence();
      this.org = extractOrg();
      this.org_id = extractOrgId();
      extractCatalyzedReactions();
    }
  }

  DBObject getMetadata() { return this.metadata; }
  String getAccessions() { return this.accession; }
  public String getGeneName() { return this.geneName; }
  public List<String> getGeneSynonyms() { return this.geneSynonyms; }
  public String getProductName() { return this.productName; }
  List<String> getPmids() { return this.pmids; }
  Long getOrgId() { return this.org_id; }
  String getOrg() { return this.org; }
  String getSeq() { return this.sequence; }
  public String getEc() { return this.ec; }
  Set<Long> getCatalyzedRxns() { return this.catalyzedRxns; }
  Set<Long> getCatalyzedSubstratesUniform() { return this.catalyzedSubstratesUniform; }
  Set<Long> getCatalyzedSubstratesDiverse() { return this.catalyzedSubstratesDiverse; }
  Set<Long> getCatalyzedProductsUniform() { return this.catalyzedProductsUniform; }
  Set<Long> getCatalyzedProductsDiverse() { return this.catalyzedProductsDiverse; }
  HashMap<Long, Set<Long>> getCatalyzedRxnsToSubstrates() { return this.catalyzedRxnsToSubstrates; }
  HashMap<Long, Set<Long>> getCatalyzedRxnsToProducts() { return this.catalyzedRxnsToProducts; }
  SAR getSar() { return this.sar; }

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
    return seqObject.getSequenceAsString();
  }

  public String extractEc() {
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seqObject.getFeatures();
    for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
      if (feature.getType().equals("Protein")) {
        Map<String, List<Qualifier>> qualifier_map = feature.getQualifiers();
        if (qualifier_map.containsKey("EC_number")) {
          return qualifier_map.get("EC_number").get(0).getValue();
        } else {
          return null;
        }
      }
    }
    return null;
  }

  public String extractOrg() {
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seqObject.getFeatures();
    for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
      if (feature.getType().equals("source")) {
        Map<String, List<Qualifier>> qualifier_map = feature.getQualifiers();
        return qualifier_map.get("organism").get(0).getValue();
      }
    }
    return null;
  }

  public Long extractOrgId() {
    return db.getOrganismId(org);
  }

  public String extractAccession() {
    AccessionID accessionID = seqObject.getAccession();
    return accessionID.getID();
  }

  public List<Seq> getSeqs() {
    return db.getSeqFromGenbank(sequence, ec, org);
  }

  public String extractGeneName() {
    String header = seqObject.getOriginalHeader();
    Pattern r = Pattern.compile("(\\S*)\\s*.*");
    Matcher m = r.matcher(header);
    if (m.find()) {
      if (m.group(1).equals(accession))
        return null;
      return m.group(1);
    }

    return null;
  }

  public DBObject extractMetadata() {
    JSONObject obj = new org.json.JSONObject();

    obj.put("proteinExistence", "");
    obj.put("name", geneName);
    obj.put("synonyms", geneSynonyms);
    obj.put("product_name", productName);
    obj.put("comment", "");
    obj.put("accession", accession);

    return MongoDBToJSON.conv(obj);
  }

  public List<String> extractGeneSynonyms() {
    ArrayList<String> gene_synonyms = new ArrayList<>();
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seqObject.getFeatures();
    for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
      if (feature.getType().equals("Protein")) {
        Map<String, List<Qualifier>> qualifier_map = feature.getQualifiers();
        if (qualifier_map.containsKey("gene_synonym")) {
          for (Qualifier qualifier : qualifier_map.get("gene_synonym")) {
            gene_synonyms.add(qualifier.getValue());
          }
        }
      }
    }
    return gene_synonyms;
  }

  public String extractProductName() {
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seqObject.getFeatures();
    for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
      if (feature.getType().equals("Protein")) {
        Map<String, List<Qualifier>> qualifier_map = feature.getQualifiers();
        if (qualifier_map.containsKey("product")) {
          return qualifier_map.get("product").get(0).getValue();
        } else {
          return null;
        }
      }
    }
    return null;
  }
}
