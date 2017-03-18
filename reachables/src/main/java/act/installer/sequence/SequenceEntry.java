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

import java.util.List;
import java.util.Set;
import act.server.MongoDB;
import com.mongodb.DBObject;
import act.shared.Seq;
import org.json.JSONObject;

public abstract class SequenceEntry {
  abstract Long getOrgId();
  abstract String getEc();
  abstract String getSeq();
  abstract List<JSONObject> getRefs();
  abstract Set<Long> getCatalyzedRxns();
  abstract DBObject getMetadata();

  public int writeToDB(MongoDB db, Seq.AccDB src) {
    Long org_id = getOrgId();
    String org = org_id != null ? db.getOrganismNameFromId(org_id) : null;
    // note that we install the full data as "metadata" in the db
    // what we extract here are just things we might want to use are keys
    // and join them against other collections.. e.g., (ec+org+pmid) can
    // be used to assign sequences to brenda actfamilies
    int id = db.submitToActSeqDB(
                src, // genbank, uniprot, swissprot, trembl, embl
                getEc(),
                org, org_id,
                getSeq(),
                getRefs(),
                getCatalyzedRxns(),
                getMetadata());

    return id;

    // ==== Fields of importance in SwissProt ====
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
}


