package com.act.biointerpretation.step3_cofactorremoval;

import act.api.NoSQLAPI;
import act.shared.Chemical;
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

    Map<ExistingRxn, Long> rxnToNewRxnId;
    Map<String, Long> inchiToNewChemId;
    List<String> cofactorNames;

    long position = 0;

    private  long chemcount = 0;
    private  int rxncount = 0;
    private transient MechanisticValidator validator;
    private transient NoSQLAPI api;

    public CofactorRemover() {
        rxnToNewRxnId = new HashMap<>();
        inchiToNewChemId = new HashMap<>();
        cofactorNames = new ArrayList<>();

        api = new NoSQLAPI("synapse", "jarvis");
        validator = new MechanisticValidator(api);
        validator.initiate();
    }

    /**
     * Transfer all the chemicals to the new database
     * Record the inchi to new id mapping
     */
    public void transferChems() {
        Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Chemical achem = iterator.next();
            long result = api.writeToOutKnowlegeGraph(achem);
            inchiToNewChemId.put(achem.getInChI(), result);
        }
    }

    /**
     * Transfer all the reactions to the new database
     * Maintains uniqueness of substrate/products/cofactors
     * Abstracts out any cofactors
     */
    public void transferRxns() {
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

            //Retrieve the next reaction
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

            //Abstract, modify, and save rxn to new db
            processRxn(rxn);

            if(rxncount % 100 == 0) {
                try {
                    this.save("output/cofactors/CofactorRemover.ser");
                } catch(Exception err) {}
            }
        }

    }

    private void processRxn(Reaction rxn) {
        ExistingRxn out = new ExistingRxn();

        //Use MechanisticValidator to remove the cofactors
        MechanisticValidator.Report report = new MechanisticValidator.Report();
        try {
            validator.preProcess(rxn, report);
        } catch(Exception err) {
            report.log.add("Failure to preProcess " + rxn.getUUID());
        }

        //Abstract the cofactors
        for(String cofactor : report.subCofactors) {
            if(!cofactorNames.contains(cofactor)) {
                cofactorNames.add(cofactor);
            }
            out.subCos.add(cofactorNames.indexOf(cofactor));
        }
        for(String cofactor : report.prodCofactors) {
            if(!cofactorNames.contains(cofactor)) {
                cofactorNames.add(cofactor);
            }
            out.pdtCos.add(cofactorNames.indexOf(cofactor));
        }

        //Abstract the inchis for the substate and product
        for(String inchi : report.subInchis) {
            long newid = inchiToNewChemId.get(inchi);
            out.subIds.add(newid);
        }
        for(String inchi : report.prodInchis) {
            long newid = inchiToNewChemId.get(inchi);
            out.pdtIds.add(newid);
        }

        //Stop if it's a repeated reaction
        if(rxnToNewRxnId.containsKey(out)) {
            //TODO:  deal with merges if two reactions now collapse to one
            //Pull the existing reaction, add the new additional data, update the db
            return;
        }

        //Set substrates
        Long[] substrates = new Long[out.subIds.size()];
        int count = 0;
        for(long newid : out.subIds) {
            substrates[count] = newid;
            count++;
        }
        rxn.setSubstrates(substrates);

        //Set products
        Long[] products = new Long[out.pdtIds.size()];
        count = 0;
        for(long newid : out.pdtIds) {
            products[count] = newid;
            count++;
        }
        rxn.setProducts(products);

        //Set the sub cofactors
        for(int coid : out.subCos) {

        }

        //Set the prod cofactors


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

        remover.transferChems();
        remover.transferRxns();


        remover.save("output/cofactors/CofactorRemover.ser");
        System.out.println("done");
    }


}
