package com.act.biointerpretation.operators;

import java.util.*;

/**
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
        hasher.index("CN", "CO.N", subCo, prodCo, 3); //Test, amide and ester are standins for inchis
        System.out.println();
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
