package act.installer.sequence;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import com.mongodb.DBObject;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.XML;
import act.shared.helpers.MongoDBToJSON;
import act.shared.helpers.P;
import act.shared.sar.SAR;

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

    // This file is written to handle rettype=native calls. (Output has Bioseq_ etc.)
    // The alternative call of using rettype=fasta returns compact seq data
    //      but is formatted differently (with TSeq_ etc.)
    // See examples at the end of this file.

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
        }
      }
    } catch (JSONException e) {

      System.out.println("Failed to parse GenBank XML. Err: " + e);

    }
    return all_entries;
  }

  private GenBankEntry(JSONObject gene_entry, JSONObject desc_entry) {
    this.data = gene_entry;
    this.desc = desc_entry;

    this.accessions = extract_accessions();
    this.refs = extract_pmids();
    this.org_id = extract_org_id();
    this.sequence = extract_seq();
    this.ec = extract_ec();

    // inits this.catalyzed_{rxns, substrates, products}
    // optionally; if there are some that we NLP out of the
    // "catalysis activity" field read from this entry
    extract_catalyzed_reactions();

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
  List<JSONObject> refs;
  String sequence;
  Long org_id;
  String ec;
  Set<Long> catalyzed_rxns;
  Set<Long> catalyzed_substrates_diverse, catalyzed_substrates_uniform;
  Set<Long> catalyzed_products_diverse, catalyzed_products_uniform;
  HashMap<Long, Set<Long>> catalyzed_rxns_to_substrates, catalyzed_rxns_to_products;
  SAR sar;

  DBObject getMetadata() { return this.metadata; }
  Set<String> getAccessions() { return this.accessions; }
  List<JSONObject> getRefs() { return this.refs; }
  Long getOrgId() { return this.org_id; }
  String getSeq() { return this.sequence; }
  String getEc() { return this.ec; }
  Set<Long> getCatalyzedRxns() { return this.catalyzed_rxns; }
  Set<Long> getCatalyzedSubstratesUniform() { return this.catalyzed_substrates_uniform; }
  Set<Long> getCatalyzedSubstratesDiverse() { return this.catalyzed_substrates_diverse; }
  Set<Long> getCatalyzedProductsUniform() { return this.catalyzed_products_uniform; }
  Set<Long> getCatalyzedProductsDiverse() { return this.catalyzed_products_diverse; }
  HashMap<Long, Set<Long>> getCatalyzedRxnsToSubstrates() { return this.catalyzed_rxns_to_substrates; }
  HashMap<Long, Set<Long>> getCatalyzedRxnsToProducts() { return this.catalyzed_rxns_to_products; }
  SAR getSar() { return this.sar; }

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
    accessions.addAll(extract_accessions_under("Seq-id_swissprot"));
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

  private List<JSONObject> extract_pmids() {
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

    List<JSONObject> pmidReferences = new ArrayList<>();
    for (String pmid : pmids) {
      JSONObject obj = new JSONObject();
      obj.put("val", pmid);
      obj.put("src", "PMID");
      pmidReferences.add(obj);
    }

    return pmidReferences;
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
    JSONObject o = traverse(this.data, seq_path);
    String seq = o.getString(seq_e);
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

  public String toString() {
    return this.desc.toString() + " -> " + this.data.toString();
  }

}

/*
Difference between rettype=native and rettype=fasta for NCBI genbank calls.

E.g.,
Case A]  16     lines in curl -s "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=protein&id=GI:6320033&rettype=fasta&retmode=xml"
Case B]  3209 lines in curl -s "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=protein&id=GI:6320033&rettype=native&retmode=xml" | wc -l

Case A] 16 lines from "fasta" have format:
{
  "TSeqSet": {
    "TSeq": {
      "TSeq_seqtype": { "-value": "protein" },
      "TSeq_gi": "6320033",
      "TSeq_accver": "NP_010113.1",
      "TSeq_taxid": "559292",
      "TSeq_orgname": "Saccharomyces cerevisiae S288c",
      "TSeq_defline": "bifunctional alcohol dehydrogenase/S-(hydroxymethyl)glutathione dehydrogenase [Saccharomyces cerevisiae S288c]",
      "TSeq_length": "386",
      "TSeq_sequence": "MSAATVGKPIKCIAAVAYDAKKPLSVEEITVDAPKAHEVRIKIEYTAVCHTDAYTLSGSDPEGLFPCVLGHEGAGIVESVGDDVITVKPGDHVIALYTAECGKCKFCTSGKTNLCGAVRATQGKGVMPDGTTRFHNAKGEDIYHFMGCSTFSEYTVVADVSVVAIDPKAPLDAACLLGCGVTTGFGAALKTANVQKGDTVAVFGCGTVGLSVIQGAKLRGASKIIAIDINNKKKQYCSQFGATDFVNPKEDLAKDQTIVEKLIEMTDGGLDFTFDCTGNTKIMRDALEACHKGWGQSIIIGVAAAGEEISTRPFQLVTGRVWKGSAFGGIKGRSEMGGLIKDYQKGALKVEEFITHRRPFKEINQAFEDLHNGDCLRTVLKSDEIK"
    }
  }
}

Case B] 3k lines (can go upto 1M+) from "native" have format:
{
  "Bioseq-set": {
    "Bioseq-set_seq-set": {
      "Seq-entry": {
        "Seq-entry_set": {
          "Bioseq-set": {
            "Bioseq-set_class": { "-value": "nuc-prot" },
            "Bioseq-set_descr": {
              "Seq-descr": {
                "Seqdesc": [
                  {
                    "Seqdesc_source": {
                      "BioSource": {
                        "BioSource_genome": {
                          "-value": "chromosome",
                          "#text": "21"
                        },
                        "BioSource_org": {
                          "Org-ref": {
                            "Org-ref_taxname": "Saccharomyces cerevisiae S288c",
                            "Org-ref_db": {
                              "Dbtag": {
                                "Dbtag_db": "taxon",
                                "Dbtag_tag": {
                                  "Object-id": { "Object-id_id": "559292" }
                                }
                              }
                            },
                            "Org-ref_orgname": {
                              "OrgName": {
                                "OrgName_name": {
                                  "OrgName_name_binomial": {
                                    "BinomialOrgName": {
                                      "BinomialOrgName_genus": "Saccharomyces",
                                      "BinomialOrgName_species": "cerevisiae"
                                    }
                                  }
                                },
                                "OrgName_mod": {
                                  "OrgMod": {
                                    "OrgMod_subtype": {
                                      "-value": "strain",
                                      "#text": "2"
                                    },
                                    "OrgMod_subname": "S288c"
                                  }
                                },
                                "OrgName_lineage": "Eukaryota; Fungi; Dikarya; Ascomycota; Saccharomycotina; Saccharomycetes; Saccharomycetales; Saccharomycetaceae; Saccharomyces",
                                "OrgName_gcode": "1",
                                "OrgName_mgcode": "3",
                                "OrgName_div": "PLN"
                              }
                            }
                          }
                        },
                        "BioSource_subtype": {
                          "SubSource": {
                            "SubSource_subtype": {
                              "-value": "chromosome",
                              "#text": "1"
                            },
                            "SubSource_name": "IV"
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_pub": {
                      "Pubdesc": {
                        "Pubdesc_pub": {
                          "Pub-equiv": {
                            "Pub": {
                              "Pub_sub": {
                                "Cit-sub": {
                                  "Cit-sub_authors": {
                                    "Auth-list": {
                                      "Auth-list_names": {
                                        "Auth-list_names_std": {
                                          "Author": {
                                            "Author_name": {
                                              "Person-id": { "Person-id_consortium": "Saccharomyces Genome Database" }
                                            }
                                          }
                                        }
                                      },
                                      "Auth-list_affil": {
                                        "Affil": {
                                          "Affil_std": {
                                            "Affil_std_affil": "Stanford University",
                                            "Affil_std_div": "Department of Genetics",
                                            "Affil_std_city": "Stanford",
                                            "Affil_std_sub": "CA",
                                            "Affil_std_country": "USA",
                                            "Affil_std_postal-code": "94305-5120"
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_date": {
                                    "Date": {
                                      "Date_std": {
                                        "Date-std": {
                                          "Date-std_year": "2009",
                                          "Date-std_month": "12",
                                          "Date-std_day": "11"
                                        }
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_pub": {
                      "Pubdesc": {
                        "Pubdesc_pub": {
                          "Pub-equiv": {
                            "Pub": [
                              {
                                "Pub_article": {
                                  "Cit-art": {
                                    "Cit-art_title": {
                                      "Title": {
                                        "Title_E": { "Title_E_name": "The nucleotide sequence of Saccharomyces cerevisiae chromosome IV." }
                                      }
                                    },
                                    "Cit-art_authors": {
                                      "Auth-list": {
                                        "Auth-list_names": {
                                          "Auth-list_names_std": {
                                            "Author": [
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Jacq",
                                                        "Name-std_first": "C",
                                                        "Name-std_initials": "C."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Alt-Morbe",
                                                        "Name-std_first": "J",
                                                        "Name-std_initials": "J."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Andre",
                                                        "Name-std_first": "B",
                                                        "Name-std_initials": "B."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Arnold",
                                                        "Name-std_first": "W",
                                                        "Name-std_initials": "W."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Bahr",
                                                        "Name-std_first": "A",
                                                        "Name-std_initials": "A."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Ballesta",
                                                        "Name-std_first": "J",
                                                        "Name-std_initials": "J.P."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Bargues",
                                                        "Name-std_first": "M",
                                                        "Name-std_initials": "M."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Baron",
                                                        "Name-std_first": "L",
                                                        "Name-std_initials": "L."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Becker",
                                                        "Name-std_first": "A",
                                                        "Name-std_initials": "A."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Biteau",
                                                        "Name-std_first": "N",
                                                        "Name-std_initials": "N."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Blocker",
                                                        "Name-std_first": "H",
                                                        "Name-std_initials": "H."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Blugeon",
                                                        "Name-std_first": "C",
                                                        "Name-std_initials": "C."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Boskovic",
                                                        "Name-std_first": "J",
                                                        "Name-std_initials": "J."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Brandt",
                                                        "Name-std_first": "P",
                                                        "Name-std_initials": "P."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Bruckner",
                                                        "Name-std_first": "M",
                                                        "Name-std_initials": "M."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Buitrago",
                                                        "Name-std_first": "M",
                                                        "Name-std_initials": "M.J."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Coster",
                                                        "Name-std_first": "F",
                                                        "Name-std_initials": "F."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Delaveau",
                                                        "Name-std_first": "T",
                                                        "Name-std_initials": "T."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "del Rey",
                                                        "Name-std_first": "F",
                                                        "Name-std_initials": "F."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Dujon",
                                                        "Name-std_first": "B",
                                                        "Name-std_initials": "B."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Eide",
                                                        "Name-std_first": "L",
                                                        "Name-std_initials": "L.G."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Garcia-Cantalejo",
                                                        "Name-std_first": "J",
                                                        "Name-std_initials": "J.M."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Goffeau",
                                                        "Name-std_first": "A",
                                                        "Name-std_initials": "A."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Gomez-Peris",
                                                        "Name-std_first": "A",
                                                        "Name-std_initials": "A."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Zaccaria",
                                                        "Name-std_first": "P",
                                                        "Name-std_initials": "P."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": { "Name-std_last": "et al." }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        },
                                        "Auth-list_affil": {
                                          "Affil": { "Affil_str": "Laboratoire de Genetique Moleculaire, URA 1302 du CNRS, Ecole Normale Superieure, Paris, France. jacq@biologie.ens.fr" }
                                        }
                                      }
                                    },
                                    "Cit-art_from": {
                                      "Cit-art_from_journal": {
                                        "Cit-jour": {
                                          "Cit-jour_title": {
                                            "Title": {
                                              "Title_E": [
                                                { "Title_E_iso-jta": "Nature" },
                                                { "Title_E_ml-jta": "Nature" },
                                                { "Title_E_issn": "0028-0836" },
                                                { "Title_E_name": "Nature" }
                                              ]
                                            }
                                          },
                                          "Cit-jour_imp": {
                                            "Imprint": {
                                              "Imprint_date": {
                                                "Date": {
                                                  "Date_std": {
                                                    "Date-std": {
                                                      "Date-std_year": "1997",
                                                      "Date-std_month": "5",
                                                      "Date-std_day": "29"
                                                    }
                                                  }
                                                }
                                              },
                                              "Imprint_volume": "387",
                                              "Imprint_issue": "6632",
                                              "Imprint_pages": "75-78",
                                              "Imprint_language": "eng",
                                              "Imprint_part-supi": "SUPPL",
                                              "Imprint_pubstatus": {
                                                "PubStatus": {
                                                  "-value": "ppublish",
                                                  "#text": "4"
                                                }
                                              },
                                              "Imprint_history": {
                                                "PubStatusDateSet": {
                                                  "PubStatusDate": [
                                                    {
                                                      "PubStatusDate_pubstatus": {
                                                        "PubStatus": {
                                                          "-value": "pubmed",
                                                          "#text": "8"
                                                        }
                                                      },
                                                      "PubStatusDate_date": {
                                                        "Date": {
                                                          "Date_std": {
                                                            "Date-std": {
                                                              "Date-std_year": "1997",
                                                              "Date-std_month": "5",
                                                              "Date-std_day": "29"
                                                            }
                                                          }
                                                        }
                                                      }
                                                    },
                                                    {
                                                      "PubStatusDate_pubstatus": {
                                                        "PubStatus": {
                                                          "-value": "medline",
                                                          "#text": "12"
                                                        }
                                                      },
                                                      "PubStatusDate_date": {
                                                        "Date": {
                                                          "Date_std": {
                                                            "Date-std": {
                                                              "Date-std_year": "1997",
                                                              "Date-std_month": "5",
                                                              "Date-std_day": "29",
                                                              "Date-std_hour": "0",
                                                              "Date-std_minute": "1"
                                                            }
                                                          }
                                                        }
                                                      }
                                                    },
                                                    {
                                                      "PubStatusDate_pubstatus": {
                                                        "PubStatus": {
                                                          "-value": "other",
                                                          "#text": "255"
                                                        }
                                                      },
                                                      "PubStatusDate_date": {
                                                        "Date": {
                                                          "Date_std": {
                                                            "Date-std": {
                                                              "Date-std_year": "1997",
                                                              "Date-std_month": "5",
                                                              "Date-std_day": "29",
                                                              "Date-std_hour": "0",
                                                              "Date-std_minute": "0"
                                                            }
                                                          }
                                                        }
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    "Cit-art_ids": {
                                      "ArticleIdSet": {
                                        "ArticleId": {
                                          "ArticleId_pubmed": { "PubMedId": "9169867" }
                                        }
                                      }
                                    }
                                  }
                                }
                              },
                              {
                                "Pub_pmid": { "PubMedId": "9169867" }
                              }
                            ]
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_pub": {
                      "Pubdesc": {
                        "Pubdesc_pub": {
                          "Pub-equiv": {
                            "Pub": [
                              {
                                "Pub_article": {
                                  "Cit-art": {
                                    "Cit-art_title": {
                                      "Title": {
                                        "Title_E": { "Title_E_name": "Life with 6000 genes." }
                                      }
                                    },
                                    "Cit-art_authors": {
                                      "Auth-list": {
                                        "Auth-list_names": {
                                          "Auth-list_names_std": {
                                            "Author": [
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Goffeau",
                                                        "Name-std_initials": "A."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Barrell",
                                                        "Name-std_initials": "B.G."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Bussey",
                                                        "Name-std_initials": "H."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Davis",
                                                        "Name-std_initials": "R.W."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Dujon",
                                                        "Name-std_initials": "B."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Feldmann",
                                                        "Name-std_initials": "H."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Galibert",
                                                        "Name-std_initials": "F."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Hoheisel",
                                                        "Name-std_initials": "J.D."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Jacq",
                                                        "Name-std_initials": "C."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Johnston",
                                                        "Name-std_initials": "M."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Louis",
                                                        "Name-std_initials": "E.J."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Mewes",
                                                        "Name-std_initials": "H.W."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Murakami",
                                                        "Name-std_initials": "Y."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Philippsen",
                                                        "Name-std_initials": "P."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Tettelin",
                                                        "Name-std_initials": "H."
                                                      }
                                                    }
                                                  }
                                                }
                                              },
                                              {
                                                "Author_name": {
                                                  "Person-id": {
                                                    "Person-id_name": {
                                                      "Name-std": {
                                                        "Name-std_last": "Oliver",
                                                        "Name-std_initials": "S.G."
                                                      }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        },
                                        "Auth-list_affil": {
                                          "Affil": { "Affil_str": "Universite Catholique de Louvain, Unite de Biochimie Physiologique, Place Croix du Sud, 2/20, 1348 Louvain-la-Neuve, Belgium." }
                                        }
                                      }
                                    },
                                    "Cit-art_from": {
                                      "Cit-art_from_journal": {
                                        "Cit-jour": {
                                          "Cit-jour_title": {
                                            "Title": {
                                              "Title_E": [
                                                { "Title_E_iso-jta": "Science" },
                                                { "Title_E_ml-jta": "Science" },
                                                { "Title_E_issn": "0036-8075" },
                                                { "Title_E_name": "Science (New York, N.Y.)" }
                                              ]
                                            }
                                          },
                                          "Cit-jour_imp": {
                                            "Imprint": {
                                              "Imprint_date": {
                                                "Date": {
                                                  "Date_std": {
                                                    "Date-std": {
                                                      "Date-std_year": "1996",
                                                      "Date-std_month": "10",
                                                      "Date-std_day": "25"
                                                    }
                                                  }
                                                }
                                              },
                                              "Imprint_volume": "274",
                                              "Imprint_issue": "5287",
                                              "Imprint_pages": "546",
                                              "Imprint_language": "eng",
                                              "Imprint_pubstatus": {
                                                "PubStatus": {
                                                  "-value": "ppublish",
                                                  "#text": "4"
                                                }
                                              },
                                              "Imprint_history": {
                                                "PubStatusDateSet": {
                                                  "PubStatusDate": [
                                                    {
                                                      "PubStatusDate_pubstatus": {
                                                        "PubStatus": {
                                                          "-value": "pubmed",
                                                          "#text": "8"
                                                        }
                                                      },
                                                      "PubStatusDate_date": {
                                                        "Date": {
                                                          "Date_std": {
                                                            "Date-std": {
                                                              "Date-std_year": "1996",
                                                              "Date-std_month": "10",
                                                              "Date-std_day": "25"
                                                            }
                                                          }
                                                        }
                                                      }
                                                    },
                                                    {
                                                      "PubStatusDate_pubstatus": {
                                                        "PubStatus": {
                                                          "-value": "medline",
                                                          "#text": "12"
                                                        }
                                                      },
                                                      "PubStatusDate_date": {
                                                        "Date": {
                                                          "Date_std": {
                                                            "Date-std": {
                                                              "Date-std_year": "1996",
                                                              "Date-std_month": "10",
                                                              "Date-std_day": "25",
                                                              "Date-std_hour": "0",
                                                              "Date-std_minute": "1"
                                                            }
                                                          }
                                                        }
                                                      }
                                                    },
                                                    {
                                                      "PubStatusDate_pubstatus": {
                                                        "PubStatus": {
                                                          "-value": "other",
                                                          "#text": "255"
                                                        }
                                                      },
                                                      "PubStatusDate_date": {
                                                        "Date": {
                                                          "Date_std": {
                                                            "Date-std": {
                                                              "Date-std_year": "1996",
                                                              "Date-std_month": "10",
                                                              "Date-std_day": "25",
                                                              "Date-std_hour": "0",
                                                              "Date-std_minute": "0"
                                                            }
                                                          }
                                                        }
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    "Cit-art_ids": {
                                      "ArticleIdSet": {
                                        "ArticleId": {
                                          "ArticleId_pubmed": { "PubMedId": "8849441" }
                                        }
                                      }
                                    }
                                  }
                                }
                              },
                              {
                                "Pub_pmid": { "PubMedId": "8849441" }
                              }
                            ]
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_pub": {
                      "Pubdesc": {
                        "Pubdesc_pub": {
                          "Pub-equiv": {
                            "Pub": {
                              "Pub_sub": {
                                "Cit-sub": {
                                  "Cit-sub_authors": {
                                    "Auth-list": {
                                      "Auth-list_names": {
                                        "Auth-list_names_std": {
                                          "Author": {
                                            "Author_name": {
                                              "Person-id": { "Person-id_consortium": "Saccharomyces Genome Database" }
                                            }
                                          }
                                        }
                                      },
                                      "Auth-list_affil": {
                                        "Affil": {
                                          "Affil_std": {
                                            "Affil_std_affil": "Stanford University",
                                            "Affil_std_div": "Department of Genetics",
                                            "Affil_std_city": "Stanford",
                                            "Affil_std_sub": "CA",
                                            "Affil_std_country": "USA",
                                            "Affil_std_postal-code": "94305-5120"
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_medium": { "-value": "other" },
                                  "Cit-sub_date": {
                                    "Date": {
                                      "Date_std": {
                                        "Date-std": {
                                          "Date-std_year": "2011",
                                          "Date-std_month": "3",
                                          "Date-std_day": "31"
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_descr": "Sequence update by submitter"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_pub": {
                      "Pubdesc": {
                        "Pubdesc_pub": {
                          "Pub-equiv": {
                            "Pub": {
                              "Pub_sub": {
                                "Cit-sub": {
                                  "Cit-sub_authors": {
                                    "Auth-list": {
                                      "Auth-list_names": {
                                        "Auth-list_names_std": {
                                          "Author": {
                                            "Author_name": {
                                              "Person-id": { "Person-id_consortium": "Saccharomyces Genome Database" }
                                            }
                                          }
                                        }
                                      },
                                      "Auth-list_affil": {
                                        "Affil": {
                                          "Affil_std": {
                                            "Affil_std_affil": "Stanford University",
                                            "Affil_std_div": "Department of Genetics",
                                            "Affil_std_city": "Stanford",
                                            "Affil_std_sub": "CA",
                                            "Affil_std_country": "USA",
                                            "Affil_std_postal-code": "94305-5120"
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_medium": { "-value": "other" },
                                  "Cit-sub_date": {
                                    "Date": {
                                      "Date_std": {
                                        "Date-std": {
                                          "Date-std_year": "2012",
                                          "Date-std_month": "5",
                                          "Date-std_day": "4"
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_descr": "Protein update by submitter"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_pub": {
                      "Pubdesc": {
                        "Pubdesc_pub": {
                          "Pub-equiv": {
                            "Pub": {
                              "Pub_sub": {
                                "Cit-sub": {
                                  "Cit-sub_authors": {
                                    "Auth-list": {
                                      "Auth-list_names": {
                                        "Auth-list_names_std": {
                                          "Author": {
                                            "Author_name": {
                                              "Person-id": { "Person-id_consortium": "Saccharomyces Genome Database" }
                                            }
                                          }
                                        }
                                      },
                                      "Auth-list_affil": {
                                        "Affil": {
                                          "Affil_std": {
                                            "Affil_std_affil": "Stanford University",
                                            "Affil_std_div": "Department of Genetics",
                                            "Affil_std_city": "Stanford",
                                            "Affil_std_sub": "CA",
                                            "Affil_std_country": "USA",
                                            "Affil_std_postal-code": "94305-5120"
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_medium": { "-value": "other" },
                                  "Cit-sub_date": {
                                    "Date": {
                                      "Date_std": {
                                        "Date-std": {
                                          "Date-std_year": "2013",
                                          "Date-std_month": "2",
                                          "Date-std_day": "6"
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_descr": "Protein update by submitter"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_pub": {
                      "Pubdesc": {
                        "Pubdesc_pub": {
                          "Pub-equiv": {
                            "Pub": {
                              "Pub_sub": {
                                "Cit-sub": {
                                  "Cit-sub_authors": {
                                    "Auth-list": {
                                      "Auth-list_names": {
                                        "Auth-list_names_std": {
                                          "Author": {
                                            "Author_name": {
                                              "Person-id": { "Person-id_consortium": "Saccharomyces Genome Database" }
                                            }
                                          }
                                        }
                                      },
                                      "Auth-list_affil": {
                                        "Affil": {
                                          "Affil_std": {
                                            "Affil_std_affil": "Stanford University",
                                            "Affil_std_div": "Department of Genetics",
                                            "Affil_std_city": "Stanford",
                                            "Affil_std_sub": "CA",
                                            "Affil_std_country": "USA",
                                            "Affil_std_postal-code": "94305-5120"
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_medium": { "-value": "other" },
                                  "Cit-sub_date": {
                                    "Date": {
                                      "Date_std": {
                                        "Date-std": {
                                          "Date-std_year": "2015",
                                          "Date-std_month": "1",
                                          "Date-std_day": "16"
                                        }
                                      }
                                    }
                                  },
                                  "Cit-sub_descr": "Protein update by submitter"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  {
                    "Seqdesc_update-date": {
                      "Date": {
                        "Date_std": {
                          "Date-std": {
                            "Date-std_year": "2015",
                            "Date-std_month": "2",
                            "Date-std_day": "18"
                          }
                        }
                      }
                    }
                  }
                ]
              }
            },
            "Bioseq-set_seq-set": {
              "Seq-entry": [
                {
                  "Seq-entry_seq": {
                    "Bioseq": {
                      "Bioseq_id": {
                        "Seq-id": [
                          {
                            "Seq-id_other": {
                              "Textseq-id": {
                                "Textseq-id_accession": "NM_001180228",
                                "Textseq-id_version": "1"
                              }
                            }
                          },
                          { "Seq-id_gi": "296143201" }
                        ]
                      },
                      "Bioseq_descr": {
                        "Seq-descr": {
                          "Seqdesc": [
                            { "Seqdesc_title": "Saccharomyces cerevisiae S288c bifunctional alcohol dehydrogenase/S-(hydroxymethyl)glutathione dehydrogenase (SFA1), mRNA" },
                            {
                              "Seqdesc_molinfo": {
                                "MolInfo": {
                                  "MolInfo_biomol": {
                                    "-value": "mRNA",
                                    "#text": "3"
                                  }
                                }
                              }
                            },
                            {
                              "Seqdesc_user": {
                                "User-object": {
                                  "User-object_type": {
                                    "Object-id": { "Object-id_str": "RefGeneTracking" }
                                  },
                                  "User-object_data": {
                                    "User-field": [
                                      {
                                        "User-field_label": {
                                          "Object-id": { "Object-id_str": "GenomicSource" }
                                        },
                                        "User-field_data": { "User-field_data_str": "NC_001136" }
                                      },
                                      {
                                        "User-field_label": {
                                          "Object-id": { "Object-id_str": "Status" }
                                        },
                                        "User-field_data": { "User-field_data_str": "PROVISIONAL" }
                                      }
                                    ]
                                  }
                                }
                              }
                            },
                            {
                              "Seqdesc_create-date": {
                                "Date": {
                                  "Date_std": {
                                    "Date-std": {
                                      "Date-std_year": "2010",
                                      "Date-std_month": "5",
                                      "Date-std_day": "17"
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      },
                      "Bioseq_inst": {
                        "Seq-inst": {
                          "Seq-inst_repr": { "-value": "raw" },
                          "Seq-inst_mol": { "-value": "rna" },
                          "Seq-inst_length": "1161",
                          "Seq-inst_seq-data": {
                            "Seq-data": {
                              "Seq-data_iupacna": { "IUPACna": "ATGTCCGCCGCTACTGTTGGTAAACCTATTAAGTGCATTGCTGCTGTTGCGTATGATGCGAAGAAACCATTAAGTGTTGAAGAAATCACGGTAGACGCCCCAAAAGCGCACGAAGTACGTATCAAAATTGAATATACTGCTGTATGCCACACTGATGCGTACACTTTATCAGGCTCTGATCCAGAAGGACTTTTCCCTTGCGTTCTGGGCCACGAAGGAGCCGGTATCGTAGAATCTGTAGGCGATGATGTCATAACAGTTAAGCCTGGTGATCATGTTATTGCTTTGTACACTGCTGAGTGTGGCAAATGTAAGTTCTGTACTTCCGGTAAAACCAACTTATGTGGTGCTGTTAGAGCTACTCAAGGGAAAGGTGTAATGCCTGATGGGACCACAAGATTTCATAATGCGAAAGGTGAAGATATATACCATTTCATGGGTTGCTCTACTTTTTCCGAATATACTGTGGTGGCAGATGTCTCTGTGGTTGCCATCGATCCAAAAGCTCCCTTGGATGCTGCCTGTTTACTGGGTTGTGGTGTTACTACTGGTTTTGGGGCGGCTCTTAAGACAGCTAATGTGCAAAAAGGCGATACCGTTGCAGTATTTGGCTGCGGGACTGTAGGACTCTCCGTTATCCAAGGTGCAAAGTTAAGGGGCGCTTCCAAGATCATTGCCATTGACATTAACAATAAGAAAAAACAATATTGTTCTCAATTTGGTGCCACGGATTTTGTTAATCCCAAGGAAGATTTGGCCAAAGATCAAACTATCGTTGAAAAGTTAATTGAAATGACTGATGGGGGTCTGGATTTTACTTTTGACTGTACTGGTAATACCAAAATTATGAGAGATGCTTTGGAAGCCTGTCATAAAGGTTGGGGTCAATCTATTATCATTGGTGTGGCTGCCGCTGGTGAAGAAATTTCTACAAGGCCGTTCCAGCTGGTCACTGGTAGAGTGTGGAAAGGCTCTGCTTTTGGTGGCATCAAAGGTAGATCTGAAATGGGCGGTTTAATTAAAGACTATCAAAAAGGTGCCTTAAAAGTCGAAGAATTTATCACTCACAGGAGACCATTCAAAGAAATCAATCAAGCCTTTGAAGATTTGCATAACGGTGATTGCTTAAGAACCGTCTTGAAGTCTGATGAAATAAAATAG" }
                            }
                          }
                        }
                      },
                      "Bioseq_annot": {
                        "Seq-annot": {
                          "Seq-annot_data": {
                            "Seq-annot_data_ftable": {
                              "Seq-feat": {
                                "Seq-feat_data": {
                                  "SeqFeatData": {
                                    "SeqFeatData_gene": {
                                      "Gene-ref": {
                                        "Gene-ref_locus": "SFA1",
                                        "Gene-ref_syn": { "Gene-ref_syn_E": "ADH5" },
                                        "Gene-ref_locus-tag": "YDL168W"
                                      }
                                    }
                                  }
                                },
                                "Seq-feat_location": {
                                  "Seq-loc": {
                                    "Seq-loc_int": {
                                      "Seq-interval": {
                                        "Seq-interval_from": "0",
                                        "Seq-interval_to": "1160",
                                        "Seq-interval_strand": {
                                          "Na-strand": { "-value": "plus" }
                                        },
                                        "Seq-interval_id": {
                                          "Seq-id": { "Seq-id_gi": "296143201" }
                                        }
                                      }
                                    }
                                  }
                                },
                                "Seq-feat_dbxref": {
                                  "Dbtag": {
                                    "Dbtag_db": "GeneID",
                                    "Dbtag_tag": {
                                      "Object-id": { "Object-id_id": "851386" }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                },
                {
                  "Seq-entry_seq": {
                    "Bioseq": {
                      "Bioseq_id": {
                        "Seq-id": [
                          {
                            "Seq-id_other": {
                              "Textseq-id": {
                                "Textseq-id_accession": "NP_010113",
                                "Textseq-id_version": "1"
                              }
                            }
                          },
                          { "Seq-id_gi": "6320033" }
                        ]
                      },
                      "Bioseq_descr": {
                        "Seq-descr": {
                          "Seqdesc": [
                            {
                              "Seqdesc_title": "bifunctional alcohol dehydrogenase/S-(hydroxymethyl)glutathione dehydrogenase [Saccharomyces cerevisiae S288c]"
                            },
                            {
                              "Seqdesc_molinfo": {
                                "MolInfo": {
                                  "MolInfo_biomol": {
                                    "-value": "peptide",
                                    "#text": "8"
                                  },
                                  "MolInfo_tech": {
                                    "-value": "concept-trans",
                                    "#text": "8"
                                  }
                                }
                              }
                            },
                            {
                              "Seqdesc_user": {
                                "User-object": {
                                  "User-object_type": {
                                    "Object-id": { "Object-id_str": "RefGeneTracking" }
                                  },
                                  "User-object_data": {
                                    "User-field": [
                                      {
                                        "User-field_label": {
                                          "Object-id": { "Object-id_str": "IdenticalTo" }
                                        },
                                        "User-field_data": {
                                          "User-field_data_fields": {
                                            "User-field": {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_id": "0" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_fields": {
                                                  "User-field": [
                                                    {
                                                      "User-field_label": {
                                                        "Object-id": { "Object-id_str": "accession" }
                                                      },
                                                      "User-field_data": { "User-field_data_str": "DAA11693" }
                                                    },
                                                    {
                                                      "User-field_label": {
                                                        "Object-id": { "Object-id_str": "gi" }
                                                      },
                                                      "User-field_data": { "User-field_data_int": "285810869" }
                                                    }
                                                  ]
                                                }
                                              }
                                            }
                                          }
                                        }
                                      },
                                      {
                                        "User-field_label": {
                                          "Object-id": { "Object-id_str": "Status" }
                                        },
                                        "User-field_data": { "User-field_data_str": "PROVISIONAL" }
                                      }
                                    ]
                                  }
                                }
                              }
                            },
                            {
                              "Seqdesc_create-date": {
                                "Date": {
                                  "Date_std": {
                                    "Date-std": {
                                      "Date-std_year": "1999",
                                      "Date-std_month": "11",
                                      "Date-std_day": "9"
                                    }
                                  }
                                }
                              }
                            }
                          ]
                        }
                      },
                      "Bioseq_inst": {
                        "Seq-inst": {
                          "Seq-inst_repr": { "-value": "raw" },
                          "Seq-inst_mol": { "-value": "aa" },
                          "Seq-inst_length": "386",
                          "Seq-inst_seq-data": {
                            "Seq-data": {
                              "Seq-data_iupacaa": { "IUPACaa": "MSAATVGKPIKCIAAVAYDAKKPLSVEEITVDAPKAHEVRIKIEYTAVCHTDAYTLSGSDPEGLFPCVLGHEGAGIVESVGDDVITVKPGDHVIALYTAECGKCKFCTSGKTNLCGAVRATQGKGVMPDGTTRFHNAKGEDIYHFMGCSTFSEYTVVADVSVVAIDPKAPLDAACLLGCGVTTGFGAALKTANVQKGDTVAVFGCGTVGLSVIQGAKLRGASKIIAIDINNKKKQYCSQFGATDFVNPKEDLAKDQTIVEKLIEMTDGGLDFTFDCTGNTKIMRDALEACHKGWGQSIIIGVAAAGEEISTRPFQLVTGRVWKGSAFGGIKGRSEMGGLIKDYQKGALKVEEFITHRRPFKEINQAFEDLHNGDCLRTVLKSDEIK" }
                            }
                          }
                        }
                      },
                      "Bioseq_annot": {
                        "Seq-annot": [
                          {
                            "Seq-annot_data": {
                              "Seq-annot_data_ftable": {
                                "Seq-feat": {
                                  "Seq-feat_id": {
                                    "Feat-id": {
                                      "Feat-id_local": {
                                        "Object-id": { "Object-id_id": "6568" }
                                      }
                                    }
                                  },
                                  "Seq-feat_data": {
                                    "SeqFeatData": {
                                      "SeqFeatData_prot": {
                                        "Prot-ref": {
                                          "Prot-ref_name": { "Prot-ref_name_E": "bifunctional alcohol dehydrogenase/S-(hydroxymethyl)glutathione dehydrogenase" },
                                          "Prot-ref_ec": {
                                            "Prot-ref_ec_E": [
                                              "1.1.1.-",
                                              "1.1.1.284",
                                              "1.1.1.1"
                                            ]
                                          }
                                        }
                                      }
                                    }
                                  },
                                  "Seq-feat_location": {
                                    "Seq-loc": {
                                      "Seq-loc_int": {
                                        "Seq-interval": {
                                          "Seq-interval_from": "0",
                                          "Seq-interval_to": "385",
                                          "Seq-interval_id": {
                                            "Seq-id": { "Seq-id_gi": "6320033" }
                                          }
                                        }
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          },
                          {
                            "Seq-annot_db": {
                              "-value": "other",
                              "#text": "255"
                            },
                            "Seq-annot_name": "Annot:CDD",
                            "Seq-annot_desc": {
                              "Annot-descr": {
                                "Annotdesc": [
                                  { "Annotdesc_name": "CddSearch" },
                                  {
                                    "Annotdesc_user": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "CddInfo" }
                                        },
                                        "User-object_data": {
                                          "User-field": {
                                            "User-field_label": {
                                              "Object-id": { "Object-id_str": "version" }
                                            },
                                            "User-field_data": { "User-field_data_str": "3.11" }
                                          }
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "Annotdesc_create-date": {
                                      "Date": {
                                        "Date_std": {
                                          "Date-std": {
                                            "Date-std_year": "2014",
                                            "Date-std_month": "2",
                                            "Date-std_day": "28",
                                            "Date-std_hour": "14",
                                            "Date-std_minute": "51",
                                            "Date-std_second": "48"
                                          }
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            },
                            "Seq-annot_data": {
                              "Seq-annot_data_ftable": {
                                "Seq-feat": [
                                  {
                                    "Seq-feat_data": {
                                      "SeqFeatData": { "SeqFeatData_region": "alcohol_DH_class_III" }
                                    },
                                    "Seq-feat_comment": "class III alcohol dehydrogenases",
                                    "Seq-feat_location": {
                                      "Seq-loc": {
                                        "Seq-loc_int": {
                                          "Seq-interval": {
                                            "Seq-interval_from": "9",
                                            "Seq-interval_to": "380",
                                            "Seq-interval_id": {
                                              "Seq-id": { "Seq-id_gi": "6320033" }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    "Seq-feat_ext": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "cddScoreData" }
                                        },
                                        "User-object_data": {
                                          "User-field": [
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "domain_from" }
                                              },
                                              "User-field_data": { "User-field_data_int": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "domain_to" }
                                              },
                                              "User-field_data": { "User-field_data_int": "367" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "definition" }
                                              },
                                              "User-field_data": { "User-field_data_str": "cd08300" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "short_name" }
                                              },
                                              "User-field_data": { "User-field_data_str": "alcohol_DH_class_III" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "score" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1838" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "evalue" }
                                              },
                                              "User-field_data": { "User-field_data_real": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "bit_score" }
                                              },
                                              "User-field_data": { "User-field_data_real": "711.692" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "specific" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "superfamily" }
                                              },
                                              "User-field_data": { "User-field_data_str": "cl16912" }
                                            }
                                          ]
                                        }
                                      }
                                    },
                                    "Seq-feat_dbxref": {
                                      "Dbtag": {
                                        "Dbtag_db": "CDD",
                                        "Dbtag_tag": {
                                          "Object-id": { "Object-id_id": "176260" }
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "Seq-feat_data": {
                                      "SeqFeatData": { "SeqFeatData_region": "AdhC" }
                                    },
                                    "Seq-feat_comment": "Zn-dependent alcohol dehydrogenases, class III [Energy production and conversion]",
                                    "Seq-feat_location": {
                                      "Seq-loc": {
                                        "Seq-loc_int": {
                                          "Seq-interval": {
                                            "Seq-interval_from": "9",
                                            "Seq-interval_to": "380",
                                            "Seq-interval_id": {
                                              "Seq-id": { "Seq-id_gi": "6320033" }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    "Seq-feat_ext": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "cddScoreData" }
                                        },
                                        "User-object_data": {
                                          "User-field": [
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "domain_from" }
                                              },
                                              "User-field_data": { "User-field_data_int": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "domain_to" }
                                              },
                                              "User-field_data": { "User-field_data_int": "364" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "definition" }
                                              },
                                              "User-field_data": { "User-field_data_str": "COG1062" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "short_name" }
                                              },
                                              "User-field_data": { "User-field_data_str": "AdhC" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "score" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1496" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "evalue" }
                                              },
                                              "User-field_data": { "User-field_data_real": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "bit_score" }
                                              },
                                              "User-field_data": { "User-field_data_real": "579.947" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "specific" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            }
                                          ]
                                        }
                                      }
                                    },
                                    "Seq-feat_dbxref": {
                                      "Dbtag": {
                                        "Dbtag_db": "CDD",
                                        "Dbtag_tag": {
                                          "Object-id": { "Object-id_id": "223990" }
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "Seq-feat_data": {
                                      "SeqFeatData": {
                                        "SeqFeatData_site": { "-value": "other" }
                                      }
                                    },
                                    "Seq-feat_comment": "NAD binding site [chemical binding]",
                                    "Seq-feat_location": {
                                      "Seq-loc": {
                                        "Seq-loc_mix": {
                                          "Seq-loc-mix": {
                                            "Seq-loc": [
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "49",
                                                    "Seq-interval_to": "50",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "178",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "182",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "203",
                                                    "Seq-interval_to": "207",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "227",
                                                    "Seq-interval_to": "228",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "232",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "275",
                                                    "Seq-interval_to": "276",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "280",
                                                    "Seq-interval_to": "281",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "299",
                                                    "Seq-interval_to": "300",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "324",
                                                    "Seq-interval_to": "326",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "376",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    },
                                    "Seq-feat_ext": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "cddSiteScoreData" }
                                        },
                                        "User-object_data": {
                                          "User-field": [
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "completeness" }
                                              },
                                              "User-field_data": { "User-field_data_real": "1" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "feature-ID" }
                                              },
                                              "User-field_data": { "User-field_data_int": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "specific" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "nonredundant" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "definition" }
                                              },
                                              "User-field_data": { "User-field_data_str": "cd08300" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "short_name" }
                                              },
                                              "User-field_data": { "User-field_data_str": "alcohol_DH_class_III" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "from" }
                                              },
                                              "User-field_data": { "User-field_data_int": "9" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "to" }
                                              },
                                              "User-field_data": { "User-field_data_int": "380" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "score" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1838" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "evalue" }
                                              },
                                              "User-field_data": { "User-field_data_real": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "bit_score" }
                                              },
                                              "User-field_data": { "User-field_data_real": "711.692" }
                                            }
                                          ]
                                        }
                                      }
                                    },
                                    "Seq-feat_dbxref": {
                                      "Dbtag": {
                                        "Dbtag_db": "CDD",
                                        "Dbtag_tag": {
                                          "Object-id": { "Object-id_id": "176260" }
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "Seq-feat_data": {
                                      "SeqFeatData": {
                                        "SeqFeatData_site": { "-value": "other" }
                                      }
                                    },
                                    "Seq-feat_comment": "substrate binding site [chemical binding]",
                                    "Seq-feat_location": {
                                      "Seq-loc": {
                                        "Seq-loc_mix": {
                                          "Seq-loc-mix": {
                                            "Seq-loc": [
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "48",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "50",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "59",
                                                    "Seq-interval_to": "61",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "70",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "96",
                                                    "Seq-interval_to": "97",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "178",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "301",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "325",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    },
                                    "Seq-feat_ext": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "cddSiteScoreData" }
                                        },
                                        "User-object_data": {
                                          "User-field": [
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "completeness" }
                                              },
                                              "User-field_data": { "User-field_data_real": "1" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "feature-ID" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "specific" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "nonredundant" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "definition" }
                                              },
                                              "User-field_data": { "User-field_data_str": "cd08300" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "short_name" }
                                              },
                                              "User-field_data": { "User-field_data_str": "alcohol_DH_class_III" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "from" }
                                              },
                                              "User-field_data": { "User-field_data_int": "9" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "to" }
                                              },
                                              "User-field_data": { "User-field_data_int": "380" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "score" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1838" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "evalue" }
                                              },
                                              "User-field_data": { "User-field_data_real": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "bit_score" }
                                              },
                                              "User-field_data": { "User-field_data_real": "711.692" }
                                            }
                                          ]
                                        }
                                      }
                                    },
                                    "Seq-feat_dbxref": {
                                      "Dbtag": {
                                        "Dbtag_db": "CDD",
                                        "Dbtag_tag": {
                                          "Object-id": { "Object-id_id": "176260" }
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "Seq-feat_data": {
                                      "SeqFeatData": {
                                        "SeqFeatData_site": { "-value": "other" }
                                      }
                                    },
                                    "Seq-feat_comment": "dimer interface [polypeptide binding]",
                                    "Seq-feat_location": {
                                      "Seq-loc": {
                                        "Seq-loc_mix": {
                                          "Seq-loc-mix": {
                                            "Seq-loc": [
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "104",
                                                    "Seq-interval_to": "105",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "108",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "110",
                                                    "Seq-interval_to": "111",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "113",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "115",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "266",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "271",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "282",
                                                    "Seq-interval_to": "283",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "290",
                                                    "Seq-interval_to": "293",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "302",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "305",
                                                    "Seq-interval_to": "312",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "315",
                                                    "Seq-interval_to": "317",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "320",
                                                    "Seq-interval_to": "325",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    },
                                    "Seq-feat_ext": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "cddSiteScoreData" }
                                        },
                                        "User-object_data": {
                                          "User-field": [
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "completeness" }
                                              },
                                              "User-field_data": { "User-field_data_real": "1" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "feature-ID" }
                                              },
                                              "User-field_data": { "User-field_data_int": "2" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "specific" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "nonredundant" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "definition" }
                                              },
                                              "User-field_data": { "User-field_data_str": "cd08300" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "short_name" }
                                              },
                                              "User-field_data": { "User-field_data_str": "alcohol_DH_class_III" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "from" }
                                              },
                                              "User-field_data": { "User-field_data_int": "9" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "to" }
                                              },
                                              "User-field_data": { "User-field_data_int": "380" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "score" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1838" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "evalue" }
                                              },
                                              "User-field_data": { "User-field_data_real": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "bit_score" }
                                              },
                                              "User-field_data": { "User-field_data_real": "711.692" }
                                            }
                                          ]
                                        }
                                      }
                                    },
                                    "Seq-feat_dbxref": {
                                      "Dbtag": {
                                        "Dbtag_db": "CDD",
                                        "Dbtag_tag": {
                                          "Object-id": { "Object-id_id": "176260" }
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "Seq-feat_data": {
                                      "SeqFeatData": {
                                        "SeqFeatData_site": { "-value": "other" }
                                      }
                                    },
                                    "Seq-feat_comment": "catalytic Zn binding site [ion binding]",
                                    "Seq-feat_location": {
                                      "Seq-loc": {
                                        "Seq-loc_mix": {
                                          "Seq-loc-mix": {
                                            "Seq-loc": [
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "48",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_int": {
                                                  "Seq-interval": {
                                                    "Seq-interval_from": "70",
                                                    "Seq-interval_to": "71",
                                                    "Seq-interval_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "178",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    },
                                    "Seq-feat_ext": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "cddSiteScoreData" }
                                        },
                                        "User-object_data": {
                                          "User-field": [
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "completeness" }
                                              },
                                              "User-field_data": { "User-field_data_real": "1" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "feature-ID" }
                                              },
                                              "User-field_data": { "User-field_data_int": "3" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "specific" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "nonredundant" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "definition" }
                                              },
                                              "User-field_data": { "User-field_data_str": "cd08300" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "short_name" }
                                              },
                                              "User-field_data": { "User-field_data_str": "alcohol_DH_class_III" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "from" }
                                              },
                                              "User-field_data": { "User-field_data_int": "9" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "to" }
                                              },
                                              "User-field_data": { "User-field_data_int": "380" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "score" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1838" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "evalue" }
                                              },
                                              "User-field_data": { "User-field_data_real": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "bit_score" }
                                              },
                                              "User-field_data": { "User-field_data_real": "711.692" }
                                            }
                                          ]
                                        }
                                      }
                                    },
                                    "Seq-feat_dbxref": {
                                      "Dbtag": {
                                        "Dbtag_db": "CDD",
                                        "Dbtag_tag": {
                                          "Object-id": { "Object-id_id": "176260" }
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "Seq-feat_data": {
                                      "SeqFeatData": {
                                        "SeqFeatData_site": { "-value": "other" }
                                      }
                                    },
                                    "Seq-feat_comment": "structural Zn binding site [ion binding]",
                                    "Seq-feat_location": {
                                      "Seq-loc": {
                                        "Seq-loc_mix": {
                                          "Seq-loc-mix": {
                                            "Seq-loc": [
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "100",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "103",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "106",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              },
                                              {

                                              },
                                              {
                                                "Seq-loc_pnt": {
                                                  "Seq-point": {
                                                    "Seq-point_point": "114",
                                                    "Seq-point_id": {
                                                      "Seq-id": { "Seq-id_gi": "6320033" }
                                                    }
                                                  }
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    },
                                    "Seq-feat_ext": {
                                      "User-object": {
                                        "User-object_type": {
                                          "Object-id": { "Object-id_str": "cddSiteScoreData" }
                                        },
                                        "User-object_data": {
                                          "User-field": [
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "completeness" }
                                              },
                                              "User-field_data": { "User-field_data_real": "1" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "feature-ID" }
                                              },
                                              "User-field_data": { "User-field_data_int": "4" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "specific" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "nonredundant" }
                                              },
                                              "User-field_data": {
                                                "User-field_data_bool": { "-value": "true" }
                                              }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "definition" }
                                              },
                                              "User-field_data": { "User-field_data_str": "cd08300" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "short_name" }
                                              },
                                              "User-field_data": { "User-field_data_str": "alcohol_DH_class_III" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "from" }
                                              },
                                              "User-field_data": { "User-field_data_int": "9" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "to" }
                                              },
                                              "User-field_data": { "User-field_data_int": "380" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "score" }
                                              },
                                              "User-field_data": { "User-field_data_int": "1838" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "evalue" }
                                              },
                                              "User-field_data": { "User-field_data_real": "0" }
                                            },
                                            {
                                              "User-field_label": {
                                                "Object-id": { "Object-id_str": "bit_score" }
                                              },
                                              "User-field_data": { "User-field_data_real": "711.692" }
                                            }
                                          ]
                                        }
                                      }
                                    },
                                    "Seq-feat_dbxref": {
                                      "Dbtag": {
                                        "Dbtag_db": "CDD",
                                        "Dbtag_tag": {
                                          "Object-id": { "Object-id_id": "176260" }
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              ]
            },
            "Bioseq-set_annot": {
              "Seq-annot": {
                "Seq-annot_data": {
                  "Seq-annot_data_ftable": {
                    "Seq-feat": {
                      "Seq-feat_data": {
                        "SeqFeatData": {
                          "SeqFeatData_cdregion": {
                            "Cdregion": {
                              "Cdregion_frame": { "-value": "one" },
                              "Cdregion_code": {
                                "Genetic-code": {
                                  "Genetic-code_E": { "Genetic-code_E_id": "1" }
                                }
                              }
                            }
                          }
                        }
                      },
                      "Seq-feat_comment": "Bifunctional alcohol dehydrogenase and formaldehyde dehydrogenase; formaldehyde dehydrogenase activity is glutathione-dependent; functions in formaldehyde detoxification and formation of long chain and complex alcohols, regulated by Hog1p-Sko1p; protein abundance increases in response to DNA replication stress",
                      "Seq-feat_product": {
                        "Seq-loc": {
                          "Seq-loc_whole": {
                            "Seq-id": { "Seq-id_gi": "6320033" }
                          }
                        }
                      },
                      "Seq-feat_location": {
                        "Seq-loc": {
                          "Seq-loc_int": {
                            "Seq-interval": {
                              "Seq-interval_from": "0",
                              "Seq-interval_to": "1160",
                              "Seq-interval_strand": {
                                "Na-strand": { "-value": "plus" }
                              },
                              "Seq-interval_id": {
                                "Seq-id": { "Seq-id_gi": "296143201" }
                              }
                            }
                          }
                        }
                      },
                      "Seq-feat_dbxref": {
                        "Dbtag": {
                          "Dbtag_db": "SGD",
                          "Dbtag_tag": {
                            "Object-id": { "Object-id_str": "S000002327" }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

*/
