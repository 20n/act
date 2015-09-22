package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.NXT;
import java.util.Set;
import java.util.HashSet;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class Complex extends BPElement {
  Set<Resource> componentStoichiometry;
  Set<Resource> components;

  public Complex(BPElement basics, Set<Resource> stoi, Set<Resource> comp) {
    super(basics);
    this.componentStoichiometry = stoi;
    this.components = comp;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.components) {
      s.addAll(this.components);
    }
    return s;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("name", super.standardName); // from BPElement
    if (components != null)
      for (BPElement c : src.resolve(components))
        o.add("component", c.expandedJSON(src));
    if (componentStoichiometry != null) {
      int pre = "R:http://biocyc.org/biopax/biopax-level3".length();
      String str = "";
      for (BPElement s : src.resolve(componentStoichiometry)) {
        JSONObject st = s.expandedJSON(src);
        str += (str.length()==0 ? "" : " + ") + st.get("c") + " x " + ((String)st.get("on")).substring(pre);
      }
      o.add("multiplicity", str);
    }
    return o.getJSON();
  }
}


