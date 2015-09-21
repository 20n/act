package act.server.Molecules;

import java.util.HashMap;

class Bond {
  BondType e; Atom a1; Atom a2;
  public Bond(Atom a1, BondType e, Atom a2) {
    this.e = e;
    this.a1 = a1;
    this.a2 = a2;
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Bond))
      return false;
    Bond b = (Bond)o;
    return ((b.a1.equals(this.a1) && b.a2.equals(this.a2))
        || (b.a1.equals(this.a2) && b.a2.equals(this.a1)))
        && b.e.equals(this.e);
  }
  @Override
  public int hashCode() {
    // see this http://stackoverflow.com/questions/4885095/what-is-the-reason-behind-enum-hashcode
    // for why Java Enum's hashCode's are not consistent across VMs.
    return this.e.valueBasedHashCode() ^ this.a1.hashCode() ^ this.a2.hashCode();
  }
  @Override
  public String toString() {
    // canonicalize the bond representation by artificially imposing an ordering on the atom (by comparing the string representations)
    if (this.a1.toString().compareTo(this.a2.toString()) < 0)
      return this.a1.toString() + this.e + this.a2.toString();
    else
      return this.a2.toString() + this.e + this.a1.toString();
  }
}

public class BRO extends RO {
  HashMap<Bond, Integer> bondSummary;

  public BRO() {}

  public BRO(HashMap<Bond, Integer> delta) {
    super(null); // this is not a traditional RO...
    this.bondSummary = new HashMap<Bond, Integer>();
    // Do not add the bonds that have 0 count....
    for (Bond b : delta.keySet())
      if (delta.get(b) != 0)
        this.bondSummary.put(b, delta.get(b));
  }

  @SuppressWarnings("unchecked")
  public static BRO deserialize(String s) {
    return new BRO((HashMap<Bond, Integer>)getXStream().fromXML(s));
  }

  public String serialize() {
    return getXStream().toXML(this.bondSummary);
  }

  /*
   * We override toString, hashcode and equals for this operator as it cannot reuse
   * the parent RO's methods that rely on "added" and "deleted" to be molecular graphs...
   */

  @Override
  public String toString() {
    // might show up with zero counts (those are not relevant..)
    return this.bondSummary.toString();
  }

  @Override
  public int hashCode() {
    int h = 42;
    // the hashcode will be identical if the sign is changed on counts; so it works to make reverse BROs equal.
    for (Bond b : this.bondSummary.keySet())
      if (this.bondSummary.get(b) != 0) { // only count the ones with non-zero count...
        // System.out.format("Reread; bond_hash[%s] = %d, int_hash[%s] = %d\n", b, b.hashCode(), this.bondSummary.get(b), this.bondSummary.get(b).hashCode());
        h = h ^ (b.hashCode() * this.bondSummary.get(b).hashCode());
      }
    return h;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BRO))
      return false;
    BRO b = (BRO)o;
    return mapEqualsModuloZeros(this.bondSummary, b.bondSummary) || mapEqualsModuloZeros(this.bondSummary, reverse(b.bondSummary));
  }

  private HashMap<Bond, Integer> reverse(HashMap<Bond, Integer> bondSummary) {
    HashMap<Bond, Integer> reversed = new HashMap<Bond, Integer>();
    for (Bond b : bondSummary.keySet())
    reversed.put(b, -bondSummary.get(b));
    return reversed;
  }

  public int ID() {
    return this.hashCode();
  }

  private boolean mapEqualsModuloZeros(HashMap<Bond, Integer> m1, HashMap<Bond, Integer> m2) {
    for (Bond b : m1.keySet()) {
      // it does not matter, for the bonds that have zero count, so don't be strict on them...
      if (m1.get(b) == 0)
        continue;
      if (!m2.containsKey(b) || !m2.get(b).equals(m1.get(b)))
        return false;
    }
    // for the other direction it is sufficient to check that each key there was mapped;
    // and if yes then we would have already compared their integer values...
    for (Bond b : m2.keySet())
      if (!m1.containsKey(b) && m2.get(b) != 0) // and if the count is 0 then it is excusable, so we will not return false then...
        return false;
    return true;
  }
}
