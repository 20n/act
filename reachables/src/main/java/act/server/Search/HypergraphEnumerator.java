package act.server.Search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import act.server.SQLInterface.MongoDB;

public class HypergraphEnumerator<N, E> {
  private int numPathsLimit = 100;

  //DEBUG tools
  static private MongoDB db;
  static private boolean DEBUG = false;
  static private boolean TIMING = true;

  static private String DEBUG_DIR = "debug";

  public void setNumPathsLimit(int limit) {
    numPathsLimit = limit;
  }

  private Map<N, E> generateBackPointers(ReactionsHypergraph<N, E> g,
      N target, Set<E> ignore) {
    Set<N> obtained = new HashSet<N>();
    Set<E> applied = new HashSet<E>();
    Map<N, E> result = new HashMap<N, E>();
    Set<N> initialSet = g.getInitialSet();
    obtained.addAll(initialSet);
    Set<N> newNodes = initialSet;
    int i = 0;
    while (!newNodes.isEmpty()) {
      Set<E> edges = new HashSet<E>();
      for (N n : newNodes)
        edges.addAll(g.filterReactions(g.getReactionsFrom(n), obtained));
      newNodes = new HashSet<N>();
      for(E e : edges) {
        if (ignore.contains(e)) continue;
        if (applied.contains(e)) continue;
        applied.add(e);
        for (N p : g.getProducts(e)) {
          if (!obtained.contains(p)) {
            obtained.add(p);
            result.put(p, e);
            newNodes.add(p);
          }
        }
      }
      i++;
    }
    System.out.println("depth of paths: " + i);
    if (obtained.contains(target))
      return result;
    return null;
  }

  /**
   * Stores a found path in result.
   *
   * Reall, a forward expansion to get backward pointers followed by bactracing.
   *
   * @param g
   * @param target
   * @param result
   * @return
   */
  public boolean backwardsSearch(ReactionsHypergraph<N, E> g, N target,
      ReactionsHypergraph<N, E> result, Set<E> ignore) {
    Map<N, E> backPointers = generateBackPointers(g, target, ignore);
    Set<N> initialSet = g.getInitialSet();
    System.out.println(g.getNumChemicals());
    if (backPointers == null) return false;

    Set<N> obtained = new HashSet<N>();
    Set<N> required = new HashSet<N>();
    required.add(target);

    obtained.addAll(initialSet);
    while (!required.isEmpty()) {
      Set<N> newRequired = new HashSet<N>();
      for (N r : required) {
        if (obtained.contains(r)) continue;
        E chosen = backPointers.get(r);
        if (chosen == null) {
          System.err.println("no edge to " + r);
          return false;
        }
        result.addReaction(chosen, g.getReactants(chosen), g.getProducts(chosen));
        /*
         * Can only add requirement we want to satisfy because
         * we don't want to obtain something not via its back pointer.
         * That can lead to cycles.
        */
        obtained.add(r);
        newRequired.addAll(g.getReactants(chosen));
      }
      required = newRequired;
    }
    return true;
  }

  public void enumerate(ReactionsHypergraph<N, E> g, N target, List<ReactionsHyperpath<N, E>> results,
      Set<E> ignore) {
    enumerate(g, target, results, ignore, new HashSet<E>());
  }

  public void enumerate(ReactionsHypergraph<N, E> g, N target, List<ReactionsHyperpath<N, E>> results,
      Set<E> ignore, Set<E> doNotIgnore) {
    if (results.size() > numPathsLimit) return;

    System.out.println("depth " + ignore.size());
    ReactionsHypergraph<N, E> backwardsResult = new ReactionsHypergraph<N, E>();
    backwardsResult.setInitialSet(g.getInitialSet());
    ReactionsHyperpath<N, E> verifiedPath = null;
    long starttime = System.currentTimeMillis();
    boolean found = backwardsSearch(g, target, backwardsResult, ignore);
    if (TIMING)
      System.out.println("TIMING: bsearch in " + (System.currentTimeMillis() - starttime));
    if (!found) {
      return;
    }

    starttime = System.currentTimeMillis();
    verifiedPath = backwardsResult.verifyPath(target);
    if (TIMING)
      System.out.println("TIMING: verify in " + (System.currentTimeMillis() - starttime));

    if (verifiedPath == null) {
      System.err.println("error in path found by backwardsSearch");
      try {
        if (db != null)
          backwardsResult.writeDOT(DEBUG_DIR + "/enumerator_error.dot", db, DEBUG_DIR);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    System.out.println("DEBUG: num reactions of path: " + verifiedPath.getNumReactions());
    if (verifiedPath.getNumReactions() > 20)
      return;
    results.add(verifiedPath);

    List<E> edges = verifiedPath.getOrdering();
    Set<E> newIgnores = new HashSet<E>();
    for (E edge : edges) {
      if (doNotIgnore.contains(edge)) continue;
      ignore.add(edge);
      enumerate(g, target, results, ignore, doNotIgnore);
      ignore.remove(edge);
      doNotIgnore.add(edge);
      newIgnores.add(edge);
    }
    for (E edge : newIgnores) {
      doNotIgnore.remove(edge);
    }
    System.out.println("Num paths found so far: " + results.size());
  }

  /**
   * Rough quick test
   * @param args
   */
  public static void main(String[] args) {
    MongoDB db = new MongoDB();
    Long target = 4271L;
    Set<Long> ignore = new HashSet<Long>();
    if (args.length > 0) {
      try {
        target = Long.parseLong(args[0]);
      } catch (Exception e) {
        Long id = db.getChemicalIDFromName(args[0]);
        if (id < 0) {
          System.out.println("cannot find chemical");
          return;
        }
        target = id;
      }
    }
    if (args.length > 1) {
      List<String> ignoreList = Arrays.asList(args[1].split(","));
      for (String s : ignoreList)
        ignore.add(Long.parseLong(s));
    }

    Set<Long> startingChemicalIDs = db.getNativeIDs();
    PathBFS bfs = new PathBFS(db, startingChemicalIDs);
    bfs.initTree();
    Set<Long> initialSet = new HashSet<Long>();
    for(Long c : startingChemicalIDs) {
      initialSet.add(c);
    }
    HypergraphEnumerator<Long, Long> enumerator = new HypergraphEnumerator<Long, Long>();
    HypergraphEnumerator.db = db;
    ReactionsHypergraph<Long, Long> g = bfs.getGraphTo(target, 10000, 10000);

    List<ReactionsHyperpath<Long, Long>> results =
        new ArrayList<ReactionsHyperpath<Long, Long>>();
    enumerator.enumerate(g, target, results, ignore);

    if (DEBUG) {
      for (int pathNum = 0; pathNum < results.size(); pathNum++)
      try {
        ReactionsHyperpath<Long, Long> path = results.get(pathNum);
        String dotFile = DEBUG_DIR + "/enumerator_debug" + pathNum + ".dot";
        String svgFile = DEBUG_DIR + "/enumerator_debug" + pathNum + ".svg";
        path.setIdTypeDB_ID();
        path.writeDOT(dotFile, db, DEBUG_DIR);
        Runtime run = Runtime.getRuntime();
        Process pr = run.exec("/usr/local/bin/dot -Tsvg " + dotFile + " -o " + svgFile);
        try {
          pr.waitFor();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

    }
    System.out.println("Paths found " + results.size());
  }
}
