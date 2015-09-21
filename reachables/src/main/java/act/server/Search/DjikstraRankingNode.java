package act.server.Search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DjikstraRankingNode implements Comparable<DjikstraRankingNode>{
  protected Long id;
  protected List<DjikstraPath> paths = new ArrayList<DjikstraPath>();
  protected List<DjikstraPath> topPaths = new ArrayList<DjikstraPath>();
  private Set<DjikstraPath> seen = new HashSet<DjikstraPath>();

  public Long getID() {
    return id;
  }

  public void addTopPath(DjikstraPath path) {
    topPaths.add(path);
  }

  public List<DjikstraPath> getTopPaths() {
    return topPaths;
  }

  public int getCost(int index) {
    return paths.get(index).getCost();
  }

  public void insertPath(DjikstraPath path) {
    if (addPath(path))
      Collections.sort(paths);
  }

  public boolean addPath(DjikstraPath path) {
    if (seen.contains(path)) return false;
    if (path.getChemicalsBeforeLastReaction() != null &&
      path.getChemicalsBeforeLastReaction().contains(this.id))
      return false;
    paths.add(path);
    seen.add(path);
    return true;
  }

  public void sortPaths() {
    Collections.sort(paths);
  }

  public DjikstraPath removePath(int index) {
    return paths.remove(index);
  }


  public List<DjikstraPath> getPaths() {
    return paths;
  }

  @Override
  public int compareTo(DjikstraRankingNode o) {
    DjikstraRankingNode other = o;
    if (paths.size() == 0) System.out.println("NO PATHS!!" + topPaths.size() + " " + getID());

    if (other.paths.size() == 0) System.out.println("NO PATHS!!" + other.topPaths.size() + " " + other.getID());
    Integer myMinCost = this.paths.get(0).getCost();
    Integer otherMinCost = other.paths.get(0).getCost();
    if (myMinCost < otherMinCost)
      return -1;
    else if (myMinCost == otherMinCost)
      return 0;
    else return 1;
  }

}
