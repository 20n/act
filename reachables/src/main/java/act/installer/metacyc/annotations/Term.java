package act.installer.metacyc.annotations;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;
import org.json.JSONArray;

public class Term extends BPElement {
  Set<String> terms;

  public Set<String> getTerms() { return this.terms; }

  public Term(BPElement basics, Set<String> t) {
    super(basics);
    this.terms = t;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (terms != null)
      if (terms.size() == 1)
        o.add("terms", terms.toArray()[0]);
      else
        o.add("terms", new JSONArray(terms.toArray()));
    return o.getJSON();
  }
}

