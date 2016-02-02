package com.act.biointerpretation.moieties;

import chemaxon.common.swing.io.util.FileUtil;
import com.act.biointerpretation.utils.FileUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by jca20n on 2/1/16.
 */
public class ScanReachablesForMoieties {
    public static void main(String[] args) {
        //Pull the reachables
        Map<String, SimChem> reachables = parseReachables();

        //Extract all drug moieties from Lucille
        Map<String, Set<String>> moietyToDrugs = CrawlMoietiesFromList.runLucille();

        //Find and store the matches
        Map<SimChem, Set<String>> reachablesToDrugs = new HashMap<>();

        for(String moiety : moietyToDrugs.keySet()) {
            if(reachables.containsKey(moiety)) {
                SimChem reachable = reachables.get(moiety);
                Set<String> drugs = moietyToDrugs.get(moiety);
                reachablesToDrugs.put(reachable, drugs);
            }
        }

        //Dump out the results
        StringBuilder sb = new StringBuilder();
        for(SimChem reachable : reachablesToDrugs.keySet()) {
            Set<String> drugs = reachablesToDrugs.get(reachable);
            sb.append(">").append(reachable.id).append("\t").append(reachable.name).append("\t").append(reachable.inchi).append("\n");
            for(String adrug : drugs) {
                sb.append(adrug).append("\n");
            }
        }

        FileUtils.writeFile(sb.toString(), "output/moieties/reachables_cross_drug_moieties.txt");

        System.out.println();
    }

    private static Map<String, SimChem> parseReachables() {
        Map<String, SimChem> out = new HashMap<>();

        String data = FileUtils.readFile("data/r-2015-10-06-new-metacyc-with-extra-cofactors.reachables.txt");
        String[] lines = data.split("(\\r|\\r?\\n)");
        for(String aline : lines) {
            String[] tabs = aline.split("\t");
            SimChem achem = new SimChem();
            achem.id = Long.parseLong(tabs[0]);
            achem.name = tabs[1];
            achem.inchi = tabs[2];

            out.put(achem.inchi, achem);
        }

        return out;
    }

    private static class SimChem {
        String name;
        String inchi;
        long id;
    }
}
