package act.server.Search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;

public abstract class DjikstraHypergraphRanker<N extends DjikstraRankingNode> {
  protected ReactionsHypergraph graph;

  protected int k = 10;

  protected MongoDB db;
  protected Map<Long, N> nodes;

  abstract protected N createNode(Long id, Integer numParents);
  abstract protected void outputGraph(Long target, String outputFile, int thresh, int limit);
  abstract public int rankPath(ReactionsHyperpath path);

  abstract protected List<DjikstraPath> getParents(Long reactionID, Long reactantExpanding);
  abstract protected int getReactionCost(Long reactionID);
  /**
   * Finds path to the reactants. Add the reaction to the paths.
   * @param reactionID
   * @return
   */
  private List<DjikstraPath> getExtendedPaths(Long reactionID, Long reactantExpanding) {
    List<DjikstraPath> paths = getParents(reactionID, reactantExpanding);
    /*List<DjikstraPath> newPaths = new ArrayList<DjikstraPath>();
    int cost = getReactionCost(reactionID);
    for (DjikstraPath path : paths) {
      Set<Long> temp = new HashSet<Long>(path.getReactions());
      newPaths.add(new DjikstraPath(path.getCost() + cost, temp));
    }*/
    return paths;
  }

  private PriorityQueue<N> toExpand;
  private Map<Long, N> expanded;
  public boolean rankPathsTo(Long target) {
    System.out.println("Target is " + target);
    //ReactionsHypergraph graph = this.graph.restrictGraph(target, -1, -1);
    System.out.println("# chemicals: " + graph.getNumChemicals() +
        " -- # reactions: " + graph.getNumReactions());
    graph.setIdTypeDB_ID();
    Set<Long> startingChemicalIDs = this.graph.getInitialSet();

    /*
     * Queue sorted by number of min cost to each node
     */
    if (toExpand == null) {
      toExpand = new PriorityQueue<N>();

      for (Long id : startingChemicalIDs) {
        nodes.put(id, createNode(id, 0));
        N node = nodes.get(id);
        if (node.getPaths().size() == 0)
          node.insertPath(new DjikstraPath(0, new HashSet<Long>()));
        toExpand.add(node);
      }
      expanded = new HashMap<Long, N>();
    }
    System.err.println("starting Djikstra ranking");

    boolean done = false;
    if (expanded.containsKey(target)) {
      if (expanded.get(target).topPaths.size() >= k)
        done = true;
    }
    while (!toExpand.isEmpty() && !done) {
      N node = toExpand.poll();

      //if (expanded.containsKey(node.getID())) continue;
      // add new chemicals to toExpand
      Set<Long> reactionIDs = graph.getReactionsFrom(node.getID());
      expanded.put(node.getID(), node);
      if (node.getID() == target) reactionIDs = null;
      if (reactionIDs == null) reactionIDs = new HashSet<Long>();

      reactionIDs = graph.filterReactions(reactionIDs, expanded.keySet(), target);
      for (Long rid : reactionIDs) {
        List<DjikstraPath> paths = getExtendedPaths(rid, node.getID());
        Set<Long> childrenID = new HashSet<Long>(graph.getProducts(rid));
        // get products of reaction. if in expanded, ignore.
        // if not in nodes yet, add them.
        for (Long childID : childrenID) {
          boolean updated = false, reinsert = false;
          /*if (startingChemicalIDs.contains(childID)) {
            continue;
          }*/

          if (!nodes.containsKey(childID)) {
            int numParents = graph.getReactionsTo(childID).size();
            nodes.put(childID, createNode(childID, numParents));
          }

          //Update cost and position on priority queue
          N child = nodes.get(childID);
          if (toExpand.contains(child)) {
            toExpand.remove(child);
            reinsert = true;
          }

          int i = 0;
          while (child.getPaths().size() + child.getTopPaths().size() < k &&
              i < paths.size()) {
            if (child.addPath(paths.get(i)))
              updated = true;
            i++;
          }
          child.sortPaths();
          int j = child.getPaths().size() - 1;
          for (; i < paths.size() && i < k && j >= 0; i++) {
            DjikstraPath path = paths.get(i);
            if (path.getCost() < child.getCost(j)) {
              if (child.addPath(path))
                updated = true;
            }
            j--;
          }
          child.sortPaths();
          while (child.getPaths().size() + child.getTopPaths().size() > k) {
            child.removePath(child.getPaths().size() - 1);
          }

          if (reinsert) {
            toExpand.add(child);
          }

          if (!updated || reinsert) continue;
          if (child.getPaths().size() > 0 && child != node) {
            toExpand.add(child);
          }

        }
      }

      node.addTopPath(node.removePath(0));
      // put new nodes in toExpand that haven't been expanded yet
      if (node.getPaths().size() > 0) {
        toExpand.add(node);
      }
      if (node.getID() == target && node.getTopPaths().size() >= k) done = true;
    }
    System.out.println("Targets found: " + nodes.size());
    if (nodes.get(target) != null) {
      return true;
    } else {
      return false;
    }
  }

}
