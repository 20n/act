package act.installer.metacyc;

public class Resource {
  String id; // this id is without the # in front of it

  public Resource(String id) { this.id = id; }

  @Override
  public String toString() {
    return "R:" + this.id;
  }

  private String mostSeenPrefix = "http://biocyc.org/biopax/biopax-level3";
  private int mostSeenPrefixLen = mostSeenPrefix.length(); 
  public String getLocal() {
    if (this.id.startsWith(mostSeenPrefix))
      return this.id.substring(mostSeenPrefixLen);
    else
      return this.id; // unexpected global uri prefix. return the entire string
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Resource)) return false;
    return this.id.equals(((Resource)o).id);
  }

  @Override
  public int hashCode() {
    return this.id.hashCode();
  }
}
