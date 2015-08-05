package act.installer.sequence;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import act.server.SQLInterface.MongoDB;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;

import com.mongodb.DBObject;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.XML;
import org.json.JSONException;

public class SwissProtEntry extends SequenceEntry {
  JSONObject data;

  public static Set<SequenceEntry> parsePossiblyMany(String uniprot_xml) {
    Set<SequenceEntry> all_entries = new HashSet<SequenceEntry>();
    try {
      JSONObject jo = XML.toJSONObject(uniprot_xml);
      Object parsed = ((JSONObject)jo.get("uniprot")).get("entry");
      // parsed comes out with structure: jo.uniprot.entry: [ {gene_entries} ] or {gene_entry} if single
      JSONArray entries; 
      if (parsed instanceof JSONArray)
        entries = (JSONArray)parsed;
      else
        entries = new JSONArray(new JSONObject[] { (JSONObject)parsed }); // manually wrap 
      for (int i = 0; i < entries.length(); i++) {
        JSONObject gene_entry = entries.getJSONObject(i);
        try { 
          all_entries.add(new SwissProtEntry(gene_entry)); 
        } catch (JSONException je) { }
      }
    } catch (JSONException je) {
      System.out.println("Failed SwissProt parse: " + je.toString() + " XML: " + uniprot_xml);
    }
    return all_entries;
  }

  private SwissProtEntry(JSONObject gene_entry) {
    this.data = gene_entry;
  }
  
  String get_ec() {
    // data.dbReference.[{id:x.x.x.x, type:"EC"}...]
    return lookup_ref(this.data, "EC");
  }

  DBObject get_metadata() {
    return MongoDBToJSON.conv(this.data);
  }

  Set<Long> get_catalyzed_rxns() {
    // optionally add reactions to actfamilies by processing 
    // "catalytic activity" annotations and then return those 
    // catalyzed reaction ids (Long _id of actfamilies). This 
    // function SHOULD NOT infer which actfamilies refer to 
    // this object, as that is done in map_seq install.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_substrates_diverse() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_products_diverse() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_substrates_uniform() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  Set<Long> get_catalyzed_products_uniform() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashSet<Long>();
  }

  HashMap<Long, Set<Long>> get_catalyzed_rxns_to_substrates() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashMap<Long, Set<Long>>();
  }

  HashMap<Long, Set<Long>> get_catalyzed_rxns_to_products() {
    // see comment in get_catalyzed_rxns for the function here
    // when we want to NLP/parse out the "catalysis activity"
    // field, we will return that here.
    return new HashMap<Long, Set<Long>>();
  }

  SAR get_sar() {
    // sar is computed later; using "initdb infer_sar"
    // for now add the empty sar constraint set
    return new SAR();
  }

  List<String> get_pmids() {
    // data.reference.[ {citation: {type: "journal article", dbReference.{id:, type:PubMed}, title:XYA } ... } .. ]
    List<String> pmids = new ArrayList<String>();
    JSONArray refs = possible_list(this.data.get("reference"));
    for (int i = 0; i<refs.length(); i++) {
      JSONObject citation = (JSONObject)((JSONObject)refs.get(i)).get("citation");
      if (citation.get("type").equals("journal article")) {
        String id = lookup_ref(citation, "PubMed");
        if (id != null) pmids.add(id); 
      }
    }
    return pmids;
  }

  Long get_org_id() {
    // data.organism.dbReference.{id: 9606, type: "NCBI Taxonomy"}
    String id = lookup_ref(this.data.get("organism"), "NCBI Taxonomy");
    if (id == null) return null;
    return Long.parseLong(id);
  }

  String get_seq() {
    // data.sequence.content: "MSTAGKVIKCKAAV.."
    return (String)((JSONObject)this.data.get("sequence")).get("content");
  }

  private String lookup_ref(Object o, String typ) {
    // o.dbReference.{id: 9606, type: typ}
    // o.dbReference.[{id: x.x.x.x, type: typ}]
    JSONObject x = (JSONObject)o;
    if (!x.has("dbReference"))
      return null;

    JSONArray set = possible_list(x.get("dbReference"));

    for (int i = 0; i<set.length(); i++) {
      JSONObject entry = set.getJSONObject(i);
      if (typ.equals(entry.get("type"))) {
        return entry.get("id").toString();
      }
    }

    return null; // did not find the requested type; not_found indicated by null
  }

  private JSONArray possible_list(Object o) {
    JSONArray l = null;
    if (o instanceof JSONObject) {
      l = new JSONArray();
      l.put(o);
    } else if (o instanceof JSONArray) {
      l = (JSONArray) o;
    } else {
      System.out.println("Json object is neither an JSONObject nor a JSONArray. Abort.");
      System.exit(-1);
    }
    return l;
  }

  @Override
  public String toString() {
    return this.data.toString(2); // format it with 2 spaces
  }
}
