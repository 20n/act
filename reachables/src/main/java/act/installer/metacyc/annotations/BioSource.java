package act.installer.metacyc.annotations;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class BioSource extends BPElement {
  // contains only a xref and a name (both from BPElement)

  // e.g.,  {
  //          ID: BioSource32423, 
  //          xref: #UnificationXref32424, 
  //          name: "Escherichia coli MS 124-1"
  //        }
  // the xref points to a unification ref: 
  //        { id: "679205", db: "NCBI Taxonomy" }
  // which is great because we can then lookup the full lineage:
  // cellular organisms; Bacteria; Proteobacteria; 
  //  Gammaproteobacteria; Enterobacteriales; 
  //  Enterobacteriaceae; Escherichia; Escherichia coli
  // from http://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?mode=Info&id=679205

  public BioSource(BPElement basics) {
    super(basics);
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    if (name != null) 
      o.add("name", this.name);
    return o.getJSON();
  }
}

