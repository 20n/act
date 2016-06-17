package act.installer.sequence;

import act.server.MongoDB;
import act.shared.Seq;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.biojava.nbio.core.sequence.AccessionID;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenbankSeqEntry extends SequenceEntry {
  ProteinSequence seq_object;
  DBObject metadata;
  static String accession;
  List<String> pmids;
  static String sequence;
  static String org;
  Long org_id;
  static String ec;
  Set<Long> catalyzed_rxns;
  Set<Long> catalyzed_substrates_diverse, catalyzed_substrates_uniform;
  Set<Long> catalyzed_products_diverse, catalyzed_products_uniform;
  HashMap<Long, Set<Long>> catalyzed_rxns_to_substrates, catalyzed_rxns_to_products;
  SAR sar;


  public GenbankSeqEntry(ProteinSequence sequence) {
    this.seq_object = sequence;
//    this.metadata = extract_metadata();
    this.accession = extract_accession();
//    this.pmids = extract_pmids();
    this.sequence = extract_sequence();
//    this.org_id = extract_org_id();
    this.org = extract_org();
    this.ec = extract_ec();
    extract_catalyzed_reactions();
  }

  DBObject get_metadata() { return this.metadata; }
  String get_accessions() { return this.accession; }
  List<String> get_pmids() { return this.pmids; }
  Long get_org_id() { return this.org_id; }
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
        return qualifier_map.get("EC_number").get(0).getValue();
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

  public String extract_accession() {
    AccessionID accessionID = seq_object.getAccession();
    return accessionID.getID();
  }


  public static List<Seq> getSeq(MongoDB db) {
//    return db.getSeqFromGenbank(sequence, ec, org);
    return db.getSeqFromGenbank(accession);
  }

  public static void main(String[] args) {
    MongoDB db = new MongoDB();
    List<Seq> seqs = getSeq(db);
    for (Seq seq : seqs) {
      System.out.println(seq.get_sequence());
    }
  }

}