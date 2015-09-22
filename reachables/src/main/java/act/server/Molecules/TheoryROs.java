package act.server.Molecules;

public class TheoryROs {
  private BRO bro;
  private CRO cro;
  private ERO ero;

  public TheoryROs() {} // for serialization...

  public TheoryROs(BRO b, CRO c, ERO e) {
    this.bro = b;
    this.cro = c;
    this.ero = e;
  }

  public BRO BRO() { return this.bro; }
  public CRO CRO() { return this.cro; }
  public ERO ERO() { return this.ero; }

  public int ID() {
    return this.hashCode();
  }

  @Override
  public int hashCode() {
    return this.bro.hashCode() ^ this.cro.hashCode() ^ this.ero.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TheoryROs))
      return false;
    TheoryROs t = (TheoryROs)o;
    return this.bro.equals(t.bro) && this.cro.equals(t.cro) && this.ero.equals(t.ero);
  }

  @Override
  public String toString() {
    return "BRO: " + this.bro + "\n" +
        "CRO: " + this.cro + "\n"  +
        "ERO: " + this.ero + "\n" ;
  }

  public static TheoryROs deserialize(String troS) {
    int broEnd, broStart;
    broStart = troS.indexOf("<bro>") + 5;
    broEnd = troS.indexOf("</bro>", broStart);
    BRO b = BRO.deserialize(troS.subSequence(broStart, broEnd).toString());

    int croEnd, croStart;
    croStart = troS.indexOf("<cro>") + 5;
    croEnd = troS.indexOf("</cro>", croStart);
    CRO c = CRO.deserialize(troS.subSequence(croStart, croEnd).toString());

    int eroEnd, eroStart;
    eroStart = troS.indexOf("<ero>") + 5;
    eroEnd = troS.indexOf("</ero>", eroStart);
    ERO e = ERO.deserialize(troS.subSequence(eroStart, eroEnd).toString());

    return new TheoryROs(b, c, e);
  }
}
