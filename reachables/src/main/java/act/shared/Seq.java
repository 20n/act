package act.shared;

import java.io.Serializable;
import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import act.shared.helpers.MongoDBToJSON;
import com.mongodb.DBObject;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class Seq implements Serializable {
  public enum AccDB { genbank, uniprot, swissprot, trembl, embl };
	private static final long serialVersionUID = 42L;
	Seq() { /* default constructor for serialization */ }

  private int id;
  private AccDB srcdb;
  private String sequence;
  private String ecnum;
  private String organism;

  private Long organismIDs;
  private List<String> references;
  private JSONObject metadata; 
  
  private String gene_name;
  private String uniprot_activity;
  private String evidence;
  private Set<String> uniprot_accs;

  private Set<String> keywords;
  private Set<String> caseInsensitiveKeywords;
  
  public Seq(long id, String e, Long oid, String o, String s, List<String> r, DBObject m, AccDB d) {
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
    this.evidence         = meta(this.metadata, new String[] { "proteinExistence", "type" });
    this.uniprot_activity = meta(this.metadata, new String[] { "comment" }, "type", "catalytic activity", "text"); // comment: [ { "type": "catalytic activity", "text": uniprot_activity_annotation } ] .. extracts the text field
    this.uniprot_accs     = meta(this.metadata, new String[] { "accession" }, true /*return set*/);

    this.keywords = new HashSet<String>();
    this.caseInsensitiveKeywords = new HashSet<String>();
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

  public int getUUID() { return this.id; }
  public String get_ec() { return this.ecnum; }
  public String get_org_name() { return this.organism; }
  public List<String> get_references() { return this.references; }
  public JSONObject get_metadata() { return this.metadata; }
  public String get_gene_name() { return this.gene_name; }
  public String get_evidence() { return this.evidence; }
  public String get_uniprot_activity() { return this.uniprot_activity; }
  public Set<String> get_uniprot_accession() { return this.uniprot_accs; }
 
  public Set<String> getKeywords() { return this.keywords; }
  public void addKeyword(String k) { this.keywords.add(k); }
  public Set<String> getCaseInsensitiveKeywords() { return this.caseInsensitiveKeywords; }
  public void addCaseInsensitiveKeyword(String k) { this.caseInsensitiveKeywords.add(k); }
}
