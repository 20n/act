package act.server.Search;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import act.server.Logger;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;

public class FindTopPaths {
  static private MongoDB db = new MongoDB();
  static private Set<Long> nativeIDs = db.getNativeIDs();
  static private PathBFS bfs = new PathBFS(db, nativeIDs);

  public static List<ReactionsHyperpath> getKPaths(ReactionsHypergraph g, Long target, int k) {
    HypergraphEnumerator enumerator = new HypergraphEnumerator();
    enumerator.setNumPathsLimit(k);

    List<ReactionsHyperpath> results = new ArrayList<ReactionsHyperpath>();
    enumerator.enumerate(g, target, results, new HashSet<Long>());
    return results;
  }

  private static double expNormalize(int x) {
    return Math.exp(-1.0/(x/10.0 + 1));
  }

  public static List<ReactionsHyperpath> findShortBalancedPaths(ReactionsHypergraph g, Long target, int k,
                                                                Map<ReactionsHyperpath, Counter<String>> scores) {
    List<ReactionsHyperpath> results = getKPaths(g, target, k);
    System.out.println("findShortBalancedPaths num paths found " + results.size());
    SetBuckets<Integer, ReactionsHyperpath> bucketSort =
        new SetBuckets<Integer, ReactionsHyperpath>();
    DistanceRanker dr = new DistanceRanker(db, g);
    BruteForceRank bfr = new BruteForceRank(db, g);
    EnergyRanker er = new EnergyRanker(db, g);
    int minCost = 100000;
    int maxCost = 0;
    int cnt = 0;
    for (ReactionsHyperpath path : results) {
      int balance = bfr.rankPath(path);
      int distance = dr.rankPath(path);
      int energyFluctuation = er.rankPath(path)/10;
      scores.put(path, new Counter<String>());
      scores.get(path).put("balance", balance);
      scores.get(path).put("distance", distance);
      scores.get(path).put("energy", energyFluctuation);

      Integer cost = balance +
          distance +
          energyFluctuation;

      bucketSort.put(cost, path);
      if (minCost > cost) minCost = cost;
      if (maxCost < cost) maxCost = cost;
      cnt++;
    }
    int i = 0;
    System.out.println(minCost);
    System.out.println(maxCost);
    List<ReactionsHyperpath> sortedPaths = new ArrayList<ReactionsHyperpath>();
    for (int cost = minCost; cost <= maxCost; cost++) {
      Set<ReactionsHyperpath> paths = bucketSort.get(cost);
      if (paths == null) continue;
      for (ReactionsHyperpath p : paths) {
        System.out.println("Rank " + i + " Cost " + cost);
        //System.out.println(p);
        sortedPaths.add(p);
      }
      i++;
    }
    System.out.println("sorted paths" + sortedPaths.size() + " " + cnt);
    return sortedPaths;
  }

  public static void outputPaths(List<ReactionsHyperpath> sortedPaths,
                                 Map<ReactionsHyperpath, Counter<String>> scores, String dirname) {
    new File(dirname).mkdir();
    PrintWriter scoreCSV;
    try {
      scoreCSV = new PrintWriter(dirname + "/scores.csv");
      scoreCSV.println("Path#,balance,distance,energy");
    } catch (FileNotFoundException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
      return;
    }
    int i = 0;
    for (ReactionsHyperpath p : sortedPaths) {
      System.out.println("output path");
      String dotFile = dirname + "/findtoppaths_" + i + ".dot";
      String svgFile = dirname + "/findtoppaths_" + i + ".svg";
      Runtime run = Runtime.getRuntime();
      try {
        p.setIdTypeDB_ID();
        p.writeDOT(dotFile, db, true);
        Process pr = run.exec("/usr/local/bin/dot -Tsvg " + dotFile + " -o " + svgFile);
        pr.waitFor();
        File f = new File(dotFile);
        f.delete();
        scoreCSV.println(i + "," + scores.get(p).get("balance") + "," +
            scores.get(p).get("distance") + "," +
            scores.get(p).get("energy"));
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return;
      }
      i++;
    }
    scoreCSV.close();
  }

  private static Set<Long> getDrugbankSigmaChemicalIds(MongoDB db) {
    List<Chemical> chemicals = db.getDrugbankChemicals();
    chemicals.addAll(db.getSigmaChemicals());
    Set<Long> ids = new HashSet<Long>();
    for (Chemical c : chemicals) {
      ids.add(c.getUuid());
    }
    return ids;
  }

  public static void main(String args[]) {
    int numPaths = 10;
    Logger.setMaxImpToShow(-1);
    MongoDB db = new MongoDB();

    // get targets
    Map<Long, String> idToTargetName = new HashMap<Long, String>();
    Set<Long> interesting = parseTargets(args, db, idToTargetName);

    //TODO: Configure what confidence metrics to use as hard constraints
    //ConfidenceMetric.hardConstraints.add("balance");
    //ConfidenceMetric.hardConstraints.add("reversibility");
    //ConfidenceMetric.hardConstraints.add("ERO");
    //ConfidenceMetric.hardConstraints.add("expression");


    //TODO: Configure starting set
    Set<Long> natives = new HashSet<Long>();
    natives.addAll(InitialSetGenerator.natives(db));
    //natives = InitialSetGenerator.getChemicalIDsOfKeggChemicalsFromFile("../Installer/data/keggEco01100.xml", db);
    //natives = InitialSetGenerator.ecoli(db);

    Set<Long> restrictedSet = ConfidenceMetric.getLegalReactionIDs(db);
    System.out.println("Number of reactions to be used: " + restrictedSet.size());
    PathBFS loader = new PathBFS(db, natives);
    loader.setRestrictedReactions(restrictedSet);
    loader.setReverse(true);
    loader.initTree();

    ReactionsHypergraph graph = loader.getGraph();
    if (args.length > 1 && args[1].equals("cascades")) {
      outputCascades(db, idToTargetName, interesting, graph);
      return;
    }


    System.out.println("Excluding the following targets in natives");
    for (Long id : interesting) {
      if (natives.contains(id)) {
        System.out.println(id + " " + db.getShortestName(id));
      }
    }
    interesting.removeAll(natives);
    System.out.println("Number of targets after initial set removal " + interesting.size());
      /*try {
        System.out.println("Enter any key to continue.");
      System.in.read();
    } catch (IOException e) {
      e.printStackTrace();
    }*/
    Set<Long> reachedChemicalIDs = graph.getChemicals();

    Map<Long, List<Integer>> idCosts = new HashMap<Long, List<Integer>>();
    Map<Long, List<Set<Long>>> idPaths = new HashMap<Long, List<Set<Long>>>();
    DistanceRanker dr = new DistanceRanker(db, graph, numPaths);
    dr.simplifyGraph();
    dr.rankPathsTo(-1L);
    for (Long id : reachedChemicalIDs) {
      if (natives.contains(id)) continue;
      if (!dr.rankPathsTo(id)) continue;
      List<Integer> cost = dr.getBestCosts(id);
      idCosts.put(id, cost);
      idPaths.put(id, dr.getBestPaths(id));
    }

    System.out.println("num found:" + idCosts.keySet().size());


    int i = 0;
    for (Long id : interesting) {
      if (natives.contains(id)) continue;
      if (!dr.rankPathsTo(id)) continue;
      int cost = dr.getBestCost(id);
      //if (cost == 1000) continue;
      i++;

      String name = db.getShortestName(id);
      if (name != null) {
        name = name.replaceAll(" ", "_");
        name = name.replaceAll("/", "_");
      }
      dr.outputGraph(id, "ranking_" + id + "_" + name + ".dot", 5, 50);
    }
    System.out.println(i + " targets found");

    // The following code tries to do a comparison of results using assignConfidence to modify
    // the confidence of certain reactions
      /*
      try {
      assignConfidence(db);
    } catch (FileNotFoundException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

      loader = new PathBFS(db, natives);
      restrictedSet = ConfidenceMetric.getLegalReactionIDs(db);
      System.out.println("Number of reactions to be used: " + restrictedSet.size());
      loader.setRestrictedReactions(restrictedSet);
      loader.setReverse(true);
      loader.initTree();
      graph = loader.getGraph();
      Set<Long> reachedChemicalIDs2 = graph.getChemicals();

      //reachedChemicalIDs2.removeAll(reachedChemicalIDs);
      //System.out.println("num new chemicals " + reachedChemicalIDs2.size());
      //System.out.println(reachedChemicalIDs2);

      System.out.println("num new chemicals " + reachedChemicalIDs2.size());
      DistanceRanker dr2 = new DistanceRanker(db, graph, numPaths);
    dr2.simplifyGraph();
    dr2.rankPathsTo(-1L);
    int i = 0;
    for (Long id : reachedChemicalIDs2) {
        if (natives.contains(id)) continue;
      if (!dr2.rankPathsTo(id)) continue;
      List<Integer> costs = dr2.getBestCosts(id);
      List<Set<Long>> paths = dr2.getBestPaths(id);
      if (idCosts.containsKey(id) &&
          !idPaths.get(id).containsAll(paths) &&
          !idCosts.get(id).containsAll(costs)) {
        System.out.println(id + " New cost " + costs + " Old cost " + idCosts.get(id));
        i++;
        String name = db.getShortestName(id);
        if (name != null) {
          name = name.replaceAll(" ", "_");
          name = name.replaceAll("/", "_");
        }
        dr2.outputGraph(id, "ranking_" + id + "_" + name + ".dot", "new_", numPaths, 50);
        dr.outputGraph(id, "old_ranking_" + id + "_" + name + ".dot", numPaths, 50);
      }

      }
    System.out.println("Num of changed paths " + i);
    */
  }

  private static void outputCascades(MongoDB db,
                                     Map<Long, String> idToTargetName, Set<Long> interesting,
                                     ReactionsHypergraph loaded) {
    for (Long t : interesting) {
      System.out.println("Target: " + idToTargetName.get(t));
      try {
        ReactionsHypergraph temp = loaded.verifyPath(t);
        if (temp == null) {
          System.out.println("No path to " + t);
          continue;
        }
        temp.setIdTypeDB_ID();
        temp = temp.restrictGraph(t, 5, 45);
        temp.setIdTypeDB_ID();

        String name = idToTargetName.get(t);
        if (name == null) {
          name = db.getShortestName(t);
        }
        if (name != null) {
          name = name.replaceAll(" ", "_");
          name = name.replaceAll("/", "_");
        }
        temp.addChemicalColor(t, "#8888FF");
        System.out.println(name);
        temp.writeDOT("cascades/" + name + ".dot", db, "cascades/chemImages");
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  private static Set<Long> parseTargets(String[] args, MongoDB db,
                                        Map<Long, String> idToTargetNames) {
    Set<Long> interesting = new HashSet<Long>();
    if (args.length == 0)
      interesting = getDrugbankSigmaChemicalIds(db);
    else {
      try{
        BufferedReader br = new BufferedReader(
            new InputStreamReader(new DataInputStream(new FileInputStream(args[0]))));
        String strLine;
        while ((strLine = br.readLine()) != null) {
          String[] splitted = strLine.split("\\s+");
          String id = splitted[0];
          try {
            interesting.add(Long.parseLong(id));
          } catch (NumberFormatException e) {
            id = strLine;
            Long longId = db.getChemicalIDFromName(id);

            if (longId >= 0) {
              if (!interesting.contains(longId))
                idToTargetNames.put(longId, id);
              else
                System.out.println(longId + ": " + id + " same as " + idToTargetNames.get(longId) + "?");
              interesting.add(longId);
            } else {
              longId = db.getChemicalIDFromName(id.toLowerCase());
              if (longId >= 0) {
                if (!interesting.contains(longId))
                  idToTargetNames.put(longId, id);
                else
                  System.out.println(longId + ": " + id + " same as " + idToTargetNames.get(longId) + "?");
                interesting.add(longId);
              } else
                System.out.println("Cannot find " + id);
            }
          }
        }
        br.close();
      } catch (Exception e){
        e.printStackTrace();
      }
    }
    System.out.println("Number of targets " + interesting.size());
    return interesting;
  }

  /*
   * The methods below were copied from Installer/wetlab
   * They should be removed.
   */

  public static Set<Reaction> getReactions(MongoDB db,
                                           P<String, String> orgEcnum) {
    Long orgId = db.getOrganismId(orgEcnum.fst());
    Map<String, Object> query = new HashMap();
    query.put("ecnum", orgEcnum.snd());
    if (orgId == null || orgId < 0) {
      query.put("easy_desc", Pattern.compile("^.*" + orgEcnum.fst() + ".*$"));
    } else {
      query.put("organisms.id", orgId);
    }

    Set<Reaction> reactions = db.getReactionsConstrained(query);
    return reactions;
  }

  //TODO: Modify this method to configure confidence scoring for specific reactions
  // For example, the commented out code does the following:
  // For the purpose of seeing how wetlab impacts paths,
  // assign full confidence if wetlab experiments say expression is true, database has no data, and reaction is balanced

  public static void assignConfidence(MongoDB db) throws FileNotFoundException, IOException {
    /*
    Map<P<String, String>, Boolean> cutoffSet = WetlabDataImpact.parse("../Installer/data/wetlab/expression.csv", 0);
    for (P<String, String> orgEcnum : cutoffSet.keySet()) {
      String org = orgEcnum.fst();
        Long nativeOrgID = db.getOrganismId(org);
        if (nativeOrgID == -1) {
          String[] temp = org.split("\\s");
          org = temp[0] + " " + temp[1];
        }
        nativeOrgID = db.getOrganismId(org);
        if (nativeOrgID == 562L) continue;
      Set<Reaction> reactions = getReactions(db, orgEcnum);

      for (Reaction reaction : reactions) {
        if (cutoffSet.get(orgEcnum) && ConfidenceMetric.expressionScore(reaction) < 1 && ConfidenceMetric.isBalanced(reaction)) {
            ConfidenceMetric.setInconfidence(new Long(reaction.getUUID()), 0);
        }
      }
    }
    */
  }
}
