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
        OperatorHasher brendaHash = OperatorHasher.deserialize("output/brenda_hash.ser");
        OperatorHasher metacycHash = OperatorHasher.deserialize("output/metacyc_hash.ser");

        Map<Pair<String,String>, Integer> brendaPairs = brendaHash.reduceToPairs();
        Map<Pair<String,String>, Integer> metacycPairs = metacycHash.reduceToPairs();

        Map<Pair<String,String>, Integer> combined = new HashMap<>();

        for(Pair<String,String> apair : brendaPairs.keySet()) {
            //The pair needs to be in both data sources
            if(!metacycPairs.containsKey(apair)) {
                continue;
            }

            int bval = brendaPairs.get(apair);
            int mval = metacycPairs.get(apair);

            //And needs to occur at least twice in each database
            if(bval<2 || mval<2) {
                continue;
            }
            int cval = bval + mval;
            combined.put(apair, cval);
        }

        List<Map.Entry<Pair<String,String>, Integer>> ranked = OperatorHasher.rank(combined);

        for(Map.Entry<Pair<String,String>, Integer> entry : ranked) {
            int count = entry.getValue();
            Pair<String,String> pair = entry.getKey();

            String subSmiles = ChemAxonUtils.InchiToSmiles(pair.left());
            String prodSmiles = ChemAxonUtils.InchiToSmiles(pair.right());

            System.out.println(subSmiles+ " >> " + prodSmiles + " : " + count);
        }
    }
}
