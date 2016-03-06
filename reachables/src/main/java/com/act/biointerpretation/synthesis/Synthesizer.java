package com.act.biointerpretation.synthesis;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by jca20n on 2/15/16.
 */
public class Synthesizer {

    Indexer index;
    Map<Long,Boolean> idToEnabled;
    Map<Long, Integer> idToDistance;
    Set<Long> starters;


    public Synthesizer(Indexer index, Set<String> startingInchis) {
        this.index = index;
        idToEnabled = new HashMap<>();
        idToDistance = new HashMap<>();

        //Put in all the starting points as enabled (and converting them to longs)
        for(String inchi : startingInchis) {
            try {
                long chemIndex = index.chemicals.get(inchi);
                idToEnabled.put(chemIndex, true);
            } catch (Exception err) {
                System.err.println("error on : " + inchi);
            }
        }

        //Store the indices of these starters
        this.starters = new HashSet<>();
        starters.addAll(idToEnabled.keySet());

        //Put in all the inchis that can be products as not-enabled
        for(long chemIndex : index.hash.keySet()) {
            if(!idToEnabled.containsKey(chemIndex)) {
                idToEnabled.put(chemIndex, false);
            }
        }
    }

    /**
     * Run the synthesizer 'limit' times
     * @param limit
     */
    public void synthesize(int limit) {
        for(int i=0; i<limit; i++) {
            Set<Long> keepers = new HashSet<>();
            //For each product, determine if it is enabled
            for(long prod : index.hash.keySet()) {
                //If this product is already enabled, then don't recompute
                if(idToEnabled.get(prod)==true) {
                    continue;
                }

                boolean result = isEnabled(prod);
                if(result == true) {
                    keepers.add(prod);
                    idToDistance.put(prod,i);
                }
            }
            for(Long prod : keepers) {
                idToEnabled.put(prod, true);
            }
        }
    }

    public Set<String> getReachables() {
        Map<Long, String> idToInchi = new HashMap<>();
        for(String inchi : index.chemicals.keySet()) {
            long id = index.chemicals.get(inchi);
            idToInchi.put(id, inchi);
        }

        Set<String> out = new HashSet<>();
        for(long chemIndex : idToEnabled.keySet()) {
            boolean isenabled = idToEnabled.get(chemIndex);
            if(isenabled == true) {
                if(!starters.contains(chemIndex)) {
                    String index = idToInchi.get(chemIndex);
                    out.add(index);
                }
            }
        }
        return out;
    }

    /**
     * Determines if a given chemical product in the index, identified by its chemical index, is
     * currently enabled.  If all the substrates in any of its subsets are enabled, then it is enabled
     * @param prod
     */
    private boolean isEnabled(long prod) {
        Set<Set<Long>> subsets = index.hash.get(prod);

        //If any reaction is all enabled, this product is enabled
        perreaction: for(Set<Long> subset : subsets) {
            persubstrate: for(Long subId : subset) {
                Boolean isenabled = idToEnabled.get(subId);
                if(isenabled !=null && isenabled==true) {
                    System.out.println("> is enabled: " + prod);
                    for(Long anid : subset) {
                        System.out.print(anid + ", ");
                    }
                    System.out.println();
                }

                if(isenabled == null || isenabled == false) {
                    continue perreaction;
                }
            }

            //If got here, then all the substrates for this reaction were enabled
            return true;
        }
        //If got here, then no reaction got through the gauntlet, so this is not enabled
        return false;
    }

    public static void main(String[] args) throws Exception {
        Indexer indexer = Indexer.fromFile("output/synthesis/indexer.ser");
        //Print out contents
//        printContents(indexer);

        //Input the starting points (ie, glucose, or other chems to biotransform)
        Set<String> startingPoints = new HashSet<>();
//        startingPoints.add("InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)"); //paba
        startingPoints.add("InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5-,6-/m1/s1"); //glucose
        startingPoints.add("InChI=1S/C9H11NO3/c10-8(9(12)13)5-6-1-3-7(11)4-2-6/h1-4,8,11H," +
                "5,10H2,(H,12,13)/t8-/m0/s1"); //tyrosine

        //Run the synthesizer for x rounds
        int x = 5;
        Synthesizer synth = new Synthesizer(indexer, startingPoints);
        synth.synthesize(x);
        Set<String> inchis = synth.getReachables();

        //Print out reachables
        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");
        System.out.println("\n\nI have x inchis: " + inchis.size() + "\n");
        for(String inchi : inchis) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(inchi);
            System.out.println(achem.getFirstName() + "   " + ChemAxonUtils.InchiToSmiles(inchi));
        }
    }

    private static void printContents(Indexer indexer) {
        Map<Long, String> idToInchi = new HashMap<>();
        for(String inchi : indexer.chemicals.keySet()) {
            long id = indexer.chemicals.get(inchi);
            idToInchi.put(id, inchi);
        }

        for(long prodId : indexer.hash.keySet()) {
            Set<Set<Long>> rxns = indexer.hash.get(prodId);
//            if(rxns.size() < 2) {
//                continue;
//            }
            System.out.println(">prod" + idToInchi.get(prodId) + ":\n");
            for(Set<Long> subSet : rxns) {
                System.out.print("\t");
                for (long subId : subSet) {
                    System.out.print(idToInchi.get(subId) + ", ");
                }
                System.out.println("\n");
            }
        }
        System.out.println("done");

    }
}
