package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import act.shared.Chemical;

import java.util.*;
import java.util.zip.Adler32;

/**
 * This creates Dr. Know from Lucille.  Dr. Know is the database in which all Reactions
 * have been merged based on the sameness of the reactions and product ids.
 *
 * Created by jca20n on 9/8/15.
 */
public class ReactionMerger {
    private NoSQLAPI api;

    public static void main(String[] args) {
        ReactionMerger merger = new ReactionMerger();
        merger.run();
    }

    public ReactionMerger() {
        this.api = new NoSQLAPI("lucille", "drknow");
    }

    public void run() {
        System.out.println("Starting ReactionMerger");
        //Populate the hashmap of duplicates keyed by a hash of the reactants and products
        long start = new Date().getTime();
        Map<String, Set<Long>> hashToDuplicates = new HashMap<>();

        Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
        while (rxns.hasNext()) {
            try {
                Reaction rxn = rxns.next();
                String hash = getHash(rxn);
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
        Map<Long, Long> oldChemToNew = new HashMap<>();
        for(String hash : hashToDuplicates.keySet()) {
            Set<Long> ids = hashToDuplicates.get(hash);

            //Merge the reactions into one
            for(Long rxnid : ids) {
                Reaction rxn = api.readReactionFromInKnowledgeGraph(rxnid);

                Long[] substrates = rxn.getSubstrates();
                Long[] products = rxn.getProducts();

                //Put in the new subsrate Ids and save any new chems
                for(int i=0; i<substrates.length; i++) {
                    Long newId = oldChemToNew.get(substrates[i]);
                    if(newId == null) {
                        Chemical achem = api.readChemicalFromInKnowledgeGraph(substrates[i]);
                        newId = api.writeToOutKnowlegeGraph(achem);
                        oldChemToNew.put(substrates[i], newId);
                    }
                    substrates[i] = newId;
                }
                rxn.setSubstrates(substrates);

                //Put in the new product Ids and save any new chems
                for(int i=0; i<products.length; i++) {
                    Long newId = oldChemToNew.get(products[i]);
                    if(newId == null) {
                        Chemical achem = api.readChemicalFromInKnowledgeGraph(products[i]);
                        newId = api.writeToOutKnowlegeGraph(achem);
                        oldChemToNew.put(products[i], newId);
                    }
                    products[i] = newId;
                }
                rxn.setProducts(products);

                //Write the reaction
                api.writeToOutKnowlegeGraph(rxn);
                break; //currently just keeps the first one, need to change such that all reactions are merged into one
            }

        }

        long end2 = new Date().getTime();
        System.out.println("Putting rxns in new db: " + (end2-end)/1000 + " seconds");
        System.out.println("done");
    }


    private String getHash(Reaction rxn) {
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

        return out.toString();
    }
}
