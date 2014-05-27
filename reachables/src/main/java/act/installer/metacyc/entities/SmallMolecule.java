package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;

public class SmallMolecule extends BPElement {
  Resource smRef; // entityReference field pointing to a SmallMoleculeRef
  Resource cellularLocation;

  public SmallMolecule(BPElement basics, Resource smRef, Resource loc) {
    super(basics);
    this.smRef = smRef;
    this.cellularLocation = loc;
  }
}

