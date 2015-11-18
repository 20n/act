package com.act.biointerpretation.operators;

import chemaxon.common.util.Pair;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two OperatorHashers, one from BRENDA to one from Metacyc
 * Created by jca20n on 11/11/15.
 */
public class CompareROs {
    public static void main(String[] args) throws Exception  {
        OperatorHasher brendaHash = OperatorHasher.deserialize("output/brenda_hash_ero.ser");
        OperatorHasher metacycHash = OperatorHasher.deserialize("output/metacyc_hash_ero.ser");

//        brendaHash.printOut();

        Map<Pair<String,String>, Integer> brendaPairs = brendaHash.reduceToPairs();
        Map<Pair<String,String>, Integer> metacycPairs = metacycHash.reduceToPairs();

        Map<Pair<String,String>, Integer> combined = new HashMap<>();

        for(Pair<String,String> apair : brendaPairs.keySet()) {
            int bval = brendaPairs.get(apair);

            if(metacycPairs.containsKey(apair)) {
                int mval = metacycPairs.get(apair);
                if(bval<2 || mval<2) {
                    continue;
                }
                bval += mval;
            } else {
                if(bval < 10) {
                    continue;
                }
            }

            combined.put(apair, bval);
        }

        for(Pair<String,String> apair : metacycPairs.keySet()) {
            int mval = metacycPairs.get(apair);
            if(brendaPairs.containsKey(apair)) {
                //Such entries were already included in previous operation
                continue;
            }
            if(mval < 10) {
                continue;
            }
            combined.put(apair, mval);
        }

        List<Map.Entry<Pair<String,String>, Integer>> ranked = OperatorHasher.rank(combined);

        for(Map.Entry<Pair<String,String>, Integer> entry : ranked) {
            int count = entry.getValue();
            Pair<String,String> pair = entry.getKey();

            String subSmiles = ChemAxonUtils.InchiToSmiles(pair.left());
            String prodSmiles = ChemAxonUtils.InchiToSmiles(pair.right());

            System.out.println(subSmiles+ " >> " + prodSmiles + " : " + count);
        }

        System.out.println("Total ERO: " + ranked.size());
    }
}
