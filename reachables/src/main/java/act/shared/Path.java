package act.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.shared.helpers.T;

/**
 * This is the class the backend can use to pass pathways to the front end.
 * Currently, what is essential is the compoundList, the list of chemicals.
 * No other information about the path is guaranteed to exist right now.
 *
 */

public class Path implements Serializable {
  private static final long serialVersionUID = 42L;
  protected List<Chemical> compoundList;
  protected List<T<Long, ReactionType, Double>> edgeList;

  /**
   * Reactants required at each step,
   * and the products produced at each step.
   * Excludes what's already in compoundList
   */
  private List<Set<Chemical>> reactants, products;

  /**
   * The rxnsList has limited expressiveness:
   *  1) Multiple reactions exist that can take compound A to B
   *  2) The reaction may have been abstract (ie CRO).
   * So it's only useful now as an example of a concrete path.
   */
  private List<Long> reactionIDList;
  private List<Reaction> reactionList;
    public Integer pathID;


  public Path(List<Chemical> compounds, List<Long> operators) {
    compoundList = compounds;
        pathID = this.hashCode();
  }

  public Path() {
    // Dummy
    compoundList = new ArrayList<Chemical>();
    for (int i = 0; i < 6; i++){
      compoundList.add(new Chemical(0, 0L, "", ""));
    }
        pathID = this.hashCode();
  }

  public Path(List<Chemical> compounds) {
    this(compounds, null);
  }
  public List<Chemical> getCompoundList() {
    return compoundList;
  }
  public List<Long> getReactionIDList() {
    return reactionIDList;
  }

  public void setReactionIDList(List<Long> rxns) {
    reactionIDList = rxns;
  }

  public void setPathID(int id) { pathID = id; }


  @Override
  public int hashCode() {
    int hash = 0;
    for(Chemical c : compoundList) {
      hash = (int) (hash ^ c.getUuid());
    }
    if(reactionIDList!=null) {
      for(Long rxn : reactionIDList) {
        hash = (int) (hash ^ rxn.intValue());
      }
    }
    return hash;
  }

  public List<Set<Chemical>> getReactants() {
    if (reactants == null) {
      reactants = new ArrayList<Set<Chemical>>();
      for (int i = 0; i < compoundList.size() - 1; i++)
        reactants.add(new HashSet<Chemical>());
    }
    return reactants;
  }

  public void setReactants(List<Set<Chemical>> reactants) {
    this.reactants = reactants;
  }

  public List<Set<Chemical>> getProducts() {
    if (products == null) {
      products = new ArrayList<Set<Chemical>>();
      for (int i = 0; i < compoundList.size() - 1; i++)
        products.add(new HashSet<Chemical>());

    }
    return products;
  }

  public void setProducts(List<Set<Chemical>> products) {
    this.products = products;
  }

  public void setEdgeList(List<T<Long, ReactionType, Double>> edges) {
    this.edgeList = edges;
  }

  public List<T<Long, ReactionType, Double>> getEdgeList() {
    return edgeList;
  }

  public void setReactionList(List<Reaction> reactions) {
    reactionList = reactions;
  }

  public List<Reaction> getReactionList() {
    return reactionList;
  }

  public void reverse() {
    Collections.reverse(compoundList);
    if (reactants!=null)
      Collections.reverse(reactants);
    if (products!=null)
      Collections.reverse(products);

    List<Set<Chemical>> temp = reactants;
    reactants = products;
    products = temp;

    if (edgeList != null)
      Collections.reverse(edgeList);
    if (reactionIDList!=null)
      Collections.reverse(reactionIDList);
    if (reactionList!=null)
      Collections.reverse(reactionList);

    this.pathID = this.hashCode();
  }

  public static Path concat(Path p1, Path p2) {
    Path ret = new Path(new ArrayList<Chemical>(p1.getCompoundList()));
    ret.setProducts(new ArrayList<Set<Chemical>>(p1.getProducts()));
    ret.setReactants(new ArrayList<Set<Chemical>>(p1.getReactants()));
    if (p1.edgeList != null)
      ret.setEdgeList(new ArrayList<T<Long, ReactionType, Double>>(p1.edgeList));
    if (p1.reactionList != null)
      ret.setReactionList(p1.reactionList);
    if (p1.reactionIDList != null)
      ret.setReactionIDList(p1.reactionIDList);

    ret.compoundList.addAll(p2.compoundList);
    ret.products.addAll(p2.getProducts());
    ret.reactants.addAll(p2.getReactants());
    if (ret.edgeList != null && p2.edgeList != null)
      ret.edgeList.addAll(p2.edgeList);
    if (ret.reactionList != null && p2.reactionList != null)
      ret.reactionList.addAll(p2.reactionList);
    if (ret.reactionIDList != null && p2.reactionIDList != null)
      ret.reactionIDList.addAll(p2.reactionIDList);
    ret.pathID = ret.hashCode();
    return ret;
  }

}
