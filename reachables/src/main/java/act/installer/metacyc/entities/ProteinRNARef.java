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

package act.installer.metacyc.entities;

import act.installer.metacyc.BPElement;
import act.installer.metacyc.JsonHelper;
import act.installer.metacyc.NXT;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.Resource;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class ProteinRNARef extends BPElement {
  Resource organism; // referring to the BioSource this protein is in
  String sequence; // protein AA sequence

  String standardName;
  Set<String> comments;

  Set<Resource> memberEntityRef; // references to other proteins?

  // we call these when looking up date in OrganismCompositionMongoWriter:getSequence(Catalysis)
  public String getSeq() { return this.sequence; }
  public Resource getOrg() { return this.organism; }

  // Allow access to standard name that was in BPElement
  public String getStandardName() {
    if (this.standardName == null) {
      return super.getStandardName();
    } else {
      return this.standardName;
    }
  }
  public Set<String> getComments() { return this.comments; }
  public Set<Resource> getRefs() { return this.memberEntityRef; }

  public ProteinRNARef(BPElement basics, Resource org, String seq, String standardName, Set<String> comments, Set<Resource> memRef) {
    super(basics);
    this.organism = org;
    this.sequence = seq;
    this.memberEntityRef = memRef;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.organism) {
      s.add(this.organism);
    }
    return s;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("seq", sequence);
    if (organism != null)
      o.add("org", src.resolve(organism).expandedJSON(src));
    if (memberEntityRef != null)
      for (BPElement m : src.resolve(memberEntityRef))
        o.add("members", m.expandedJSON(src));
    return o.getJSON();
  }
}

/*
<bp:ProteinReference rdf:ID="ProteinReference1733619">
            <bp:organism rdf:resource="#BioSource1698655"/>
            <bp:sequence rdf:datatype="http://www.w3.org/2001/                  XMLSchema#string">MVTLSPRKRHGGSLKAGRSLQWPRSSR*GVLAIKGSCCRLKQGRGRS*              SSPAGWWDVLRGGNLLLCPRGTTYADATLTAIA*RPKCLRAGWCPQLVLSASALWL*                       PRANMKTNSYLSVDGRSPFDNLF*VACCVVLSSSI*PCRQD*PRACREPRDCSRGLP*                      KPCTLTQGFDNHFNQAFYLYQAPKRLVYKHELNSFNPRRTSCVGATGKVKQNLLCYVDIIECRERLGYLL*KD*      PALALQGITSYSNREQQKPK*GAIKMHRVR*NGTNTVISVKVKV*SRKAKWN*                           ATGLTLFSFENTVSLLPRLGVRSKGLIGLQSWFIFAVTVVA*AALELVLSQPPE*LGCTTCPHNLSLLSPASHAC* GPWTSGYRLRSWGHPAEGCQGRPLGTLSFDLCWALLLQLPRCRHLQSGCHAVEALTWVQHFDCVPPCLCFTNAVHLAVRGDEG*AHWWAECLLITWLFSEFIC*GRGLRS*VRPFVSLCSILH*VWALAHSLAVRLNIS*AHVAFDHHFLRRAS*LLSG*EHLFSQEGGLFPPELDFA*SSKVFPLEPPILVQVTPALLMALLLGNI*SGDALND*FV*                     VLGRRLRKAGFRQVQFREFSLFVD*NMTDCKQTTLQMYLNCAEVSCPKV*D*VINCLPTVGVVNEKESVL*KNKIFK*  NII</bp:sequence>
            <bp:xref rdf:resource="#UnificationXref1733621"/>
            <bp:xref rdf:resource="#UnificationXref1733620"/>
            <bp:standardName rdf:datatype="http://www.w3.org/2001/              XMLSchema#string">succinate dehydrogenase complex, subunit D, integral membrane protein</bp:standardName>
            <bp:comment rdf:datatype="http://www.w3.org/2001/                   XMLSchema#string">GO:0005506</bp:comment>
          </bp:ProteinReference>
   */
