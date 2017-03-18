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

import act.installer.brenda.BrendaRxnEntry;
import act.installer.brenda.BrendaSupportingEntries;
import act.shared.Reaction;
import act.shared.Seq;
import act.shared.helpers.MongoDBToJSON;
import com.mongodb.DBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: make the SequenceEntry methods public so that this doesn't have to live in the installer.sequence package.
public class BrendaEntry extends SequenceEntry {
  // Note: this has been mostly copied from MetacycEntry.
  JSONObject data;

  public static SequenceEntry initFromBrendaEntry(
      long rxnId, Reaction rxn, BrendaRxnEntry brendaRxnEntry, BrendaSupportingEntries.Sequence brendaSequence,
      Long orgId) {
    return new BrendaEntry(
        brendaSequence.getSequence(),
        orgId,
        brendaSequence.getEntryName(),
        Collections.emptySet(),
        rxnId,
        rxn,
        brendaSequence.getBrendaId(),
        brendaSequence.getFirstAccessionCode(),
        brendaSequence.getFromExactMatch()
    );
  }

  public BrendaEntry(String sequence, Long orgId, String standardName, Set<String> comments,
                     long rxnid, Reaction rxn, Integer brendaId, String firstAccessionCode, Boolean fromExactMatch) {
    this.sequence = sequence;
    this.org_id = orgId;
    this.refs = new ArrayList<>();
    this.ec = rxn.getECNum();

    this.catalyzed_rxns = new HashSet<>(Collections.singletonList(rxnid));

    // new Seq(..) looks at the metadata in this.data for SwissProt fields:
    // this.data { "name" : gene_name_eg_Adh1 }
    // this.data { "proteinExistence": { "type" : "evidence at transcript level" });
    // this.data { "comment": [ { "type": "catalytic activity", "text": uniprot_activity_annotation } ] }
    // this.data { "accession" : ["Q23412", "P87D78"] }
    // we manually add these fields so that we have consistent data

    this.data = new JSONObject();
    this.data.put("name", standardName);
    this.data.put("proteinExistence", new JSONObject());
    this.data.put("comment",
        new JSONArray(new JSONObject[] { new JSONObject().put("type", "brenda_id").put("text", brendaId) }));
    this.data.put("accession",
        new JSONObject().put(Seq.AccType.uniprot.toString(),
            new JSONArray(Collections.singletonList(firstAccessionCode))));
    this.data.put("from_exact_match", fromExactMatch);

    // extract_metadata processes this.data, so do that only after updating
    // this.data with the proxy fields from above.
    this.metadata = extract_metadata();
  }

  private DBObject extract_metadata() {
    // cannot directly return this.data coz in Seq.java
    // we expect certain specific JSON format fields
    return MongoDBToJSON.conv(this.data);
  }

  DBObject metadata;
  List<JSONObject> refs;
  String sequence;
  Long org_id;
  String ec;
  Set<Long> catalyzed_rxns;

  @Override
  Long getOrgId() {
    return this.org_id;
  }

  @Override
  String getEc() {
    return this.ec;
  }

  @Override
  String getSeq() {
    return this.sequence;
  }

  @Override
  List<JSONObject> getRefs() {
    return this.refs;
  }

  @Override
  Set<Long> getCatalyzedRxns() {
    return this.catalyzed_rxns;
  }

  @Override
  DBObject getMetadata() {
    return this.metadata;
  }
}
