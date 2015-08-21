package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.NXT;
import act.installer.metacyc.OrganismComposition;
import act.installer.metacyc.JsonHelper;
import org.json.JSONObject;
import java.util.Set;
import java.util.HashSet;

public class SmallMoleculeRef extends BPElement {
  Set<Resource> memberEntityRefs; // reference to other small molecules?
  Resource chemicalStructure;
  Float molecularWeight;

  public Resource getChemicalStructure() { return this.chemicalStructure; }
  public Float getMolecularWeight() { return this.molecularWeight; }
  public Set<Resource> getMemberEntityRefs() {
    return this.memberEntityRefs;
  }

  public SmallMoleculeRef(BPElement basics, Set<Resource> memRefs, Resource struc, Float molWeight) {
    super(basics);
    this.memberEntityRefs = memRefs;
    this.chemicalStructure = struc;
    this.molecularWeight = molWeight;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.structure) {
      s.add(this.chemicalStructure);
    } else if (typ == NXT.members) {
      s.addAll(this.memberEntityRefs);
    }
    return s;
  }

  public JSONObject expandedJSON(OrganismComposition src) {
    JsonHelper o = new JsonHelper(src);
    o.add("name", super.standardName); // from BPElement
    if (memberEntityRefs != null)
      for (BPElement mem : src.resolve(memberEntityRefs))
        o.add("members", mem.expandedJSON(src));
    if (molecularWeight != null)
      o.add("mol_weight", molecularWeight);
    if (chemicalStructure != null)
      o.add("structure", src.resolve(chemicalStructure).expandedJSON(src));
    return o.getJSON();
  }
}

