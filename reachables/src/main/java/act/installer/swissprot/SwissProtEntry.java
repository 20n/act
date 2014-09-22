package act.installer.swissprot;

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

    // ==== Fields of importance ====
    // ==== See sample.json for example ====
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

  public void writeToDB(MongoDB db) {
    Long org_id = get_org_id();
    String org = org_id != null ? db.getOrganismNameFromId(org_id) : null;
    db.submitToActSeqDB(get_ec(), 
          org, 
          org_id, 
          get_seq(), 
          MongoDBToJSON.conv(this.data));
  }
  
  private String get_ec() {
    // data.dbReference.[{id:x.x.x.x, type:"EC"}...]
    return lookup_ref(this.data, "EC");
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
    Object oo = ((JSONObject)o).get("dbReference");
    JSONArray set = null;
    if (oo instanceof JSONObject) {
      set = new JSONArray();
      set.put(oo);
    } else if (oo instanceof JSONArray) {
      set = (JSONArray) oo;
    } else {
      System.out.println("Attempt to lookup a dbReference in !(JSONO, JSONA). Fail!");
      System.exit(-1);
    }

    for (int i = 0; i<set.length(); i++) {
      JSONObject entry = set.getJSONObject(i);
      if (typ.equals(entry.get("type"))) {
        return entry.get("id").toString();
      }
    }

    return null;
  }
}
