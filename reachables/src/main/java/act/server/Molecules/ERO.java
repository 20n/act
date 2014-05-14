package act.server.Molecules;

public class ERO extends RO {
	private Double reversibility;
	
	public ERO() {}

	public ERO(RxnWithWildCards ro) {
		super(ro);
	}
	
	public void render() {
		render("ero.png", "ERO");
	}
	
	public static ERO deserialize(String s) {
		return new ERO((RxnWithWildCards)getXStream().fromXML(s));
	}

	public ERO reverse() {
		return new ERO(new RxnWithWildCards(RxnWithWildCards.reverse(this.ro.rxn_with_concretes)));
	}

	public Double getReversibility() {
		return reversibility;
	}

	public void setReversibility(Double reversibility) {
		this.reversibility = reversibility;
	}
}
