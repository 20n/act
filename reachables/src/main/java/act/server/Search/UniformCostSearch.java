package act.server.Search;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Generic uniform cost search.
 * Finds the path with the lowest cost.
 * @param <N>
 */
public abstract class UniformCostSearch<N extends UCSNode> {

  protected abstract N getInitialState();
  protected abstract List<N> getChildren(N node);
  protected abstract boolean isGoal(N node);

  protected N search() {
    PriorityQueue<N> queue = new PriorityQueue<N>(1000,
        new Comparator<N>() {
      @Override
      public int compare(N arg0, N arg1) {
        double diff = arg0.getCost() - arg1.getCost();
        if (diff < 0) return -1;
        if (diff > 0) return 1;
        return 0;
      }

    });
    Set<N> expanded = new HashSet<N>();
    queue.add(getInitialState());

    while (!queue.isEmpty()) {
      N node = queue.poll();
      if (expanded.contains(node)) continue;
      expanded.add(node);
      if (isGoal(node)) return node;
      List<N> children = getChildren(node);
      for (N child : children) {
        if (!expanded.contains(child)) {
          queue.add(child);
        }
      }
    }
    return null;
  }
}
