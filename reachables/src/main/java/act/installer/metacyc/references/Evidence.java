package act.installer.metacyc.references;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;

public class Evidence extends BPElement {
  // xref (from super BPElement) points to a publication
  Set<Resource> evidenceCodes; // points to a Term (EvidenceCodeVocab) string, e.g., "EV-COMP-AINF"

  public Evidence(BPElement basics, Set<Resource> codes) {
    super(basics);
    this.evidenceCodes = codes;
  }
}

