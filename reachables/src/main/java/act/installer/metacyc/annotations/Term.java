package act.installer.metacyc.annotations;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;
import java.util.Set;

public class Term extends BPElement {
  Set<String> terms;

  public Term(BPElement basics, Set<String> t) {
    super(basics);
    this.terms = t;
  }
}

