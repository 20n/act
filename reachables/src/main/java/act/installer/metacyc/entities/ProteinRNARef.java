package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import act.installer.metacyc.NXT;
import org.json.JSONObject;
import java.util.Set;
import java.util.HashSet;

public class ProteinRNARef extends BPElement {
  Resource organism; // referring to the BioSource this protein is in
  String sequence; // protein AA sequence

  String standardName;
  Set<String> comments;

  Set<Resource> memberEntityRef; // references to other proteins?

  // we call these when looking up date in OrganismCompositionMongoWriter:getSequence(Catalysis)
  public String getSeq() { return this.sequence; }
  public Resource getOrg() { return this.organism; }
  public String getStandardName() { return this.standardName; }
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
