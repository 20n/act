package act.server.Search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import act.shared.Chemical;
import act.shared.Path;
import act.shared.ReactionType;
import act.shared.SimplifiedReactionNetwork;
import act.shared.helpers.P;
import act.shared.helpers.T;

public class SimplifiedUCS extends UniformCostSearch<SimplifiedUCSNode> {
  private Chemical goal;
  private Set<Long> startingChemicals;
  private SimplifiedReactionNetwork srn;

  private Set<Long> ignore;

  public SimplifiedUCS(SimplifiedReactionNetwork srn, Set<Long> startingChemicals) {
    this.srn = srn;
    this.startingChemicals = startingChemicals;
    this.ignore = new HashSet<Long>();
  }

  public Path getPath(Chemical goal) {
    this.goal = goal;
    SimplifiedUCSNode reached = this.search();
    if (reached == null) return null;
    SimplifiedUCSNode parent = reached;
    Stack<Chemical> chemicalPath = new Stack<Chemical>();
    Stack<T<Long, ReactionType, Double>> edgeList = new Stack();
    Stack<Long> compounds = new Stack<Long>();

    System.out.println("reached" + reached);
    compounds.push(reached.getID());
    chemicalPath.push(srn.getChemical(reached.getID()));
    edgeList.push(reached.getRxn());

    while ((parent = (SimplifiedUCSNode) parent.getParent()) != null) {
      compounds.push(parent.getID());
      chemicalPath.push(srn.getChemical(parent.getID()));
      if (parent.getRxn() != null)
        edgeList.push(parent.getRxn());
    }
    List<List<Long>> paths = new ArrayList<List<Long>>();
    paths.add(compounds);
    return srn.convertCompoundListToPaths(paths).get(0);
    /*Path path = new Path(chemicalPath);
    path.setEdgeList(edgeList);*/
    //return path;
  }

  @Override
  protected SimplifiedUCSNode getInitialState() {
    return new SimplifiedUCSNode(goal.getUuid(), null, 0);
  }

  @Override
  protected List<SimplifiedUCSNode> getChildren(SimplifiedUCSNode node) {
    Set<Long> products = srn.getProducts(node.getID());
    List<SimplifiedUCSNode> children = new ArrayList<SimplifiedUCSNode>();
    for (Long product : products) {
      if (ignore.contains(product)) {
        continue;
      }
      Set<T<Long,ReactionType,Double>> rxns = srn.getOriginalRxns(new P<Long, Long>(node.getID(), product));
      Double bestCost = (double) 10000;
      T<Long,ReactionType,Double> bestRxn = null;
      for (T<Long,ReactionType,Double> rxn : rxns) {
        Double weight = rxn.third();
        Double cost = Math.exp(1-weight);
        if (cost < bestCost) {
          bestCost = cost;
          bestRxn = rxn;
        }
      }
      SimplifiedUCSNode n = new SimplifiedUCSNode(product, node, (double) (node.getCost() + bestCost));
      n.setRxn(bestRxn);
      children.add(n);
    }
    return children;
  }

  @Override
  protected boolean isGoal(SimplifiedUCSNode node) {
    System.out.println("goal? " + node.getID());
    return startingChemicals.contains(node.getID());
  }

  public void setIgnore(Set<Long> ignore) {
    this.ignore = ignore;
  }


  public static void main(String[] args) {
    SimplifiedReactionNetwork srn = new SimplifiedReactionNetwork("onestepro", null);
    List<Chemical> chemicals = srn.getDB().getNativeMetaboliteChems();
    Set<Long> chemicalIds = new HashSet<Long>();
    for (Chemical c : chemicals) {
      chemicalIds.add(c.getUuid());
    }
    SimplifiedUCS ucs = new SimplifiedUCS(srn, chemicalIds);
    Long id = srn.getDB().getChemicalIDFromName("butan-1-ol");
    Chemical chemical = srn.getDB().getChemicalFromChemicalUUID(id);
    Path path = ucs.getPath(chemical);
    System.out.println(path);
    for(Chemical c : path.getCompoundList()) {
      System.out.println(c.getUuid());
    }


  }
}
