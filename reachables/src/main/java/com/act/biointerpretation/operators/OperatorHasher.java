package com.act.biointerpretation.operators;

import chemaxon.common.util.Pair;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.io.*;
import java.util.Map.Entry;
import java.util.*;

/**
 * This is a datastructure for indexing reaction operators
 * and facilitate queries about them
 *
 * Created by jca20n on 11/9/15.
 */
public class OperatorHasher implements Serializable {
    private static final long serialVersionUID = -6151894164438402261L;
    public Map<String,Map<String,Map<Set<Integer>,Map<Set<Integer>,Set<Integer>>>>> megamap;
    public List<String> cofactors;

    public OperatorHasher(List<String> cofactors) {
        this.cofactors = cofactors;
        megamap = new HashMap<>();
    }

    public static void main(String[] args) throws Exception {
        testReadin();
    }

    private static void testReadin() throws Exception {
        OperatorHasher hasher = OperatorHasher.deserialize("output/testhashserial.ser");

        List<Entry<Pair<String,String>, Integer>> ranked = hasher.rank();

        for(Entry<Pair<String,String>, Integer> entry : ranked) {
            int count = entry.getValue();
            Pair<String,String> pair = entry.getKey();
            System.out.println(pair.left() + "," + pair.right() + " : " + count);
        }
    }

    private static OperatorHasher intersect(OperatorHasher first, OperatorHasher second) {
        //TODO
        return first;
    }

    private static void testCreate() throws Exception {
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

        //Serialize the hasher
        hasher.serialize("output/testhashserial.ser");

        List<Entry<Pair<String,String>, Integer>> ranked = hasher.rank();

        for(Entry<Pair<String,String>, Integer> entry : ranked) {
            int count = entry.getValue();
            Pair<String,String> pair = entry.getKey();
            System.out.println(pair.left() + "," + pair.right() + " : " + count);
        }
    }

    public static List<Entry<Pair<String,String>, Integer>> rank(Map<Pair<String,String>,Integer> map) {

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

    public List<Entry<Pair<String,String>, Integer>> rank() {
        Map<Pair<String,String>,Integer> map = this.reduceToPairs();
        return rank(map);
    }

    public void serialize(String filename) throws Exception {
        FileOutputStream fos = new FileOutputStream(filename);
        ObjectOutputStream out = new ObjectOutputStream(fos);
        out.writeObject(this);
        out.close();
        fos.close();
    }

    public static OperatorHasher deserialize(String filename) throws Exception {
        FileInputStream fis = new FileInputStream(filename);
        ObjectInputStream ois = new ObjectInputStream(fis);
        OperatorHasher out = (OperatorHasher) ois.readObject();
        ois.close();
        fis.close();
        return out;
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

    public Map<Pair<String, String>, Integer> reduceToPairs() {
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
        return map;
    }

    public void printOut() {
        for(String subro : megamap.keySet()) {
            System.out.println(ChemAxonUtils.InchiToSmiles(subro));

            Map<String,Map<Set<Integer>,Map<Set<Integer>,Set<Integer>>>> prodros = megamap.get(subro);
            for(String prodro : prodros.keySet()) {
                System.out.println("\t" + ChemAxonUtils.InchiToSmiles(prodro));

                Map<Set<Integer>, Map<Set<Integer>, Set<Integer>>> subCOs = prodros.get(prodro);

                //Count up everything below the pair
                for (Set<Integer> subCO : subCOs.keySet()) {
                    for(int index : subCO) {
                        String term = cofactors.get(index);
                        System.out.println("\t\t" + term);
                    }


                    Map<Set<Integer>, Set<Integer>> prodCOs = subCOs.get(subCO);
                    for (Set<Integer> prodCO : prodCOs.keySet()) {
                        for(int index : prodCO) {
                            String term = cofactors.get(index);
                            System.out.println("\t\t\t" + term);
                        }
                    }
                }
            }
        }
    }
}
