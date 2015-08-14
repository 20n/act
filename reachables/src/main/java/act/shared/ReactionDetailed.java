package act.shared;

import java.io.Serializable;

public class ReactionDetailed extends Reaction implements Serializable {
	private static final long serialVersionUID = 42L;
	ReactionDetailed() { /* default constructor for serialization */ }

	public String[] getSubstrateURLs() { return this.substrateURLs; }
	public String[] getProductURLs() { return this.productURLs; }
	public String[] getSubstrateImages() { return this.substrateImages; }
	public String[] getProductImages() { return this.productImages; }
	public void setSubstrateImages(String[] sI) { this.substrateImages = sI; }
	public void setProductImages(String[] pI) { this.productImages = pI; }
	
	String[] substrateURLs, productURLs, substrateImages, productImages;
	Chemical[] substrateChems, productChems;
	public ReactionDetailed(Reaction r, Chemical[] sC, String[] sU, Chemical[] pC, String[] pU) {
		super(r.getUUID(), r.getSubstrates(), r.getProducts(), r.getECNum(), r.getReactionName());
		
		this.substrateChems = sC; // new Chemical[substrates.length];
		this.productChems = pC; // new Chemical[products.length];
		this.substrateURLs = sU; // new String[substrates.length];
		this.productURLs = pU; // new String[products.length];
	}
}
