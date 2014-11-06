package act.shared.sar;

public class SARConstraint {
  SARConstraint(SAR.ConstraintPresent p, SAR.ConstraintContent c, SAR.ConstraintRequire r) {
    this.presence = p;
    this.contents = c;
    this.requires = r;
  }
  public SAR.ConstraintPresent presence;
  public SAR.ConstraintContent contents;
  public SAR.ConstraintRequire requires;

  @Override
  public String toString() { return this.presence + "/" + this.contents + "/" + this.requires; }
  @Override
  public int hashCode() { return this.presence.hashCode() ^ this.contents.hashCode() ^ this.requires.hashCode(); }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SARConstraint)) return false;
    SARConstraint other = (SARConstraint) o;
    return other.presence == this.presence && other.contents == this.contents && other.requires == this.requires;
  }
}


