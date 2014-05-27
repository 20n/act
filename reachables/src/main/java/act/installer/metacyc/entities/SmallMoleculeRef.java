package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;

public class SmallMoleculeRef extends BPElement {
  Set<Resource> memberEntityRefs; // reference to other small molecules?
  Resource chemicalStructure;
  Float molecularWeight;

  public SmallMoleculeRef(BPElement basics, Set<Resource> memRefs, Resource struc, Float molWeight) {
    super(basics);
    this.memberEntityRefs = memRefs;
    this.chemicalStructure = struc;
    this.molecularWeight = molWeight;
  }
}

