package org.twentyn.proteintodna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is a hacked down version of RBSChooser2 that doesn't do the update portion.
 * It just chooses an RBS.
 */
public class RBSChooser3 {
    private List<RBSOption> rbss;
    Translator translator = new Translator();

    public static RBSChooser3 initiate() throws Exception {
        RBSChooser3 out = new RBSChooser3();
        
        //Gather up all the genes, index by gene name
        Map<String,String[]> geneToData = new HashMap<>();
        String data = FileUtils.readFile2("data/CodonOptimization/coli_genes.txt");
        String[] lines = data.split("\\r|\\r?\\n");
        for(String line : lines) {
            try {
                String[] tabs = line.split("\t");
                String name = tabs[1];
                geneToData.put(name, tabs);
            } catch(Exception err) {
                continue;
            }
        }
        
        //Populate the RBS choices
        out.rbss = new ArrayList<>();
        data = FileUtils.readFile2("data/CodonOptimization/rbs_options.txt");
        lines = data.split("\\r|\\r?\\n");
        for(int i=0; i<lines.length; i++) {
            String line = lines[i];
            String[] tabs = line.split("\t");
            String name = tabs[0];
            if(!geneToData.containsKey(name)) {
                System.out.println("!! skipping + " + name);
                continue;
            }
            
            //Populate the RBS option
            RBSOption opt = new RBSOption();
            opt.rbs = tabs[1];
            opt.name = name;
            String[] rbsdata = geneToData.get(name);
            opt.cds = rbsdata[6];
            opt.first6aas = out.translator.translate(opt.cds.substring(0,18));
            out.rbss.add(opt);
        }
        
        return out;
    }
        
    public RBSOption choose(String peptide, Set<RBSOption> ignores) throws Exception {
        String pep = peptide.substring(0,18);
        
        RBSOption bestRBS = null;
        int best = 100000;
        for(RBSOption opt : rbss) {
            if(ignores.contains(opt)) {
                continue;
            }
            int score = dpEditDistance(pep, opt.first6aas);
            // This test has been completed and no Exceptions get thrown
            // so we are certain that the native optimized dynamic programming
            // yield identical results. We can remove this outdated code before mainlining.
            // int scoreNaive = naiveEditDistance(pep, opt.first6aas);
            // if (score != scoreNaive)
            //   throw new Exception("optimized and naive computation differ! test failure!");

            if(score < best) {
                best = score;
                bestRBS = opt;
            }
        }
        
        return bestRBS;
    }
    
    // this recursive (as opposed to dynamic programming) version takes O(3^18) = 387,420,489 steps
    // while the DP version would take O(18 * 18) = 324 steps
    // So 1.2M times improvement in going to non recursive version
    // This is dead code now. Delete before mainlining.
    private static int naiveEditDistance(String s1, String s2) {
        int matchDist;   // Edit distance if first char. match or do a replace
        int insertDist;  // Edit distance if insert first char of s1 in front of s2.
        int deleteDist;  // Edit distance if delete first char of s2.
        int swapDist;    // edit distance for twiddle (first 2 char. must swap).

        if (s1.length() == 0) {
            return s2.length();   // Insert the remainder of s2
        } else if (s2.length() == 0) {
            return s1.length();   // Delete the remainer of s1
        } else {
            matchDist = naiveEditDistance(s1.substring(1), s2.substring(1));
            if (s1.charAt(0) != s2.charAt(0)) {
                matchDist++;  // If first 2 char. don't match must replace
            }
            insertDist = naiveEditDistance(s1.substring(1), s2) + 1;
            deleteDist = naiveEditDistance(s1, s2.substring(1)) + 1;

            if (s1.length() > 1 && s2.length() > 1
                    && s1.charAt(0) == s2.charAt(1) && s1.charAt(1) == s2.charAt(0)) {
                swapDist = naiveEditDistance(s1.substring(2), s2.substring(2)) + 1;
            } else {
                swapDist = Integer.MAX_VALUE;  // Can't swap if first 2 char. don't match
            }
            return Math.min(matchDist, Math.min(insertDist, Math.min(deleteDist, swapDist)));
        }
    }

    // Compute edit distance using Smith-Waterman.
    private static int dpEditDistance(String s1, String s2) {
      int s1len = s1.length();
      int s2len = s2.length();

      int[][] dist = new int[s1len + 1][s2len + 1];

      for (int a = 0; a <= s1len; a++) {
        for (int b = 0; b <= s2len; b++) {
          if (a == 0) dist[a][b] = b;
          else if (b == 0) dist[a][b] = a;
          else if (s1.charAt(a - 1) == s2.charAt(b - 1)) dist[a][b] = dist[a-1][b-1];
          else dist[a][b] = 1 + Math.min(Math.min(dist[a][b-1], dist[a-1][b]), dist[a-1][b-1]);
        }
      }

      return dist[s1len][s2len];
    }
}
