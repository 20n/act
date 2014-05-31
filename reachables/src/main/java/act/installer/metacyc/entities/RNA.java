package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import act.installer.metacyc.NXT;
import java.util.Set;
import java.util.HashSet;

public class RNA extends BPElement {
  Resource rnaRef; // entityReference field pointing to a ProteinRNARef
  Resource cellularLocation; // cellularLocationVocab that is a act.installer.metacyc.annotations.Term

  public RNA(BPElement basics, Resource r, Resource localization) {
    super(basics);
    this.rnaRef = r;
    this.cellularLocation = localization;
  }

  @Override
  public Set<Resource> field(NXT typ) {
    Set<Resource> s = new HashSet<Resource>();
    if (typ == NXT.ref) {
      s.add(this.rnaRef);
    }
    return s;
  }

}
