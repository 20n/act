package act.shared;

import java.io.Serializable;

public class Organism implements Serializable {
  private static final long serialVersionUID = 42L;
  Organism() { /* default constructor for serialization */ }

  private Long uuid, parent, ncbiID;
  private String name, rank;

  @Deprecated
  public Organism(long uuid, long ncbiID, String name) {
    this.uuid = uuid;
    this.ncbiID = ncbiID;
    this.name = name;
  }

  public Organism(long uuid, String name) {
    this.uuid = uuid;
    this.ncbiID = -1L;
    this.name = name;
  }

  public void setParent(Long parent) { this.parent = parent; }
  public void setRank(String rank) { this.rank= rank; }

  public Long getUUID() { return uuid; }
  public Long getParent() { return parent; }
  public Long getNCBIid() { return ncbiID; }
  public String getName() { return name; }
  public String getRank() { return rank; }

  @Override
  public String toString() {
    return name + "[id:" + uuid + ", parent: " + parent +
        ", rank: " + rank + ", ncbi: " + ncbiID + "]";
  }
}
