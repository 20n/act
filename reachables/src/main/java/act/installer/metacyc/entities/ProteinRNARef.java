package act.installer.metacyc.entities;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;

public class ProteinRNARef extends BPElement {
  Resource organism; // referring to the BioSource this protein is in
  String sequence; // protein AA sequence
  Set<Resource> memberEntityRef; // references to other proteins?

  public ProteinRNARef(BPElement basics, Resource org, String seq, Set<Resource> memRef) {
    super(basics);
    this.organism = org;
    this.sequence = seq;
    this.memberEntityRef = memRef;
  }
}

