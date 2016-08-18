package act.shared;

import act.shared.helpers.MongoDBToJSON;
import com.mongodb.DBObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

  private String geneName;
  private Set<String> uniprotAccs;
  private Set<String> genbankProtAccs;
  private Set<String> genbankNucAccs;
  private Set<String> brendaIDs;
  private Set<String> synonyms;
  private Set<String> productNames;
  private String catalyticActivity;

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
    this.geneName        = meta(this.metadata, new String[] { "name" });
    if (this.metadata.has("xref")) {
      JSONObject xrefObject = metadata.getJSONObject("xref");

      if (xrefObject.has("brenda_id")) {
        this.brendaIDs = parseJSONArray(xrefObject.getJSONArray("brenda_id"));
      }
    }
    if (this.metadata.has("accession")) {
      // accounts for new structure of accessions in seq collection
      JSONObject accessions = metadata.getJSONObject("accession");

      if (accessions.has("uniprot")) {
        this.uniprotAccs = parseJSONArray(accessions.getJSONArray("uniprot"));
      }

      if (accessions.has("genbank_protein")) {
        this.genbankProtAccs = parseJSONArray(accessions.getJSONArray("genbank_protein"));
      }

      if (accessions.has("genbank_nucleotide")) {
        this.genbankNucAccs = parseJSONArray(accessions.getJSONArray("genbank_nucleotide"));
      }
    }
    if (this.metadata.has("product_names"))
      this.productNames = parseJSONArray((JSONArray) this.metadata.get("product_names"));
    if (this.metadata.has("synonyms"))
      this.synonyms = parseJSONArray((JSONArray) this.metadata.get("synonyms"));
    if (this.metadata.has("catalytic_activity"))
      this.catalyticActivity = this.metadata.getString("catalytic_activity");

    this.reactionsCatalyzed = new HashSet<Long>();
  }

  public static Seq rawInit(
    // the first set of arguments are the same as the constructor
    long id, String e, Long oid, String o, String s, List<JSONObject> r, DBObject m, AccDB d,
    // the next set of arguments are the ones that are typically "constructed"
    // but here, passed as raw input, e.g., when reading directly from db
    Set<Long> rxns
  ) {
    Seq seq = new Seq(id, e, oid, o, s, r, m, d);

    // overwrite from the raw data provided "as-is"
    seq.reactionsCatalyzed = rxns;

    return seq;
  }

  static final String not_found = "";
  private String meta(JSONObject o, String[] xpath) {
    Set<String> ret = meta(o, xpath, true);
    if (ret.size() > 0) {
      return ret.iterator().next();
    } else {
      return not_found;
    }
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
  public String getSequence() { return this.sequence; }
  public String getEc() { return this.ecnum; }
  public String getOrgName() { return this.organism; }
  public Long getOrgId() {
    return this.organismIDs;
  }
  public List<JSONObject> getReferences() { return this.references; }
  public JSONObject getMetadata() { return this.metadata; }
  public void setMetadata(JSONObject metadata) { this.metadata = metadata; }
  public Set<String> getProductNames() { return this.productNames; }
  public Set<String> getSynonyms() { return this.synonyms; }
  public String getCatalyticActivity() {return this.catalyticActivity; }
  public String getGeneName() { return this.geneName; }
  public Set<String> getUniprotAccession() { return this.uniprotAccs; }
  public Set<String> getGenbankProteinAccession() { return this.genbankProtAccs; }
  public Set<String> getGenbankNucleotideAccession() { return this.genbankNucAccs; }
  public Set<String> getBrendaIDs() { return this.brendaIDs; }
  public AccDB getSrcdb() { return this.srcdb; }

  public void addReactionsCatalyzed(Long r) { this.reactionsCatalyzed.add(r); }
  public Set<Long> getReactionsCatalyzed() { return this.reactionsCatalyzed; }

  public void setReferences(List<JSONObject> refs) { this.references = refs; }
  public void setReactionsCatalyzed(Set<Long> reactionsCatalyzed) { this.reactionsCatalyzed = reactionsCatalyzed; }
  public void set_organism_name(String orgName) { this.organism = orgName; }
  public void setOrgId(Long orgId) { this.organismIDs = orgId; }
}


