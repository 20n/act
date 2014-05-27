package act.installer.metacyc.references;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;

public class Provenance extends BPElement {
  // only populated field is the name field from BPElement
  // e.g., name = Escherichia coli MS 124-1, whole genome shotgun sequencing project.
  public Provenance(BPElement basics) {
    super(basics);
  }
}

