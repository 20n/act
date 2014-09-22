package act.shared;

import java.io.Serializable;
import java.util.List;
import act.shared.helpers.MongoDBToJSON;
import com.mongodb.DBObject;

public class Seq implements Serializable {
	private static final long serialVersionUID = 42L;
	Seq() { /* default constructor for serialization */ }

  private int id;
  private String sequence;
  private String ecnum;
  private String organism;
  private Long organismIDs;
  private List<String> references;
  private DBObject metadata; 
  
  public Seq(long id, String e, Long oid, String o, String s, List<String> r, DBObject m) {
    this.id = (new Long(id)).intValue();
    this.sequence = s;
    this.ecnum = e;
    this.organism = o;
    this.organismIDs = oid;
    this.references = r;
    this.metadata = m;    // MongoDBToJSON.conv if needed to get to JSONObject
  }

  public String get_ec() { return this.ecnum; }
  public String get_org_name() { return this.organism; }
  public List<String> get_references() { return this.references; }
 
}
