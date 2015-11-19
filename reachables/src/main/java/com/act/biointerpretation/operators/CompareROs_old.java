package com.act.biointerpretation.operators;

import chemaxon.common.util.Pair;
import com.act.biointerpretation.operators.OperatorHasher.Operator;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.*;

/**
 * Compares two OperatorHashers, one from BRENDA to one from Metacyc
 * Created by jca20n on 11/11/15.
 */
public class CompareROs_old {
    OperatorHasher brendaHash;
    OperatorHasher metacycHash;

    public static void main(String[] args) throws Exception  {
        CompareROs_old comp = new CompareROs_old();
        comp.initiate();
        Map<Pair<String,String>, Integer> counts = comp.compare();
        Map<Pair<String,String>, Set<Operator>> Cops = comp.expand(counts);

        //Print out Cops
        int count = 0;
        for(Pair<String,String> pair : Cops.keySet()) {
            count++;
            System.out.print(count + "  ");
            System.out.print(ChemAxonUtils.InchiToSmiles(pair.left()));
            System.out.print(" >> ");
            System.out.println(ChemAxonUtils.InchiToSmiles(pair.right()));

            System.out.println("#tot_rxns: " + counts.get(pair));

            Set<Operator> ops = Cops.get(pair);

            for(Operator op : ops) {
                Set<String> subs = op.subCofactors;
                Set<String> prods = op.prodCofactors;
                for(String sub : subs) {
                    System.out.println("\t+" + sub);
                }
                for(String prod : prods) {
                    System.out.println("\t-" + prod);
                }
                System.out.println("\t#rxns: " + op.rxnIds.size());
                System.out.println();
            }

        }


        System.out.println();
    }

    public void initiate() throws Exception {
        brendaHash = OperatorHasher.deserialize("output/brenda_hash_ero.ser");
        metacycHash = OperatorHasher.deserialize("output/metacyc_hash_ero.ser");
    }

    public Map<Pair<String,String>, Set<Operator>> expand(Map<Pair<String,String>, Integer> combined) {
        Map<Pair<String,String>, Set<Operator>> out = new HashMap<>();

        Map<Pair<String,String>, Set<Operator>> brendaOps = brendaHash.reduceToOperators();
        Map<Pair<String,String>, Set<Operator>> metacycOps = metacycHash.reduceToOperators();

        for(Pair<String,String> pair : combined.keySet()) {
            Set<Operator> Bops = brendaOps.get(pair);
            Set<Operator> Mops = metacycOps.get(pair);

            if(Bops == null && Mops != null) {
                out.put(pair, Mops);
            } else if(Bops != null && Mops == null) {
                out.put(pair, Bops);
            } else if(Bops == null && Mops == null) {
                System.out.println("bops and mops null, shouldn't happen");
            } else if(Bops != null && Mops != null) {
                Set<Operator> Mops2 = new HashSet<>(Mops);
                Set<Operator> Cops = new HashSet<>();
                for(Operator bop : Bops) {
                    for(Operator mop : Mops) {
                        if(bop.matches(mop)) {
                            bop.rxnIds.addAll(mop.rxnIds);
                            Mops2.remove(mop);
                        }
                    }
                    Cops.add(bop);
                }
                for(Operator mop : Mops2) {
                    Cops.add(mop);
                }
                out.put(pair, Cops);
            }
        }
        return out;
    }

    public Map<Pair<String,String>, Integer> compare() throws Exception {
        Map<Pair<String,String>, Integer> brendaPairs = brendaHash.reduceToCounts();
        Map<Pair<String,String>, Integer> metacycPairs = metacycHash.reduceToCounts();

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

//            System.out.println(subSmiles+ " >> " + prodSmiles + " : " + count);
        }

        System.out.println("Total ERO: " + ranked.size());
        return combined;
    }

}
