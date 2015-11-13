package com.act.biointerpretation.cleanchems;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.ggasoftware.indigo.Indigo;
import java.util.*;

/**
 * This creates ??? from Synapse.
 *
 * Created by jca20n on 9/12/15.
 */
public class ChemicalCleaner {
    private NoSQLAPI api;
    private ChemInspecter chemInspecter;

    private Map<Long, Boolean> preChecked;
    private Map<Long, Long> oldToNew;

    private long chemCount = 0;
    private long rxnCount = 0;
    private long dudChems = 0;
    private long dudRxns = 0;


    public static void main(String[] args) {
        System.out.println("Hi there!");
        ChemicalCleaner cclean = new ChemicalCleaner();
        cclean.run();
    }

    public ChemicalCleaner() {
        Indigo indigo = new Indigo();
        chemInspecter = new ChemInspecter(api);
        preChecked = new HashMap<>();
        oldToNew = new HashMap<>();
    }

    public void run() {
        long start = 0;
        long end = 0;

        //First inspect all the chemicals
        this.api = new NoSQLAPI("synapse", "nextone");
//        Iterator<Chemical> allChems = api.readChemsFromInKnowledgeGraph();
//        while(allChems.hasNext()) {

        for(int i=0; i<1000; i++) {
            double drand = Math.random()*798953;
            long lrand = (int) Math.floor(drand);
            try {
                Chemical achem = api.readChemicalFromInKnowledgeGraph(lrand);
                chemInspecter.inspect(achem);
            } catch(Exception err) {
                System.err.println("Error inspecting chemicals");
                err.printStackTrace();
            }
        }

        //Postprocess any inspectors that require that
        chemInspecter.postProcess();

        start = new Date().getTime();

        //Gather up any dud reactions
        Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
        Outer: while (rxns.hasNext()) {
            try {
                Reaction rxn = rxns.next();

                List<Long> allChemIds = new ArrayList(Arrays.asList(rxn.getSubstrates()));
                allChemIds.addAll(Arrays.asList(rxn.getProducts()));

                for(long chemId : allChemIds) {
                    if(!isClean(chemId, rxn)) {
                        continue Outer;
                    }
                }

                //The reaction's chemicals passed inspection, so transfer them to new DB
                for(long chemId : allChemIds) {
                    //If it's already in preChecked, then it must be "true" and already saved to new DB
                    if(oldToNew.containsKey(chemId)) {
                        continue;
                    }

                    Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
//                    long newid = api.writeToOutKnowlegeGraph(achem); //unsilence me for realz
                    chemCount++;
                    preChecked.put(chemId, true);
//                    oldToNew.put(chemId, newid); //unsilence me for realz
                    oldToNew.put(chemId, (long) 5); //silence me for realz
                }

                //Update the reaction's chem ids, put it in the db
                Long[] chems = rxn.getSubstrates();
                Long[] newIds = new Long[chems.length];
                for(int i=0; i<chems.length; i++) {
                    newIds[i] = oldToNew.get(chems[i]);
                }
                rxn.setSubstrates(newIds);

                chems = rxn.getProducts();
                newIds = new Long[chems.length];
                for(int i=0; i<chems.length; i++) {
                    newIds[i] = oldToNew.get(chems[i]);
                }
                rxn.setProducts(newIds);

//                api.writeToOutKnowlegeGraph(rxn); //unsilence me for realz
                rxnCount++;

            } catch (Exception err) {
                err.printStackTrace();
            }
        }

        end = new Date().getTime();

        System.out.println("Cleaning out dud chemicals: " + (end-start)/1000 + " seconds and created x,x chems and rxns: " + chemCount + " , " + rxnCount);
        System.out.println("ChemicalCleaner done!");
    }

    private boolean isClean(long chemId, Reaction rxn) {
//        if(preChecked.containsKey(chemId)) {
//            boolean checked = preChecked.get(chemId);
//            if(checked == false) {
////                log(chemId, rxn);
//            }
//            return checked;
//        }
//
//        Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
//        Chemical newchem = chemInspecter.inspect(achem);
//        if(newchem==null) {
//            return false;
//        }
//
//        //Put the chemical in the database
//        //Log the id of the chem

        return true;
    }

    class MyComparator implements Comparator<Object> {

        Map<Long, Integer> map;

        public MyComparator(Map<Long, Integer> map) {
            this.map = map;
        }

        public int compare(Object o1, Object o2) {
            int i1 = map.get(o1);
            int i2 = map.get(o2);

            if (i1 < i2) {
                return -1;
            }
            else return 1;
        }
    }

}
