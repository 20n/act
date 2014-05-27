package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;

public class Complex extends BPElement {
  Set<Resource> componentStoichiometry;
  Set<Resource> components;

  public Complex(BPElement basics, Set<Resource> stoi, Set<Resource> comp) {
    super(basics);
    this.componentStoichiometry = stoi;
    this.components = comp;
  }
}


