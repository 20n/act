package act.installer.swissprot;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import act.server.SQLInterface.MongoDB;
import act.shared.helpers.MongoDBToJSON;

import org.json.JSONObject;
import org.json.JSONArray;

public class SwissProtEntry {
  JSONObject data;
  
  SwissProtEntry(JSONObject gene_entry) {
    this.data = gene_entry;
  }

  public void writeToDB(MongoDB db) {
    Long org_id = get_org_id();
    String org = org_id != null ? db.getOrganismNameFromId(org_id) : null;
    // note that we install the full data as "metadata" in the db
    // what we extract here are just things we might want to use are keys
    // and join them against other collections.. e.g., (ec+org+pmid) can
    // be used to assign sequences to brenda actfamilies
    db.submitToActSeqDB(
          get_ec(), 
          org, org_id, 
          get_seq(), 
          get_pmids(),
          MongoDBToJSON.conv(this.data));

    // ==== Fields of importance ====
    // (See sample.json for example)
    // data.sequence.content: "MSTAGKVIKCKAAV.."
    // data.organism.dbReference.{id: 9606, type: "NCBI Taxonomy"}
    // data.organism.name{[{content:Homo sapiens, type:scientific}, {content: Human, type: common}]
    // data.proteinExistence: { type: "evidence at protein level" }
    // data.gene.name: [{content: ADH1B, type: primary}, {content: ADH2, type: synonym}]
    // data.name: "ADH1B_HUMAN"
    // data.protein.recommendedName.fullName: "Alcohol dehydrogenase 1B"
    // data.protein.recommendedName.ecNumber: 1.1.1.1
    // data.accession: [ list of acc#s ]
    // 
    // data.reference.[ {citation: {type: "journal article", dbReference.{id:, type:PubMed}, title:XYA } ... ]
    // data.reference.[ {citation: {type: "submission", db:"EMBL/Genbank/DDBJ databases" } ... ]
    //
    // data.dbReference.[{id:x.x.x.x, type:"EC"}...]
    // data.dbReference.[{id:REACT_34, type: Reactome}]
    // data.dbReference.[{id:MetaCyc:MONOMER66-321, type: BioCyc}]
    // also GO, Pfam
    //
    // data.comment.[{ type:"catalytic activity", text: "An alcohol + NAD(+) = an aldehyde or ketone + NADH." }..]
    //
    // data.feature: descriptive notations on sublocation's functions
    // data.comment: extra notes
  }
  
  private String get_ec() {
    // data.dbReference.[{id:x.x.x.x, type:"EC"}...]
    return lookup_ref(this.data, "EC");
  }

  private List<String> get_pmids() {
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

  private Long get_org_id() {
    // data.organism.dbReference.{id: 9606, type: "NCBI Taxonomy"}
    String id = lookup_ref(this.data.get("organism"), "NCBI Taxonomy");
    if (id == null) return null;
    return Long.parseLong(id);
  }

  private String get_seq() {
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
}
