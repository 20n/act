package act.shared.sar;
import java.util.HashMap;

public class SAR {
  public enum ConstraintPresent { should_have, should_not_have };
  public enum ConstraintContent { substructure };
  public enum ConstraintRequire { soft, hard };

  private HashMap<Object, SARConstraint> constraints;
  public SAR() {
    this.constraints = new HashMap<Object, SARConstraint>();
  }

  public void addConstraint(ConstraintPresent presence, ConstraintContent data_c, ConstraintRequire typ, Object data) {
    this.constraints.put(data, new SARConstraint(presence, data_c, typ));
  }

  public HashMap<Object, SARConstraint> getConstraints() {
    return this.constraints;
  }

  @Override
  public String toString() { return this.constraints.toString(); }
  @Override
  public int hashCode() { 
    int hash = 42;
    for (Object o : this.constraints.keySet())
      hash ^= o.hashCode() ^ this.constraints.get(o).hashCode();
    return hash; 
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SAR)) return false;
    SAR other = (SAR) o;
    if (this.constraints.size() != other.constraints.size()) return false;
    for (Object oo : this.constraints.keySet())
      if (!other.constraints.containsKey(oo) || other.constraints.get(oo) != this.constraints.get(oo))
        return false;
    return true;
  }
}
