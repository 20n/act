package act.installer.metacyc;

public class Resource {
  String id; // this id is without the # in front of it

  public Resource(String id) { this.id = id; }

  @Override
  public String toString() {
    return "R:" + this.id;
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
