package act.server.Molecules;

public class CRO extends RO {

  public CRO() {}

  public CRO(RxnWithWildCards ro) {
    super(ro);
  }

  public void render() {
    render("cro.png", "CRO");
  }

  public static CRO deserialize(String s) {
    return new CRO((RxnWithWildCards)getXStream().fromXML(s));
  }

  public CRO reverse() {
    return new CRO(new RxnWithWildCards(RxnWithWildCards.reverse(this.ro.rxn_with_concretes)));
  }
}
