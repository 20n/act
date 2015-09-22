package act.shared.helpers;

public class P<S1, S2>
{
  S1 fst; S2 snd;
  public S1 fst() { return this.fst; } // set { this.fst = value; } }
  public void SetFst(S1 value) { this.fst = value; }
  public S2 snd() { return this.snd; }  // set { this.snd = value; } }
  public void SetSnd(S2 value) { this.snd = value; }
  public P(S1 fst, S2 snd)
  {
    this.fst = fst;
    this.snd = snd;
  }
  @Override
  public String toString() {
    return "(" + fst + "," + snd + ")";
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof P<?,?>)) return false;
    P<S1, S2> p = (P<S1, S2>)o;
    if ((p.fst == null && this.fst != null) || (p.fst != null && this.fst == null))
      return false;
    if ((p.snd == null && this.snd != null) || (p.snd != null && this.snd == null))
      return false;
    return ((p.fst == null && this.fst == null) || p.fst.equals(this.fst)) &&
        ((p.snd == null && this.snd == null) || p.snd.equals(this.snd));
  }
  @Override
  public int hashCode() {
    if (this.fst == null)
      return this.snd.hashCode();
    if (this.snd == null)
      return this.fst.hashCode();
    return this.fst.hashCode() ^ this.snd.hashCode();
  }
}