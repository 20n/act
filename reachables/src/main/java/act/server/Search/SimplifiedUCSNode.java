package act.server.Search;

import act.shared.ReactionType;
import act.shared.helpers.T;

public class SimplifiedUCSNode extends UCSNode {
  private Long chemicalID;

  private T<Long,ReactionType,Double> rxn;

  public SimplifiedUCSNode(Long chemicalID, SimplifiedUCSNode parent, double cost) {
    super(parent, cost);
    this.chemicalID = chemicalID;
  }

  public Long getID() { return chemicalID; }

  public void setRxn(T<Long,ReactionType,Double> rxn) { this.rxn = rxn; }
  public T<Long,ReactionType,Double> getRxn() { return this.rxn; }

  @Override
  public int hashCode() {
    return chemicalID.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (! (o instanceof SimplifiedUCSNode)) {
      return false;
    }

    SimplifiedUCSNode node = (SimplifiedUCSNode) o;

    return chemicalID.equals(node.chemicalID);
  }

}
