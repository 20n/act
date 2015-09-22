package act.server.Search;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.server.VariousSearchers;
import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;

/**
 * Currently tracks NADH usage in paths
 *
 */
public class BruteForceRank extends AbstractHypergraphRanker <ImbalanceRankingNode, Set>{

  public BruteForceRank(MongoDB db, ReactionsHypergraph g) {
    this.db = db;
    this.nodes = new HashMap<Long, ImbalanceRankingNode>();
    RankingMetrics.init(db);
    this.graph = g;

  }

  private Set<Integer> possibleImbalances(Long[] reactantIDs, int reactantNum) {
    Set<Integer> result = new HashSet<Integer>();
    if (reactantNum == reactantIDs.length) { // base case
      result.add(0);
      return result;
    }
    ImbalanceRankingNode node = nodes.get(reactantIDs[reactantNum]);
    if (node == null) return result;
    if (node.imbalances == null || node.imbalances.isEmpty()) return result;
    Set<Integer> otherImbalances = possibleImbalances(reactantIDs, reactantNum + 1);
    if (otherImbalances == null) return result;
    for (Integer imbalance : node.imbalances) {
      for (Integer temp : otherImbalances) {
        result.add(absMax(imbalance, temp));
      }
    }
    return result;
  }

  private int absMax(int a, int b) { return Math.abs(a) > Math.abs(b) ? a : b; }

  protected Set<Integer> getCost(Long reactionID) {
    Set<Integer> result = new HashSet<Integer>();
    Reaction reaction = db.getReactionFromUUID(reactionID);
    Long[] reactants = reaction.getSubstrates();
    Set<Integer> imbalances = possibleImbalances(reactants, 0);
    Counter<Long> metrics = RankingMetrics.getCofactorUsage(reactionID, false);
    int cofactorUsage = metrics.get(7037L); //NADH
    for (Integer imbalance : imbalances) {
      result.add(cofactorUsage + imbalance);
      //result.add(max(cofactorUsage, imbalance));
    }

    return result;

  }

  protected Set<Integer> join(List<Set<Integer>> imbalances) {
    Set<Integer> result = new HashSet<Integer>();
    for (Set<Integer> imbalance : imbalances) {
      result.addAll(imbalance);
    }

    Set<Integer> widened = new HashSet<Integer>();
    // widening? or limiting domain
    for (Integer imbalance : result) {
      if (imbalance > 10) {
        imbalance = 10;
      } else if (imbalance < -10) {
        imbalance = -10;
      }

      widened.add(imbalance);
    }
    return widened;
  }

  @Override
  protected ImbalanceRankingNode createNode(Long id, Integer numParents) {
    ImbalanceRankingNode node = new ImbalanceRankingNode(id, numParents);
    if (numParents == 0) {
      node.imbalances.add(0);
    }
    return node;
  }

  public static void main(String[] args) {
    MongoDB db = new MongoDB();
      PathBFS pathFinder = new PathBFS(db, InitialSetGenerator.natives(db));
    BruteForceRank bfr = new BruteForceRank(db, pathFinder.getGraph());
    bfr.rankPathsTo(4271L);
  }

  @Override
  public void outputGraph(Long target, String outputFile, int thresh, int limit) {
    ReactionsHypergraph graph = this.graph.restrictGraph(target, thresh, limit);
    graph.setIdTypeDB_ID();

    for (Long id : nodes.keySet()) {
      ImbalanceRankingNode node = nodes.get(id);
      //System.out.println(id + " " + node.toString());
      graph.addChemicalInfo(id, node.toString());
      int bestImbalance = 100;
      for (int imbalance : node.getCosts()) {
        if (Math.abs(imbalance) < Math.abs(bestImbalance)) {
          bestImbalance = imbalance;
        }
      }
      String red = Integer.toHexString(50 + bestImbalance * 10);
      String green = Integer.toHexString(50 + 200 - bestImbalance * 10);
      graph.addChemicalColor(id, "#" + red + green + "00");
    }

    graph.addChemicalColor(target, "#0000FF");

    try {
      graph.writeDOT(outputFile, db);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public int rankPath(ReactionsHyperpath path) {
    List<Long> reactions = path.getOrdering();
    Set<Long> natives = path.getInitialSet();

    Counter<Long> imbalance = new Counter<Long>();
    for (Long reaction : reactions) {
      Set<Long> reactants = path.getReactants(reaction);
      for (Long r : reactants) {
        if (natives.contains(r))
          imbalance.dec(r);
      }
      Set<Long> products = path.getProducts(reaction);
      for (Long p : products) {
        if (natives.contains(p))
          imbalance.inc(p);
      }
    }
    int total = 0;
    for (Long id : imbalance.keySet()) {
      total += Math.abs(imbalance.get(id));
    }
    return total;
  }
}

class ImbalanceRankingNode extends RankingNode{
  Set<Integer> imbalances;

  ImbalanceRankingNode(Long id, int numUnexpandedParents) {
    this.id = id;
    this.imbalances = new HashSet<Integer>();
    this.numUnexpandedParents = numUnexpandedParents;
  }

  @Override
  public String toString() {
    String result;
    result = "id = " + id;
    result += " imbalances: ";
    for (Integer imbalance : imbalances) {
      result += imbalance + ", ";
    }
    result += "";
    return result;
  }

  @Override
  public Set<Integer> getCosts() {
    return imbalances;
  }

  @Override
  public void setCosts(Set<Integer> costs) {
    imbalances = costs;
  }
}
