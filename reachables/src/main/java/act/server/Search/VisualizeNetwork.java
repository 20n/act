package act.server.Search;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import act.server.SQLInterface.MongoDBPaths;
import act.shared.Chemical;
import act.shared.SimplifiedReactionNetwork;
import act.shared.helpers.P;

public class VisualizeNetwork {
    private static SimplifiedReactionNetwork srn;
    private static MongoDBPaths db;
    private static Set<Long> ignore;

    public static void init() {
      if (db!=null) return;
      // db = new MongoDBPaths();
      db = new MongoDBPaths("pathway.berkeley.edu", 27017, "actv01"); // should get this from command line
      ignore = new HashSet<Long>();
      srn = new SimplifiedReactionNetwork(db,"srn",db.getCofactorChemicals(),true);
      for (Chemical c : db.getCofactorChemicals()) {
        ignore.add(c.getUuid());
      }
    }

    public static Map<Long,Set<Long>> reverseGraph(Map<Long,Set<Long>>  graph) {
      Map<Long,Set<Long>> revGraph = new HashMap<Long,Set<Long>>();
      for(Long k : graph.keySet()) {
        Set<Long> children = graph.get(k);
        for(Long c : children) {
          if(!revGraph.containsKey(c))
            revGraph.put(c, new HashSet<Long>());
          revGraph.get(c).add(k);
        }
      }
      return revGraph;
    }

    /**
     * Graph between a pair
     */
    public static Map<Long,Set<Long>> generateBetweenPair(long src, long dest, int numSteps) {
      init();
      Map<Long,Set<Long>> destSink = reverseGraph(generateNeighborhood(src,numSteps,srn.getAdjacencyList()));
      return reverseGraph(generateNeighborhood(dest,numSteps,destSink));
    }

    /**
     * BFS
     */
    public static Map<Long,Set<Long>> generateNeighborhood(long src, int numSteps,Map<Long, Set<Long>> initGraph) {
      Set<Long> closed = new HashSet<Long>();
      Queue<P<Long, Integer>> toExplore = new LinkedList<P<Long, Integer>>();
      toExplore.add(new P<Long,Integer>(src, 0));
      closed.add(src);
      Map<Long,Set<Long>> graph = new HashMap<Long,Set<Long>>();
      graph.put(src, new HashSet<Long>());

      while(!toExplore.isEmpty()) {
        P<Long,Integer> node = toExplore.poll();
        Long chemID = node.fst();
        Integer dist = node.snd();
        if(dist == numSteps) continue;
        Set<Long> products = initGraph.get(chemID);
        if(products==null) continue;
        for(Long p : products) {
          //no loop to self
          if (ignore.contains(p)) continue;
          if (p.equals(chemID)) continue;
          //no loop back to parent
          if (!graph.containsKey(p) || !graph.get(p).contains(chemID)) {
            graph.get(chemID).add(p);
          } else
            continue;
          if(closed.contains(p)) continue;
          closed.add(p);
          toExplore.add(new P<Long,Integer>(p, dist + 1));

          graph.put(p, new HashSet<Long>());
        }
      }
      System.out.println("Number of chemicals seen: " + closed.size());
      return graph;
    }

    public static void writeDOT(String fname, Map<Long,Set<Long>> graph) throws IOException {
      BufferedWriter dotf = new BufferedWriter(new FileWriter(fname, false)); // open for overwrite
      // taking hints from http://en.wikipedia.org/wiki/DOT_language#A_simple_example
      String graphname = "paths";
      dotf.write("digraph " + graphname + " {\n");
      dotf.write("\tnode [shape=plaintext]\n");

      Set<Long> chemicals = new HashSet<Long>();
      for (Long nid : graph.keySet()) {
        chemicals.add(nid);
        for (Long e : graph.get(nid)) {
          chemicals.add(e);
        }
      }
      for(Long c : chemicals) {
        String idName = "" + c;
        if (c >= 0) {
          Chemical chem = db.getChemicalFromChemicalUUID(c);
          String shortestName = chem.getCanon();
          for(String name : chem.getBrendaNames()) {
            if(shortestName == null ||name.length() < shortestName.length())
              shortestName = name;
          }
          idName += "_"+shortestName;
        }

        dotf.write(c + " [label=\"" + idName + "\"]\n");
      }
      System.out.println("Chemicals: " + chemicals.size());
      int edges = 0;
      for (Long nid : graph.keySet()) {
        for (Long e : graph.get(nid)) {
          dotf.write("\t" + nid + "->" + e + ";\n");
          edges++;
        }
      }
      dotf.write("}");
      dotf.close();
      System.out.println("Edges: " + edges);
    }


    public static void main(String[] args) {
      init();
      try {
        //System.out.println(srn.getGraph().containsKey(new Long(1)));
        //writeDOT("temp.dot",generateBetweenPair(12983,4271,40));
        writeDOT("temp.dot",generateNeighborhood(4271,3,srn.getAdjacencyList()));
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
