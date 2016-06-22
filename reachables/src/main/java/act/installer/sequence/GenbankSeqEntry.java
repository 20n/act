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
  ProteinSequence seq_object;
  DBObject metadata;
  String accession;
  List<String> pmids;
  String sequence;
  String gene_name;
  String product_name;
  List<String> gene_synonyms;
  String org;
  Long org_id;
  public String ec;
  Set<Long> catalyzed_rxns;
  Set<Long> catalyzed_substrates_diverse, catalyzed_substrates_uniform;
  Set<Long> catalyzed_products_diverse, catalyzed_products_uniform;
  HashMap<Long, Set<Long>> catalyzed_rxns_to_substrates, catalyzed_rxns_to_products;
  SAR sar;


  public GenbankSeqEntry(ProteinSequence sequence) {
    this.seq_object = sequence;
    this.ec = extract_ec();

    if (this.ec != null) {
      MongoDB db = new MongoDB("localhost", 27017, "marvin");

      this.accession = extract_accession();
      this.gene_name = extract_gene_name();
      this.gene_synonyms = extract_gene_synonyms();
      this.product_name = extract_product_name();
      this.metadata = extract_metadata();
//    this.pmids = extract_pmids();
      this.sequence = extract_sequence();
      this.org = extract_org();
      this.org_id = extract_org_id(db);
      extract_catalyzed_reactions();
    }
  }

  DBObject get_metadata() { return this.metadata; }
  String get_accessions() { return this.accession; }
  public String get_gene_name() { return this.gene_name; }
  public List<String> get_gene_synonyms() { return this.gene_synonyms; }
  public String get_product_name() { return this.product_name; }
  List<String> get_pmids() { return this.pmids; }
  Long get_org_id() { return this.org_id; }
  String get_org() { return this.org; }
  String get_seq() { return this.sequence; }
  String get_ec() { return this.ec; }
  Set<Long> get_catalyzed_rxns() { return this.catalyzed_rxns; }
  Set<Long> get_catalyzed_substrates_uniform() { return this.catalyzed_substrates_uniform; }
  Set<Long> get_catalyzed_substrates_diverse() { return this.catalyzed_substrates_diverse; }
  Set<Long> get_catalyzed_products_uniform() { return this.catalyzed_products_uniform; }
  Set<Long> get_catalyzed_products_diverse() { return this.catalyzed_products_diverse; }
  HashMap<Long, Set<Long>> get_catalyzed_rxns_to_substrates() { return this.catalyzed_rxns_to_substrates; }
  HashMap<Long, Set<Long>> get_catalyzed_rxns_to_products() { return this.catalyzed_rxns_to_products; }
  SAR get_sar() { return this.sar; }

  void extract_catalyzed_reactions() {
    // optionally add reactions to actfamilies by processing
    // "catalytic activity" annotations and then return those
    // catalyzed reaction ids (Long _id of actfamilies). This
    // function SHOULD NOT infer which actfamilies refer to
    // this object, as that is done in map_seq install.
    this.catalyzed_rxns = new HashSet<Long>();
    this.catalyzed_substrates_uniform = new HashSet<Long>();
    this.catalyzed_substrates_diverse = new HashSet<Long>();
    this.catalyzed_products_diverse = new HashSet<Long>();
    this.catalyzed_products_uniform = new HashSet<Long>();
    this.catalyzed_rxns_to_substrates = new HashMap<Long, Set<Long>>();
    this.catalyzed_rxns_to_products = new HashMap<Long, Set<Long>>();
    this.sar = new SAR();
  }

  public String extract_sequence() {
    return seq_object.getSequenceAsString();
  }

  public String extract_ec() {
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seq_object.getFeatures();
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

  public String extract_org() {
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seq_object.getFeatures();
    for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
      if (feature.getType().equals("source")) {
        Map<String, List<Qualifier>> qualifier_map = feature.getQualifiers();
        return qualifier_map.get("organism").get(0).getValue();
      }
    }
    return null;
  }

  public Long extract_org_id(MongoDB db) {
    return db.getOrganismId(org);
  }

  public String extract_accession() {
    AccessionID accessionID = seq_object.getAccession();
    return accessionID.getID();
  }

  public List<Seq> getSeqs(MongoDB db) {
    return db.getSeqFromGenbank(sequence, ec, org);
  }

  public String extract_gene_name() {
    String header = seq_object.getOriginalHeader();
    Pattern r = Pattern.compile("(\\S*)\\s*.*");
    Matcher m = r.matcher(header);
    if (m.find()) {
      if (m.group(1).equals(accession))
        return null;
      return m.group(1);
    }

    return null;
  }

  public DBObject extract_metadata() {
    JSONObject obj = new org.json.JSONObject();

    obj.put("proteinExistence", "");
    obj.put("name", gene_name);
    obj.put("synonyms", gene_synonyms);
    obj.put("product_name", product_name);
    obj.put("comment", "");
    obj.put("accession", accession);

    return MongoDBToJSON.conv(obj);
  }

  public List<String> extract_gene_synonyms() {
    ArrayList<String> gene_synonyms = new ArrayList<>();
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seq_object.getFeatures();
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

  public String extract_product_name() {
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features = seq_object.getFeatures();
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