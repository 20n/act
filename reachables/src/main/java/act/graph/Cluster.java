package act.graph;

import java.util.List;

public interface Cluster<N> {
  public void setDataLayout(Graph<N, Double> distances);
  public List<List<N>> getClusters();

}
