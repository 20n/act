package act.installer.metacyc.references;

import act.installer.metacyc.Resource;
import act.installer.metacyc.BPElement;

public class Relationship extends BPElement {
  Resource relationshipType; // ref to Term
  String db;
  String id;

  public String getRelnID() { return this.id; }
  public String getRelnDB() { return this.db; }

  public Relationship(BPElement basics, Resource typ, String db, String id) {
    super(basics);
    this.relationshipType = typ;
    this.db = db;
    this.id = id;
  }
}

