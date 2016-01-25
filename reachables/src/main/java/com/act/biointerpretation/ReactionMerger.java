package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Reaction;

import java.util.*;
import java.util.zip.Adler32;

/**
 * Created by jca20n on 9/8/15.
 */
public class ReactionMerger {
    private NoSQLAPI api;

    public static void main(String[] args) {
        ReactionMerger merger = new ReactionMerger();
        merger.merge();
    }

    public ReactionMerger() {
        this.api = new NoSQLAPI();
    }

    public void merge() {
        //Populate the hashmap of duplicates keyed by a hash of the reactants and products
        long start = new Date().getTime();
        Map<Long, Set<Long>> hashToDuplicates = new HashMap<>();

        Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
        while (rxns.hasNext()) {
            try {
                Reaction rxn = rxns.next();
                long hash = getHash(rxn);
                long id = rxn.getUUID();
                Set<Long> existing = hashToDuplicates.get(hash);
                if (existing == null) {
                    existing = new HashSet<>();
                }
                if (existing.contains(id)) {
                    System.err.println("Existing already has this id: I doubt this should ever be triggered");
                } else {
                    existing.add(id);
                    hashToDuplicates.put(hash, existing);
//                    System.out.println(hash);
                }

            } catch (Exception err) {
                //int newid = api.writeToOutKnowlegeGraph(rxn);
                err.printStackTrace();
            }
        }

        long end = new Date().getTime();

        System.out.println("Hashing out the reactions: " + (end-start)/1000 + " seconds");

        //Create one Reaction in new DB for each hash
        for(Long hash : hashToDuplicates.keySet()) {
            Set<Long> ids = hashToDuplicates.get(hash);

            //Merge the reactions into one
            for(Long rxnid : ids) {
                Reaction rxn = api.readReactionFromInKnowledgeGraph(rxnid);
                api.writeToOutKnowlegeGraph(rxn);
                break; //currently just keeps the first one
            }

        }

        long end2 = new Date().getTime();
        System.out.println("Putting rxns in new db: " + (end2-end)/1000 + " seconds");
        System.out.println("done");
    }


    private Long getHash(Reaction rxn) {
        StringBuilder out = new StringBuilder();
        Long[] substrates = rxn.getSubstrates();
        Long[] products = rxn.getProducts();

        Arrays.sort(substrates);
        Arrays.sort(products);

        // Add the ids of the substrates
        for (int i = 0; i < substrates.length; i++) {
            out.append(" + ");
            out.append(substrates[i]);
        }

        out.append(" >> ");

        // Add the ids of the products
        for (int i = 0; i < products.length; i++) {
            out.append(" + ");
            out.append(products[i]);
        }

        byte bytes[] = out.toString().getBytes();
        Adler32 checksum = new Adler32();
        checksum.update(bytes, 0, bytes.length);
        return checksum.getValue();
    }
}
