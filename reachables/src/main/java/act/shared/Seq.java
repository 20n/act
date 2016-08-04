package act.shared;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class Seq implements Serializable {
  public enum AccDB { genbank, uniprot, swissprot, trembl, embl, ncbi_protein, metacyc, brenda };
  public enum AccType {genbank_nucleotide, genbank_protein, uniprot};
  private static final long serialVersionUID = 42L;
  Seq() { /* default constructor for serialization */ }

  private int id;
  private AccDB srcdb;
  private String sequence;
  private String ecnum;
  private String organism;

  private Long organismIDs;
  private List<JSONObject> references;
  private JSONObject metadata;

  private Set<Long> reactionsCatalyzed;

  private String gene_name;
  private Set<String> uniprot_accs;
  private Set<String> genbank_prot_accs;
  private Set<String> genbank_nuc_accs;
  private Set<String> brenda_ids;
  private Set<String> synonyms;
  private Set<String> product_names;
  private String catalytic_activity;

  private Set<String> keywords;
  private Set<String> caseInsensitiveKeywords;

  public Seq(long id, String e, Long oid, String o, String s, List<JSONObject> r, DBObject m, AccDB d) {
    this.id = (new Long(id)).intValue();
    this.sequence = s;
    this.ecnum = e;
    this.organism = o;
    this.organismIDs = oid;
    this.references = r;
    this.srcdb = d;
    this.metadata = MongoDBToJSON.conv(m); // to JSONObject

    // extracted data from metadata:
    this.gene_name        = meta(this.metadata, new String[] { "name" });
    if (this.metadata.has("xref")) {
      JSONObject xrefObject = metadata.getJSONObject("xref");

      if (xrefObject.has("brenda_id")) {
        this.brenda_ids = parseJSONArray(xrefObject.getJSONArray("brenda_id"));
      }
    }
    if (this.metadata.has("accession")) {
      // accounts for new structure of accessions in seq collection
      JSONObject accessions = metadata.getJSONObject("accession");

      if (accessions.has("uniprot")) {
        this.uniprot_accs = parseJSONArray(accessions.getJSONArray("uniprot"));
      }

      if (accessions.has("genbank_protein")) {
        this.genbank_prot_accs = parseJSONArray(accessions.getJSONArray("genbank_protein"));
      }

      if (accessions.has("genbank_nucleotide")) {
        this.genbank_nuc_accs = parseJSONArray(accessions.getJSONArray("genbank_nucleotide"));
      }
    }
    if (this.metadata.has("product_names"))
      this.product_names = parseJSONArray((JSONArray) this.metadata.get("product_names"));
    if (this.metadata.has("synonyms"))
      this.synonyms = parseJSONArray((JSONArray) this.metadata.get("synonyms"));
    if (this.metadata.has("catalytic_activity"))
      this.catalytic_activity = this.metadata.getString("catalytic_activity");

    this.reactionsCatalyzed = new HashSet<Long>();
  }

  public static Seq rawInit(
    // the first set of arguments are the same as the constructor
    long id, String e, Long oid, String o, String s, List<JSONObject> r, DBObject m, AccDB d,
    // the next set of arguments are the ones that are typically "constructed"
    // but here, passed as raw input, e.g., when reading directly from db
    Set<String> keywords, Set<String> ciKeywords, Set<Long> rxns
  ) {
    Seq seq = new Seq(id, e, oid, o, s, r, m, d);

    // overwrite from the raw data provided "as-is"
    seq.keywords = keywords;
    seq.caseInsensitiveKeywords = ciKeywords;
    seq.reactionsCatalyzed = rxns;

    return seq;
  }

  static final String not_found = "";
  private String meta(JSONObject o, String[] xpath) {
    Set<String> ret = meta(o, xpath, true);
    for (String s : ret) return s;
    return not_found;
  }

  private Set<String> meta(JSONObject o, String[] xpath, boolean returnSet) {
    int len = xpath.length;
    Set<String> not_fnd = new HashSet<String>();
    not_fnd.add(not_found);
    try {
      if (len == 0)
        return not_fnd;

      if (len == 1) {
        Object val = o.get(xpath[0]); // can throw
        Set<String> fnd = new HashSet<String>();
        if (val instanceof String) {
          fnd.add(o.getString(xpath[0])); // can throw
        } else if (val instanceof JSONArray) {
          JSONArray aval = (JSONArray)val;
          for (int i = 0; i<aval.length(); i++) fnd.add(aval.getString(i)); // can throw
        }
        return fnd;
      } else {
        String[] rest = Arrays.copyOfRange(xpath, 1, xpath.length);
        meta(o.getJSONObject(xpath[0]), rest, returnSet);  // can throw
      }
    } catch (JSONException ex) {
      return not_fnd;
    }
    return not_fnd;
  }

  private String meta(JSONObject o, String[] xpath, String field, String should_equal, String extract_field) {
    int len = xpath.length;
    try {
      if (len == 0)
        return not_found;

      if (len == 1) {
        JSONArray a = o.getJSONArray(xpath[0]); // can throw
        // iterate over all objects
        for (int i = 0; i < a.length(); i++) {
          JSONObject oo = a.getJSONObject(i); // can throw
          // if the object has the "field" and its value equals "should_equal"
          if (should_equal.equals(oo.getString(field))) { // can throw
            // return on first match
            return oo.getString(extract_field);
          }
        }
      } else {
        String[] rest = Arrays.copyOfRange(xpath, 1, xpath.length);
        meta(o.getJSONObject(xpath[0]), rest, field, should_equal, extract_field);  // can throw
      }
    } catch (JSONException ex) {
      return not_found;
    }
    return not_found;
  }

  private Set<String> parseJSONArray(JSONArray jArray) {
    Set<String> listdata = new HashSet<>();
    if (jArray != null) {
      for (int i = 0; i < jArray.length(); i++) {
        listdata.add(jArray.get(i).toString());
      }
    }
    return listdata;
  }

  public int getUUID() { return this.id; }
  public String get_sequence() { return this.sequence; }
  public String get_ec() { return this.ecnum; }
  public String get_org_name() { return this.organism; }
  public Long getOrgId() {
    return this.organismIDs;
  }
  public List<JSONObject> get_references() { return this.references; }
  public JSONObject get_metadata() { return this.metadata; }
  public Set<String> get_product_names() { return this.product_names; }
  public Set<String> get_synonyms() { return this.synonyms; }
  public String get_catalytic_activity() {return this.catalytic_activity; }
  public String get_gene_name() { return this.gene_name; }
  public Set<String> get_uniprot_accession() { return this.uniprot_accs; }
  public Set<String> get_genbank_protein_accession() { return this.genbank_prot_accs; }
  public Set<String> get_genbank_nucleotide_accession() { return this.genbank_nuc_accs; }
  public Set<String> get_brenda_ids() { return this.brenda_ids; }
  public AccDB get_srcdb() { return this.srcdb; }

  public Set<String> getKeywords() { return this.keywords; }
  public void addKeyword(String k) { this.keywords.add(k); }
  public Set<String> getCaseInsensitiveKeywords() { return this.caseInsensitiveKeywords; }
  public void addCaseInsensitiveKeyword(String k) { this.caseInsensitiveKeywords.add(k); }
  public void addReactionsCatalyzed(Long r) { this.reactionsCatalyzed.add(r); }
  public Set<Long> getReactionsCatalyzed() { return this.reactionsCatalyzed; }

  public void set_references(List<JSONObject> refs) { this.references = refs; }
  public void setReactionsCatalyzed(Set<Long> reactionsCatalyzed) { this.reactionsCatalyzed = reactionsCatalyzed; }
  public void set_metadata(JSONObject metadata) { this.metadata = metadata; }
  public void set_organism_name(String orgName) { this.organism = orgName; }
  public void setOrgId(Long orgId) { this.organismIDs = orgId; }


  private Set<Long> get_common(Collection<Set<Long>> reactants_across_all_rxns) {
    Set<Long> common = null;
    for (Set<Long> reactants : reactants_across_all_rxns) {
      if (common == null) common = reactants;
      else common = intersect(common, reactants);
    }
    return common;
  }

  private Set<Long> get_diversity(Collection<Set<Long>> reactants_across_all_rxns, Set<Long> common) {
    Set<Long> diversity = new HashSet<Long>();
    for (Set<Long> reactants : reactants_across_all_rxns) {
      Set<Long> not_common = new HashSet<Long>(reactants);
      not_common.removeAll(common);
      diversity.addAll(not_common);
    }
    return diversity;
  }

  public static <X> Set<X> intersect(Set<X> set1, Set<X> set2) {
    boolean set1IsLarger = set1.size() > set2.size();
    Set<X> cloneSet = new HashSet<X>(set1IsLarger ? set2 : set1);
    cloneSet.retainAll(set1IsLarger ? set1 : set2);
    return cloneSet;
  }

}


