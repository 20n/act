package act.shared;

import java.io.Serializable;

public class ReactionWithAnalytics extends Reaction implements Serializable {
  private static final long serialVersionUID = 42L;
  ReactionWithAnalytics() { /* default constructor for serialization */ }

  Float[] substrateRarity, productRarity;
  public Float[] getSubstrateRarityPDF() { return this.substrateRarity; }
  public Float[] getProductRarityPDF() { return this.productRarity; }

  public ReactionWithAnalytics(Reaction r, Float[] substrRarity, Float[] prodRarity) {
    super(r.getUUID(), r.getSubstrates(), r.getProducts(), r.getECNum(), r.getReactionName());
    this.substrateRarity = substrRarity;
    this.productRarity = prodRarity;
  }
}
