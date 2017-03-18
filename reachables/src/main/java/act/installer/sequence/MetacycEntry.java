/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package act.installer.sequence;

import act.shared.Reaction;
import act.shared.helpers.MongoDBToJSON;
import act.shared.sar.SAR;
import com.mongodb.DBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MetacycEntry extends SequenceEntry {
  JSONObject data;

  public static SequenceEntry initFromMetacycEntry(String sequence, Long org_id, String standardName, String ecnumber, Set<String> comments, Set<JSONObject> metacyc_refs, long rxnid, Reaction rxn, String activation_inhibition, String direction) {
    return new MetacycEntry(sequence, org_id, standardName, ecnumber, comments, metacyc_refs, rxnid, rxn, activation_inhibition, direction);
  }

  public MetacycEntry(String sequence, Long org_id, String standardName, String ec, Set<String> comments, Set<JSONObject> metacyc_refs, long rxnid, Reaction rxn, String activation_inhibition, String direction) {

    this.sequence = sequence;
    this.org_id = org_id;
    this.refs = new ArrayList<>(metacyc_refs);
    this.ec = ec;
    this.accessions = new HashSet<String>();

    // inits this.catalyzed_{rxns, substrates, products}
    extract_catalyzed_reactions(rxnid, rxn);

    // new Seq(..) looks at the metadata in this.data for SwissProt fields:
    // this.data { "name" : gene_name_eg_Adh1 }
    // this.data { "proteinExistence": { "type" : "evidence at transcript level" });
    // this.data { "comment": [ { "type": "catalytic activity", "text": uniprot_activity_annotation } ] }
    // this.data { "accession" : ["Q23412", "P87D78"] }
    // we manually add these fields so that we have consistent data

    this.data = new JSONObject();
    this.data.put("name", standardName);
    this.data.put("proteinExistence", new JSONObject());
    this.data.put("comment", new JSONArray());
    this.data.put("accession", new JSONObject());

    // extract_metadata processes this.data, so do that only after updating
    // this.data with the proxy fields from above.
    this.metadata = extract_metadata();
  }

  private void extract_catalyzed_reactions(long rxnid, Reaction rxn) {
    this.sar = new SAR();
    this.catalyzed_rxns = new HashSet<Long>();
    this.catalyzed_rxns_to_substrates = new HashMap<Long, Set<Long>>();
    this.catalyzed_rxns_to_products = new HashMap<Long, Set<Long>>();
    this.catalyzed_substrates_uniform = new HashSet<Long>();
    this.catalyzed_substrates_diverse = new HashSet<Long>();
    this.catalyzed_products_uniform = new HashSet<Long>();
    this.catalyzed_products_diverse = new HashSet<Long>();

    Set<Long> rxn2substrates = new HashSet(Arrays.asList(rxn.getSubstrates()));
    Set<Long> rxn2products = new HashSet(Arrays.asList(rxn.getProducts()));

    this.catalyzed_rxns.add(rxnid);
    this.catalyzed_rxns_to_substrates.put(rxnid, rxn2substrates);
    this.catalyzed_rxns_to_products.put(rxnid, rxn2products);
    // we add all to the diverse set...
    this.catalyzed_substrates_diverse.addAll(rxn2substrates);
    this.catalyzed_products_diverse.addAll(rxn2products);
    // we do not add anything to the uniform set...

    // TODO... later, move the cofactors to the uniform set,
    //          only leave the non-cofactors in the diverse set
  }

  private DBObject extract_metadata() {
    // cannot directly return this.data coz in Seq.java
    // we expect certain specific JSON format fields
    return MongoDBToJSON.conv(this.data);
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

  @Override
  public String toString() {
    return this.data.toString(2); // format it with 2 spaces
  }
}
