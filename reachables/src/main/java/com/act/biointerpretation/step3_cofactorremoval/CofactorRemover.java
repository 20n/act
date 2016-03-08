package com.act.biointerpretation.step3_cofactorremoval;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.step4_mechanisminspection.MechanisticValidator;

import java.io.*;
import java.util.*;

/**
 * Created by jca20n on 2/15/16.
 *
 * This class reads in the synapse database and creates jarvis
 * It removes the cofactors, or rather abstracts them and moves
 * them to another place in the Reaction objects
 */
public class CofactorRemover implements Serializable {
    private static final long serialVersionUID = -3632199916443842212L;

    Map<String, Long> hashToNewRxnId;
    Map<String, Long> inchiToNewChemId;
    long position = 0;

    private  long chemcount = 0;
    private  int rxncount = 0;
    private transient MechanisticValidator validator;
    private transient NoSQLAPI api;

    public CofactorRemover() {
        hashToNewRxnId = new HashMap<>();
        inchiToNewChemId = new HashMap<>();
        api = new NoSQLAPI("synapse", "jarvis");
        validator = new MechanisticValidator(api);
        validator.initiate();
    }

    public void populate() {
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

        //Find the end of the ids (this is done so can restart from pre-saved index)
        long highest = 0;
        while(iterator.hasNext()) {
            iterator.next();
            try {
                Reaction rxn = iterator.next();
                long id = rxn.getUUID();
                if (id > highest) {
                    highest = id;
                }
            } catch(Exception err) {
                break;
            }
        }


        //Scan through each reaction, starting with a pre-saved index
        for(long i=position; i<highest; i++) {
            position = i;
            Reaction rxn = null;
            try {
                rxn = api.readReactionFromInKnowledgeGraph(i);
            } catch(Exception er2) {
                System.out.println("err 1 - " + i);
                continue;
            }
            if(rxn==null) {
                System.out.println("err 2 - " + i);
                continue;
            }
            rxncount++;

            System.out.println("working: " + rxn.getUUID());

            //Use MechanisticValidator to remove the cofactors
            MechanisticValidator.Report report = new MechanisticValidator.Report();
            try {
                //Remove both concrete and FAKE cofactors
                validator.preProcess(rxn, report);

                //Populate the substrate set for this reaction
                Set<Long> subSet = new HashSet<>();
//                for(String subInchi : report.subInchis) {
//                    Long chemIndex = null;
//                    if(inchiToNewChemId.containsKey(subInchi)) {
//                        chemIndex = inchiToNewChemId.get(subInchi);
//                    } else {
//                        chemIndex = chemcount;
//                        inchiToNewChemId.put(subInchi, chemIndex);
//                        chemcount++;
//                    }
//                    subSet.add(chemIndex);
//                }

                //Associate the substrate set with each product
                for(String prodInchi : report.prodInchis) {
//
//                    Long chemIndex = null;
//                    if(inchiToNewChemId.containsKey(prodInchi)) {
//                        chemIndex = inchiToNewChemId.get(prodInchi);
//                    } else {
//                        chemIndex = chemcount;
//                        inchiToNewChemId.put(prodInchi, chemIndex);
//                        chemcount++;
//                    }
//                    Set<Set<Long>> existing = hashToNewRxnId.get(chemIndex);
//                    if (existing == null) {
//                        existing = new HashSet<>();
//                    }
//                    existing.add(subSet);
//                    hashToNewRxnId.put(chemIndex, existing);
                }
            } catch(Exception err) {
                report.log.add("Failure to preProcess " + rxn.getUUID());
            }
            if(rxncount % 100 == 0) {
                try {
                    this.save("output/cofactors/CofactorRemover.ser");
                } catch(Exception err) {}
            }
        }

    }

    /**
     * Might be wrong signature here....goal should be to remove exact matches first (Set<Long> >> Long)
     * then secondarily match this stuff
     */
    private String createRxnHash(Set<String> subInchis, Set<String> prodInchis, Set<String> subCos, Set<String> prodCos) {
        StringBuilder sb = new StringBuilder();

        return sb.toString();
    }

    public void save(String path) throws Exception {
        FileOutputStream fos = new FileOutputStream(path);
        ObjectOutputStream out = new ObjectOutputStream(fos);
        out.writeObject(this);
        out.close();
        fos.close();
    }

    public static CofactorRemover fromFile(String path) throws Exception {
        FileInputStream fis = new FileInputStream(path);
        ObjectInputStream ois = new ObjectInputStream(fis);
        CofactorRemover out = (CofactorRemover) ois.readObject();
        ois.close();
        fis.close();
        return out;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File("output/cofactors");
        if(!dir.exists()) {
            dir.mkdir();
        }

        //Load existing data, or start over
        CofactorRemover remover = null;
        try {
            remover = CofactorRemover.fromFile("output/cofactors/CofactorRemover.ser");
            remover.api = new NoSQLAPI();
            remover.validator = new MechanisticValidator(remover.api);
        } catch(Exception err) {}
        if(remover == null) {
            remover = new CofactorRemover();
        }

        remover.populate();

        System.out.println(remover.hashToNewRxnId.size());

        remover.save("output/cofactors/CofactorRemover.ser");
        System.out.println("done");
    }
}
