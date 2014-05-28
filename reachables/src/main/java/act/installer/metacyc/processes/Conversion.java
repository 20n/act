package act.installer.metacyc.processes;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.annotations.Stoichiometry;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import java.util.Set;
import org.json.JSONObject;
import org.biopax.paxtools.model.level3.ConversionDirectionType; // enum, so ok to use

public class Conversion extends BPElement {
  public enum TYPE { BIOCHEMICAL_RXN, TRANSPORT, TRANSPORT_W_BIOCHEMICAL_RXN };
  TYPE type;
  public TYPE getTyp() { return this.type; }
  Set<Resource> left, right;
  Set<Resource> participantStoichiometry;

  ConversionDirectionType dir; // REVERSIBLE, LEFT-TO-RIGHT or RIGHT-TO-LEFT: If a reaction will run in a single direction under all biological contexts then it is considered irreversible and has a direction. Otherwise it is reversible. MOSTLY not set.
  Boolean spontaneous; // MOSTLY not set
  Set<String> ecNumber;
  Set<Resource> deltaG;

  public Conversion(BPElement basics, Set<Resource> l, Set<Resource> r, Set<Resource> stoic, ConversionDirectionType dir, Boolean spont, Set<String> ec, Set<Resource> dG, TYPE t) {
    super(basics);
    this.left = l;
    this.right = r;
    this.participantStoichiometry = stoic;
    this.dir = dir;
    this.spontaneous = spont;
    this.ecNumber = ec;
    this.deltaG = dG;
    this.type = t;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (type != null) o.add("type", type.toString());
    if (dir != null) o.add("dir", dir.toString());
    if (left != null)
      for (BPElement l : src.resolve(left)) 
        o.add("left", l.expandedJSON(src));
    if (right != null)
      for (BPElement r : src.resolve(right)) 
        o.add("right", r.expandedJSON(src));
    if (participantStoichiometry != null) {
      int pre = "R:http://biocyc.org/biopax/biopax-level3".length();
      String str = "";
      for (BPElement s : src.resolve(participantStoichiometry)) {
        JSONObject st = s.expandedJSON(src);
        str += (str.length()==0 ? "" : " + ") + st.get("c") + " x " + ((String)st.get("on")).substring(pre);
      }
      o.add("stoichiometry", str);
    }
    if (spontaneous != null) o.add("spontaneous", spontaneous);
    if (ecNumber != null) o.add("ec", ecNumber.toString());
    if (deltaG != null)
      for (BPElement d : src.resolve(deltaG)) 
        o.add("deltaG", d.expandedJSON(src));
    return o.getJSON();
  }
}
