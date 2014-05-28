package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.entities.SmallMoleculeRef;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;

public class SmallMolecule extends BPElement {
  Resource smRef; // entityReference field pointing to a SmallMoleculeRef
  Resource cellularLocation;

  public SmallMolecule(BPElement basics, Resource smRef, Resource loc) {
    super(basics);
    this.smRef = smRef;
    this.cellularLocation = loc;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("ref", src.resolve(smRef).expandedJSON(src));
    if (cellularLocation != null) 
      o.add("loc", src.resolve(cellularLocation).expandedJSON(src));
    return o.getJSON();
  }
}

