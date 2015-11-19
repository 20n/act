package com.act.biointerpretation.operators;

import chemaxon.common.util.Pair;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.*;

/**
 * Compares two OperatorHashers, one from BRENDA to one from Metacyc
 * Created by jca20n on 11/11/15.
 */
public class CompareROs2 {
    OperatorHasher hasher;

    public static void main(String[] args) throws Exception  {
        CompareROs2 comp = new CompareROs2();
        comp.initiate();
        Map<Pair<String,String>, Integer> counts = comp.compare();
    }

    public void initiate() throws Exception {
        hasher = OperatorHasher.deserialize("output/hash_hmERO.ser");
    }

    public Map<Pair<String,String>, Integer> compare() throws Exception {
        Map<Pair<String,String>, Integer> combined = hasher.reduceToCounts();


        List<Map.Entry<Pair<String,String>, Integer>> ranked = OperatorHasher.rank(combined);

        for(Map.Entry<Pair<String,String>, Integer> entry : ranked) {
            int count = entry.getValue();
            Pair<String,String> pair = entry.getKey();

            String subSmiles = ChemAxonUtils.InchiToSmiles(pair.left());
            String prodSmiles = ChemAxonUtils.InchiToSmiles(pair.right());

            System.out.println(subSmiles+ " >> " + prodSmiles + " : " + count);
        }

        System.out.println("Total ERO: " + ranked.size());
        return combined;
    }

}
