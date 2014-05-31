package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.entities.SmallMoleculeRef;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.NXT;
import act.installer.metacyc.JsonHelper;
import java.util.Set;
import java.util.HashSet;
import org.json.JSONObject;

public class SmallMolecule extends BPElement {
  Resource smRef; // entityReference field pointing to a SmallMoleculeRef
  Resource cellularLocation;

  public Resource getSMRef() { return this.smRef; }
  public Resource getCellularLocation() { return this.cellularLocation; }

  public SmallMolecule(BPElement basics, Resource smRef, Resource loc) {
    super(basics);
    this.smRef = smRef;
    this.cellularLocation = loc;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.ref) {
      s.add(this.smRef);
    }
    return s;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("ref", src.resolve(smRef).expandedJSON(src));
    if (cellularLocation != null) 
      o.add("loc", src.resolve(cellularLocation).expandedJSON(src));
    return o.getJSON();
  }

}

