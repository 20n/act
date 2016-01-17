package com.act.biointerpretation.step3_mechanisminspection;

import com.act.biointerpretation.utils.FileUtils;

import java.util.*;

/**
 * Analyzes the results of running the main method of MechanisticValidator.
 * It bundles up the different results and their count, and dumps out a report
 *
 * Created by jca20n on 1/16/16.
 */
public class ValidationAnalyzer {
    public static void main(String[] args) {
        List<RxnAnalysis> rxnanalyses = new ArrayList<>();

        Map<Integer, Set<Integer>> scoreToRxnId = new HashMap<>();
        Map<Integer, Set<Integer>> roIdToRxnId = new HashMap<>();

        //Read in data
        String data = FileUtils.readFile("output/MechanisticValidator_dbscan.txt");
        data = data.replaceAll("\"", "");
        String[] lines = data.split("\\r|\\r?\\n");
        for(int i=0; i<lines.length; i++) {
            String[] tabs = lines[i].split("\t");
            RxnAnalysis ra = new RxnAnalysis();
            ra.rxnId = Integer.parseInt(tabs[0]);
            ra.score = Integer.parseInt(tabs[1]);
            try {
                ra.roId = Integer.parseInt(tabs[2]);
                ra.roName = tabs[3];

                //Put into map
                Set<Integer> existing = roIdToRxnId.get(ra.roId);
                if(existing==null) {
                    existing = new HashSet<>();
                }
                existing.add(ra.rxnId);
                roIdToRxnId.put(ra.roId, existing);

            } catch(Exception err) {}

            //Put in the score map
            Set<Integer> existing = scoreToRxnId.get(ra.score);
            if(existing==null) {
                existing = new HashSet<>();
            }
            existing.add(ra.rxnId);
            scoreToRxnId.put(ra.score, existing);

            //Put in the list
            rxnanalyses.add(ra);
        }

        //Output the results to file
        StringBuilder sb = new StringBuilder();
        sb.append(">>Scores:\n\n");
        for(Integer score : scoreToRxnId.keySet()) {
            Set<Integer> rxns = scoreToRxnId.get(score);
            sb.append(score).append("\t").append(rxns.size()).append("\n");
        }
        sb.append("\n\n>>ROs:\n\n");
        for(Integer ro : roIdToRxnId.keySet()) {
            Set<Integer> rxns = roIdToRxnId.get(ro);
            sb.append(ro).append("\t").append(rxns.size()).append("\n");
        }

        System.out.println(sb.toString());
    }

    static class RxnAnalysis {
        int rxnId;
        int score;
        int roId;
        String roName;
    }
}
