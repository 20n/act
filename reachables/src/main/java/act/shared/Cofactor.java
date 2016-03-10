package act.shared;

import java.io.Serializable;
import java.util.List;

public class Cofactor implements Serializable {
  private static final long serialVersionUID = 42L;
  public Cofactor() { /* default constructor for serialization */ }

  private Long uuid;
  private String inchi;
  private List<String> names; // synonyms

  public Cofactor(String inchi, List<String> names) {
    this.inchi = inchi;
    this.names = names;
  }

  public Long getUuid() { return this.uuid; }
  public List<String> getNames() { return this.names; }
  public String getInChI() { return this.inchi; }
}
