package act.installer.swissprot;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import com.mongodb.DBObject;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.XML;
import act.shared.helpers.MongoDBToJSON;
import act.shared.helpers.P;

public class GenBankEntry extends SequenceEntry {
  JSONObject data;
  JSONObject desc;

  public static Set<P<JSONObject, JSONObject>> get_seq_entry_objs(JSONObject root) {
    // differentiates between multiple entries (CASE 1) and single entry (CASE 2)
    //
    // CASE 1:
    // {"Bioseq-set": {"Bioseq-set_seq-set": {"Seq-entry": {"Seq-entry_set": {"Bioseq-set": {
    //     "Bioseq-set_annot": {...}
    //     "Bioseq-set_seq-set": {"Seq-entry": [
    //         {"Seq-entry_seq": {"Bioseq": {
    // CASE 2:
    // {"Bioseq-set": {"Bioseq-set_seq-set": {"Seq-entry": {"Seq-entry_seq": {"Bioseq": {  
    // The way we do that is to:
    // a. traverse Bioseq-set -> Bioseq-set_seq-set -> Seq-entry
    // b. check if we encounter a Seq-entry_set (multiple) or Seq-entry_seq (single)
    // c. If multip: 
    //      c.A traverse Seq-entry_set -> Bioseq-set
    //      c.B get Seq-entry array within Bioseq-set_seq-set
    //      c.C iterate array and traverse Seq-entry_seq -> Bioseq within each
    // d. If single: traverse Seq-entry_seq -> Bioseq within it

    Set<P<JSONObject, JSONObject>> all = new HashSet<P<JSONObject, JSONObject>>();
    // a. traverse Bioseq-set -> Bioseq-set_seq-set -> Seq-entry
    String[] init_path = new String[] { "Bioseq-set", "Bioseq-set_seq-set", "Seq-entry" };
    JSONObject inside = traverse(root, init_path);


    // b. check if we encounter a Seq-entry_set (multiple) or Seq-entry_seq (single)
    if (inside.has("Seq-entry_set")) { // multiple entries
      // System.out.println(root.toString(2));
      System.out.println("###### Received multiple entries");

      //      c.A traverse Seq-entry_set -> Bioseq-set 
      String[] m_path = new String[] { "Seq-entry_set", "Bioseq-set"};
      JSONObject main = traverse(inside, m_path);
      String[] desc_path = new String[] { "Bioseq-set_descr", "Seq-descr" };
      JSONObject desc = traverse(main, desc_path);

      //      c.B get Seq-entry array within Bioseq-set_seq-set
      JSONArray entries = main.getJSONObject("Bioseq-set_seq-set").getJSONArray("Seq-entry");

      //      c.C iterate array and traverse Seq-entry_seq -> Bioseq within each
      String[] inside_path = new String[] { "Seq-entry_seq", "Bioseq" };
      for (int i=0; i<entries.length(); i++) {
        JSONObject entry = traverse(entries.getJSONObject(i), inside_path);
        all.add(new P<JSONObject, JSONObject>(entry, desc));
      }

    } else { // single entry

      // d. If single: traverse Seq-entry_seq -> Bioseq within it
      String[] inside_path = new String[] { "Seq-entry_seq", "Bioseq" };
      JSONObject entry = traverse(inside, inside_path);
      String[] desc_path = new String[] { "Bioseq_descr", "Seq-descr" };
      JSONObject desc = traverse(entry, desc_path);
      all.add(new P<JSONObject, JSONObject>(entry, desc));

    }
    return all;
  }

  public static Set<SequenceEntry> parsePossiblyMany(String xml) {
    Set<SequenceEntry> all_entries = new HashSet<SequenceEntry>();
    JSONObject jo = null;
    try {
      jo = XML.toJSONObject(xml);
      Set<P<JSONObject, JSONObject>> seq_entries = get_seq_entry_objs(jo);
      for (P<JSONObject, JSONObject> gene_entry : seq_entries) {
        try {
          GenBankEntry entry = new GenBankEntry(gene_entry.fst(), gene_entry.snd());
          all_entries.add(entry);
        } catch (JSONException e) {

          System.out.println("Data: " + gene_entry.fst().toString(4));
          System.out.println("Desc: " + gene_entry.snd().toString(4));
          System.out.println("Failed to extract some field in Genbank. Err: " + e);
          // System.console().readLine();

        }
      }
    } catch (JSONException e) {
     
      // if (jo == null)
      //   System.out.println(xml + "\n Of sz = " + xml.length());
      // else
      //   System.out.println(jo.toString(4));
      System.out.println("Failed to parse GenBank XML. Err: " + e);
      // System.console().readLine();

    }
    return all_entries;
  }

  private GenBankEntry(JSONObject gene_entry, JSONObject desc_entry) {
    this.data = gene_entry;
    this.desc = desc_entry;

    this.accessions = extract_accessions();
    this.pmids = extract_pmids();
    this.org_id = extract_org_id();
    this.sequence = extract_seq();
    this.ec = extract_ec();
    this.catalyzed_rxns = extract_catalyzed_reactions();

    // new Seq(..) looks at the metadata in this.data for SwissProt fields:
    // this.data { "name" : gene_name_eg_Adh1 }
    // this.data { "proteinExistence": { "type" : "evidence at transcript level" });
    // this.data { "comment": [ { "type": "catalytic activity", "text": uniprot_activity_annotation } ] }
    // this.data { "accession" : ["Q23412", "P87D78"] }
    // we manually add these fields so that we have consistent data
    JSONArray accs = new JSONArray();
    for (String a : this.accessions)
      accs.put(a);
    JSONObject evidence = new JSONObject(), activity = new JSONObject();
    String name = "";

    this.data.put("name", name);
    this.data.put("proteinExistence", evidence);
    this.data.put("comment", new JSONArray(new JSONObject[] { activity }));
    this.data.put("accession", accs);

    // extract_metadata processes this.data, so do that only after updating
    // this.data with the proxy fields from above.
    this.metadata = extract_metadata();
  }

  DBObject metadata;
  Set<String> accessions;
  List<String> pmids;
  String sequence;
  Long org_id;
  String ec;
  Set<Long> catalyzed_rxns;

  DBObject get_metadata() { return this.metadata; }
  Set<String> get_accessions() { return this.accessions; }
  List<String> get_pmids() { return this.pmids; }
  Long get_org_id() { return this.org_id; }
  String get_seq() { return this.sequence; }
  String get_ec() { return this.ec; }
  Set<Long> get_catalyzed_rxns() { return this.catalyzed_rxns; }

  private DBObject extract_metadata() { 
    // cannot directly return this.data coz in Seq.java 
    // we expect certain specific JSON format fields
    return MongoDBToJSON.conv(this.data);
  }

  private Set<String> extract_accessions() {
    Set<String> accessions = new HashSet<String>();
    // "Bioseq_id": {"Seq-id": [
    //     {"Seq-id_ddbj": {"Textseq-id": {
    //         "Textseq-id_version": 1,
    //         "Textseq-id_accession": "E07950"


    accessions.addAll(extract_accessions_under("Seq-id_ddbj"));
    accessions.addAll(extract_accessions_under("Seq-id_embl"));
    accessions.addAll(extract_accessions_under("Seq-id_genbank"));
    accessions.addAll(extract_accessions_under("Seq-id_other"));

    if (accessions.size() == 0) {
      System.out.println("Got 0 accessions from: " + this.data.toString(2));
      System.console().readLine();
    }
    return accessions;
  }

  Set<String> extract_accessions_under(String key) {
    Set<String> accessions = new HashSet<String>();
    String[] initpath = new String[] {"Bioseq_id"}; 
    Set<JSONObject> os = get_inarray(this.data, key, initpath, "Seq-id");
    for (JSONObject o : os)
      accessions.add(o.getJSONObject(key).getJSONObject("Textseq-id").getString("Textseq-id_accession")); 
    return accessions;
  }

  private Set<JSONObject> get_desc_obj(String haskey) {
    // the description object is a wierd beast: need it for get_org_id + get_pmids
    // 1. We have to traverse down to this.data -> "Bioseq_descr" -> "Seq-descr" -> "Seqdesc" : JSONArray
    // 2. Then within this array there are objects like below:
    //        {"Seqdesc_title": "gDNA encoding NAD synthetase."},  
    //        {"Seqdesc_comment": "OS   Bacillus ste ...
    //        {"Seqdesc_genbank": {"GB-block": { ...
    //        {"Seqdesc_pub": {"Pubdesc": {"Pubdesc_pub": {"Pub-equiv": {"Pub": {"Pub_patent ...
    //        {"Seqdesc_source": {"BioSource": {"BioSource_org": {"Org-ref": {  ...
    //        {"Seqdesc_update-date": {"Date": ...
    //        {"Seqdesc_create-date": {"Date": 
    // 
    // For get_org_id and get_pmids we have to retrieve the objects that have fields
    // Seqdesc_source and Seqdesc_pub, respectively. We first get the array and then search
    // each object within it for one that has the @param field

    return get_inarray(this.desc, haskey, new String[] {}, "Seqdesc");
  }

  private Set<JSONObject> get_inarray(JSONObject in_obj, String has_key, String[] initpath, String pathend) {
    Set<JSONObject> objs_having_key = new HashSet<JSONObject>();

    JSONObject container = traverse(in_obj, initpath);
    Object x = container.get(pathend);
    if (x instanceof JSONArray) {
      JSONArray desc_array = (JSONArray)x;

      for (int i=0; i<desc_array.length(); i++) {
        JSONObject o = desc_array.getJSONObject(i);
        if (o.has(has_key))
          objs_having_key.add(o);
      }
    } else if (x instanceof JSONObject) {
      JSONObject o = (JSONObject)x;
      if (o.has(has_key))
        objs_having_key.add(o);
    }

    return objs_having_key;
  }

  Set<Long> extract_catalyzed_reactions() {
    // optionally add reactions to actfamilies by processing 
    // "catalytic activity" annotations and then return those 
    // catalyzed reaction ids (Long _id of actfamilies). This 
    // function SHOULD NOT infer which actfamilies refer to 
    // this object, as that is done in map_seq install.
    return new HashSet<Long>();
  }
  
  private List<String> extract_pmids() { 
    // See comments in get_desc_obj for how we traverse to an array 
    // and then find an object within with a particular field

    List<String> pmids = new ArrayList<String>();
    // the pub's contain patents (and probably paper references)
    // For patents it looks like:
    // {"Seqdesc_pub": {"Pubdesc": {"Pubdesc_pub": {"Pub-equiv": {"Pub": {"Pub_patent": {"Cit-pat": { ...
    // For publications it looks like:
    // {"Seqdesc_pub": {"Pubdesc": {"Pubdesc_pub": {"Pub-equiv": {"Pub": [
    //     {"Pub_article": {"Cit-art": {...
    //     {"Pub_pmid": {"PubMedId": 15057458}}
    Set<JSONObject> sourceflds = get_desc_obj("Seqdesc_pub") ;
    for (JSONObject sourcefld : sourceflds) {
      String[] toPub_path = new String[] { "Seqdesc_pub", "Pubdesc", "Pubdesc_pub", "Pub-equiv" };
      Set<JSONObject> o = get_inarray(sourcefld, "Pub_pmid", toPub_path, "Pub");
      for (JSONObject pmid_obj : o)
        pmids.add(pmid_obj.getJSONObject("Pub_pmid").getInt("PubMedId") + "");
    }

    // System.out.println("Genbank: Extracted pmids: " + pmids);
    return pmids; 
  }

  private Long extract_org_id() { 
    // See comments in get_desc_obj for how we traverse to an array 
    // and then find an object within with a particular field

    Long org_id = null;
    Set<JSONObject> sourceflds = get_desc_obj("Seqdesc_source") ;
    if (sourceflds.size() > 1)
      System.out.println("WARN: Genbank entry has more than one source organism id.");
    for (JSONObject sourcefld : sourceflds) {
      String[] path = new String[] { "Seqdesc_source", "BioSource", "BioSource_org", "Org-ref",
                                      "Org-ref_db", "Dbtag", "Dbtag_tag", "Object-id" };
      JSONObject o = traverse(sourcefld, path);
      org_id = new Long(o.getInt("Object-id_id"));
    }
    // System.out.println("Genbank: Extracted org_id: " + org_id);
    if (org_id == null) {
      System.out.println("org_id == null. Multiple entries? We dont traverse down to the right desc for that case");
      System.console().readLine();
    }
    return org_id;

    // We have to navigate this structure to get to the Object-id_id value...
    //
    //  {"Seqdesc_source": {"BioSource": {"BioSource_org": {"Org-ref": {                                   
    //      "Org-ref_orgname": {"OrgName": {                                                               
    //          "OrgName_lineage": "Bacteria; Firmicutes; Bacilli; Bacillales; Bacillaceae; Geobacillus",
    //          "OrgName_div": "BCT",
    //          "OrgName_mod": {"OrgMod": {
    //              "OrgMod_subtype": {
    //                  "value": "old-name",
    //                  "content": 254
    //              },
    //              "OrgMod_subname": "Bacillus stearothermophilus"
    //          }},
    //          "OrgName_name": {"OrgName_name_binomial": {"BinomialOrgName": {
    //              "BinomialOrgName_genus": "Geobacillus",
    //              "BinomialOrgName_species": "stearothermophilus"
    //          }}},
    //          "OrgName_gcode": 11
    //      }},
    //      "Org-ref_taxname": "Geobacillus stearothermophilus",
    //      "Org-ref_db": {"Dbtag": {
    //          "Dbtag_tag": {"Object-id": {"Object-id_id": 1422}},
    //          "Dbtag_db": "taxon"
    //      }}
    //  }}}}},
  }

  private String extract_seq() { 
    // "Bioseq_inst": {"Seq-inst": {                
    //    "Seq-inst_mol": {"value": "dna"},
    //    "Seq-inst_length": 1238,           
    //    "Seq-inst_seq-data": {"Seq-data": {"Seq-data_iupacna": {"IUPACna": "GCATGCGCTCT 
    // OR
    // "Bioseq_inst": {"Seq-inst": {
    //   "Seq-inst_mol": {"value": "aa"},
    //   "Seq-inst_length": 770,
    //   "Seq-inst_seq-data": {"Seq-data": {"Seq-data_iupacaa": {"IUPACaa": "MTT

    String pathend_e, seq_e;
    String[] type_path = new String[] {"Bioseq_inst", "Seq-inst", "Seq-inst_mol" }; 
    String seq_type = traverse(this.data, type_path).getString("value"); 
    // seq_type == dna | rna | aa 
    boolean dna = "dna".equals(seq_type); 
    boolean rna = "rna".equals(seq_type);

    if (dna || rna) { // NT seq
      pathend_e = "Seq-data_iupacna";
      seq_e = "IUPACna";
    } else { // AA seq
      pathend_e = "Seq-data_iupacaa";
      seq_e = "IUPACaa";
    }
    String[] seq_path = new String[] {"Bioseq_inst", "Seq-inst", "Seq-inst_seq-data", "Seq-data", pathend_e }; 
    // System.out.println("Genbank: Extracting sequence from: " + this.data.toString(2));
    JSONObject o = traverse(this.data, seq_path);
    String seq = o.getString(seq_e); 
    // System.out.println("Genbank: Extracted seq: " + seq.substring(0,Math.min(20, seq.length())));
    return seq;
  }

  private String extract_ec() { 
    // genbank entries dont seem to have the ec#
    return null; 
  }

  static JSONObject traverse(JSONObject container, String[] xpath) {
    return traverse(container, xpath, 0);
  }

  static JSONObject traverse(JSONObject container, String[] xpath, int idx) {
    if (idx == xpath.length)
      return container;
    else 
      return traverse(container.getJSONObject(xpath[idx]), xpath, idx + 1);
  }

}

