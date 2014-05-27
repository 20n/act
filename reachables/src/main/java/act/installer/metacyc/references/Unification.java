package act.installer.metacyc.references;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;

public class Unification extends BPElement {
  String id;
  String db;

  public Unification(BPElement basics, String db, String id) {
    super(basics);
    this.db = db;
    this.id = id;
  }
}

