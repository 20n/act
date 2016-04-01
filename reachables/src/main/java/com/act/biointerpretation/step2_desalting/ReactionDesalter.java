package com.act.biointerpretation.step2_desalting;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ReactionDesalter itself does the processing of the database using an instance of Desalter.
 * This class creates Synapse from Dr. Know.  Synapse is the database in which the chemicals
 * have been inspected for containing multiple species or ionized forms, and corrected.
 *
 * Created by jca20n on 10/22/15.
 */
public class ReactionDesalter {
    private NoSQLAPI api;
    private Desalter desalter;
    private Map<Long,Long> oldChemIdToNew;
    private Map<String, Long> inchiToNewId;

    public static void main(String[] args) {
        ReactionDesalter runner = new ReactionDesalter();
        runner.run();
    }

    public ReactionDesalter() {
        NoSQLAPI.dropDB("synapse");
        this.api = new NoSQLAPI("drknow", "synapse");
        this.desalter = new Desalter();
        this.oldChemIdToNew = new HashMap<>();
        this.inchiToNewId = new HashMap<>();
    }

    public void run() {
        System.out.println("Starting ReactionDesalter");
        long start = new Date().getTime();

        //Scan through all Reactions and process each
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Reaction rxn = iterator.next();
            Long[] newSubs = handleChems(rxn.getSubstrates());
            rxn.setSubstrates(newSubs);
            Long[] newProds = handleChems(rxn.getProducts());
            rxn.setProducts(newProds);

            //Write the modified Reaction to the db
            api.writeToOutKnowlegeGraph(rxn);
        }

        long end = new Date().getTime();
        double duration = (end-start) / 1000;
        System.out.println("Time in seconds: " + duration);
        System.out.println("done");
    }

    private Long[] handleChems(Long[] chemIds) {

        Set<Long> newIds = new HashSet<>();

        outer: for(int i=0; i<chemIds.length; i++) {
            long oldid = chemIds[i];

            //If the chemical's ID maps to a single pre-seen entry, use its existing oldid
            if(oldChemIdToNew.containsKey(oldid)) {
                long prerun = oldChemIdToNew.get(oldid);
                newIds.add(prerun);
                continue outer;
            }

            //Otherwise need to clean the chemical
            Set<String> cleanedInchis = null;
            Chemical achem = api.readChemicalFromInKnowledgeGraph(oldid);
            String inchi = achem.getInChI();

            //If it's FAKE, just go with it
            if(inchi.contains("FAKE")) {
                long newid = api.writeToOutKnowlegeGraph(achem); //Write to the db
                inchiToNewId.put(inchi, newid);
                newIds.add(newid);
                oldChemIdToNew.put(oldid, newid);
                continue outer;
            }

            try {
                cleanedInchis = Desalter.desaltMolecule(inchi);
            } catch (Exception e) {
                //TODO:  probably should handle this error differently, currently just letting pass unaltered
                long newid = api.writeToOutKnowlegeGraph(achem); //Write to the db
                inchiToNewId.put(inchi, newid);
                newIds.add(newid);
                oldChemIdToNew.put(oldid, newid);
                continue outer;
            }

            //For each cleaned chemical, put in DB or update ID
            for(String cleanInchi : cleanedInchis) {

                //If the cleaned inchi is already in DB, use existing ID, and hash the id
                if(inchiToNewId.containsKey(cleanInchi)) {
                    long prerun = inchiToNewId.get(cleanInchi);
                    newIds.add(prerun);
                    oldChemIdToNew.put(oldid, prerun);
                }

                //Otherwise update the chemical, put into DB, and hash the id and inchi
                else {
                    achem.setInchi(cleanInchi);
                    long newid = api.writeToOutKnowlegeGraph(achem); //Write to the db
                    inchiToNewId.put(cleanInchi, newid);
                    newIds.add(newid);
                    oldChemIdToNew.put(oldid, newid);
                }
            }
        }

        //Return the newIds as an array
        Long[] out = new Long[newIds.size()];
        List<Long> tempList = new ArrayList<>();
        tempList.addAll(newIds);
        for(int i=0; i<tempList.size(); i++) {
            out[i] = tempList.get(i);
        }
        return out;
    }

}
