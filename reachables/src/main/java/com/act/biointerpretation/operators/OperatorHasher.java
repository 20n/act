package com.act.biointerpretation.operators;

import chemaxon.common.util.Pair;
import java.util.Map.Entry;
import java.util.*;

/**
 * This is a datastructure for indexing reaction operators
 * and facilitate queries about them
 *
 * Created by jca20n on 11/9/15.
 */
public class OperatorHasher {
    private Map<String,Map<String,Map<Set<Integer>,Map<Set<Integer>,Set<Integer>>>>> megamap;
    private List<String> cofactors;

    public OperatorHasher(List<String> cofactors) {
        this.cofactors = cofactors;
        megamap = new HashMap<>();
    }

    public static void main(String[] args) {
        //Create a mini version of the cofactor list of names
        List<String> cofactors = new ArrayList<>();
        cofactors.add("water");
        cofactors.add("proton");

        //Instantiate the hasher
        OperatorHasher hasher = new OperatorHasher(cofactors);

        //Create mock data for the information about cofactors in SimpleReaction
        Set<String> subCo = new HashSet<>();
        subCo.add("water");
        Set<String> prodCo = new HashSet<>();
        prodCo.add("proton");

        //Index the mock data
        hasher.index("CI", "CO.I", subCo, prodCo, 1); //Test, amide and ester are standins for inchis
        hasher.index("CI", "CO.I", subCo, prodCo, 2); //Test, amide and ester are standins for inchis

        hasher.index("CN", "CO.N", subCo, prodCo, 3); //Test, amide and ester are standins for inchis
        hasher.index("CN", "CO.N", subCo, prodCo, 4); //Test, amide and ester are standins for inchis
        hasher.index("CN", "CO.N", subCo, prodCo, 5); //Test, amide and ester are standins for inchis
        hasher.index("CN", "CO.N", subCo, prodCo, 6); //Test, amide and ester are standins for inchis
        hasher.index("CN", "CO.N", subCo, prodCo, 7); //Test, amide and ester are standins for inchis
        hasher.index("CN", "CO.N", subCo, prodCo, 8); //Test, amide and ester are standins for inchis

        hasher.index("COC", "CO.C", subCo, prodCo, 9); //Test, amide and ester are standins for inchis
        hasher.index("COC", "CO.C", subCo, prodCo, 10); //Test, amide and ester are standins for inchis
        hasher.index("COC", "CO.C", subCo, prodCo, 11); //Test, amide and ester are standins for inchis
        hasher.index("COC", "CO.C", subCo, prodCo, 12); //Test, amide and ester are standins for inchis
        hasher.index("COC", "CO.C", subCo, prodCo, 13); //Test, amide and ester are standins for inchis

        System.out.println();

        List<Entry<Pair<String,String>, Integer>> ranked = hasher.rank();

        for(Entry<Pair<String,String>, Integer> entry : ranked) {
            int count = entry.getValue();
            Pair<String,String> pair = entry.getKey();
            System.out.println(pair.left() + "," + pair.right() + " : " + count);
        }
    }

    public List<Entry<Pair<String,String>, Integer>> rank() {
        Map<Pair<String,String>,Integer> map = new HashMap<>();
        for(String subro : megamap.keySet()) {
            Map<String,Map<Set<Integer>,Map<Set<Integer>,Set<Integer>>>> prodros = megamap.get(subro);
            for(String prodro : prodros.keySet()) {
                Map<Set<Integer>,Map<Set<Integer>,Set<Integer>>> subCOs = prodros.get(prodro);
                Pair<String,String> pair = new Pair<>(subro,prodro);
                int count = 0;

                //Count up everything below the pair
                for(Set<Integer> subCO : subCOs.keySet()) {
                    Map<Set<Integer>,Set<Integer>> prodCOs = subCOs.get(subCO);
                    for(Set<Integer> prodCO : prodCOs.keySet()) {
                        Set<Integer> rxns = prodCOs.get(prodCO);
                        count+= rxns.size();
                    }
                }

                //Put the count into the hashmap
                map.put(pair, count);
            }
        }

        //Sort the data
        Comparator comparator = new Comparator<Map.Entry<Pair<String,String>, Integer>>() {
            public int compare( Map.Entry<Pair<String,String>, Integer> o1, Map.Entry<Pair<String,String>, Integer> o2 ) {
                return (o2.getValue()).compareTo( o1.getValue() );
            }
        };

        Set<Entry<Pair<String,String>, Integer>> set = map.entrySet();
        List<Entry<Pair<String,String>, Integer>> list = new ArrayList<Entry<Pair<String,String>, Integer>>(set);
        Collections.sort(list, comparator);

        return list;
    }

    public void index(String subRO, String prodRO, Set<String> subCoStr, Set<String> prodCoStr, Integer rxn) {
        //Convert the String representation of cofactors to ints
        Set<Integer> subCo = new HashSet<>();
        for(String coName : subCoStr) {
            int index = cofactors.indexOf(coName);
            subCo.add(index);
        }
        Set<Integer> prodCo = new HashSet<>();
        for(String coName : prodCoStr) {
            int index = cofactors.indexOf(coName);
            prodCo.add(index);
        }

        //Unpack everything
        Map<String,Map<Set<Integer>,Map<Set<Integer>,Set<Integer>>>> prodros = megamap.get(subRO);
        if(prodros==null) {
            prodros = new HashMap<>();
        }

        Map<Set<Integer>,Map<Set<Integer>,Set<Integer>>> subCOs = prodros.get(prodRO);
        if(subCOs == null) {
            subCOs = new HashMap<>();
        }

        Map<Set<Integer>,Set<Integer>>  prodCOs = subCOs.get(subCo);
        if(prodCOs == null) {
            prodCOs = new HashMap<>();
        }

        Set<Integer> rxns = prodCOs.get(prodCo);
        if(rxns == null) {
            rxns = new HashSet<>();
        }

        //Repack everything
        rxns.add(rxn);
        prodCOs.put(prodCo, rxns);
        subCOs.put(subCo, prodCOs);
        prodros.put(prodRO, subCOs);
        megamap.put(subRO, prodros);
    }

}
