package com.act.biointerpretation.stereochemistry;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.utils.FileUtils;

import java.io.File;
import java.util.*;

/**
 * This creates Red Queen from Synapse.  Red Queen is the database in which the chemicals
 * have been inspected for stereochemical completeness, and if they are abstract, have
 * been exploded for each relevant stereoisomer.
 *
 * Created by jca20n on 10/22/15.
 */
public class ReactionStereoFixer {
    private NoSQLAPI api;
    private StereoAbstracter abstracter;

    private Map<Long,Long> oldChemIdToNew;
    private Map<String, Long> concreteInchiToNewId;
    private Set<String> abstractInchis;
    private Set<Long> dudChemicals; //Chems that don't parse

    public static void main(String[] args) {
        ReactionStereoFixer runner = new ReactionStereoFixer();
        runner.run();
    }

    public ReactionStereoFixer() {
        NoSQLAPI.dropDB("redqueen");
        this.api = new NoSQLAPI("synapse", "redqueen");
        this.abstracter = new StereoAbstracter();
        this.oldChemIdToNew = new HashMap<>();
        this.concreteInchiToNewId = new HashMap<>();
        this.abstractInchis = new HashSet<>();
        this.dudChemicals = new HashSet<>();
    }

    public void run() {
        System.out.println("Starting ReactionStereoFixer");
        long start = new Date().getTime();

        //Scan through all Reactions and collect all concrete inchis
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        int count = 0;
        while(iterator.hasNext()) {
            Reaction rxn = iterator.next();
            handleChems(rxn.getSubstrates());
            handleChems(rxn.getProducts());
            count ++;

            if(count % 10 == 0) {
                System.out.println(count);
            }
        }

        //Done hashing out all the chems
        long end = new Date().getTime();
        double duration = (end-start) / 1000;
        System.out.println("Time in seconds to process chems: " + duration);

        //Log some results of chemical hashing
        StringBuilder abstractSB = new StringBuilder();
        for(String inchi : abstractInchis) {
            abstractSB.append(inchi).append("\n");
        }

        File dir = new File("output/stereo/");
        if(!dir.exists()) {
            dir.mkdir();
        }
        FileUtils.writeFile(abstractSB.toString(), "output/stereo/abstract_inchis.txt");

        System.out.println("Number of abstract chems: " + abstractInchis.size());
        System.out.println("Number of concrete chems: " + concreteInchiToNewId.size());
        System.out.println("Number of dud chems: " + dudChemicals.size());

        //Process all the reactions again

        System.out.println("done");
    }

    private Long[] handleChems(Long[] ids)  {
        Long[] out = new Long[ids.length];

        for(int i=0; i<ids.length; i++) {
            //Pull the id
            long chemId = ids[i];

            //If the chem Id has already been processed, don't re-save
            if(oldChemIdToNew.containsKey(chemId)) {
                out[i] = oldChemIdToNew.get(chemId);
                continue;
            }

            if(dudChemicals.contains(chemId)) {
                out[i] = null;
                continue;
            }

            //Pull the inchi
            Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
            String inchi = achem.getInChI();

            if(inchi.contains("FAKE")) {
                continue;
            }

            //If the inchi has already been processed, don't re-save
            if(abstractInchis.contains(inchi)) {
                out[i] = null;
                continue; //If it's already scored as abstract, ignore it
            }
            if(concreteInchiToNewId.containsKey(inchi)) {
                out[i] = concreteInchiToNewId.get(inchi);
            }

            //Otherwise the inchi is being examined for the first time
            String newInchi = null;
            try {
                System.out.print(inchi + "\n\n");
                newInchi = abstracter.clean(inchi); //Clean it adding any ?'s
            } catch(Exception err) {
                out[i] = null;
                dudChemicals.add(chemId);
                continue;
            }


            //If it is abstract
            if (newInchi.indexOf("?") > -1) {
                abstractInchis.add(inchi);
                out[i] = null;

            //Otherwise it is new and concrete
            } else {
                achem.setInchi(newInchi);
                long newId = api.writeToOutKnowlegeGraph(achem);
                concreteInchiToNewId.put(newInchi, newId);
                oldChemIdToNew.put(chemId, newId);
                out[i] = newId;
            }
        }
        return out;
    }
}
