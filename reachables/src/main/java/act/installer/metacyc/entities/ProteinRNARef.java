package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class ProteinRNARef extends BPElement {
  Resource organism; // referring to the BioSource this protein is in
  String sequence; // protein AA sequence
  Set<Resource> memberEntityRef; // references to other proteins?

  public ProteinRNARef(BPElement basics, Resource org, String seq, Set<Resource> memRef) {
    super(basics);
    this.organism = org;
    this.sequence = seq;
    this.memberEntityRef = memRef;
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

