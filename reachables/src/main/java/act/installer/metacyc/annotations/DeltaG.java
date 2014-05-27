package act.installer.metacyc.annotations;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;

public class DeltaG extends BPElement {
  Float deltaGPrime0;

  public DeltaG(BPElement basics, Float dg) {
    super(basics);
    this.deltaGPrime0 = dg;
  }
}

