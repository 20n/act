package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Cleanup {
    private static NoSQLAPI api = new NoSQLAPI();
    private static Set<String> seenInchis = new HashSet<>();

    public static enum RxnError {
        BREAKS_CLEANUP,
        BAD_INCHIS,
        UNBALANCED
    }

    public static void main(String[] args) {
        cleanup();
    }

    public static void cleanup() {
        Iterator iterator = api.getRxns();
        while(iterator.hasNext()) {
            Reaction rxn = null;

            //Pull the reaction
            try {
                rxn = (Reaction) iterator.next();
            } catch(Exception err) {
                err.printStackTrace();
                continue;
            }

            //Process the reaction, put it in the new db
            try {
                if(cleanOne(rxn)!=null) {
                    putInDB(rxn);
                }
            } catch(Exception err) {
                log(rxn, RxnError.BREAKS_CLEANUP, "");
            }
        }
    }

    /**
     * Returns null if the reaction fails cleanup. Bad rxn is logged based on its first error
     * Returns either the original rxn or a new one if the data passes through the gauntlet of validation/correction
     *
     * @param rxn
     * @return
     * @throws Exception
     */
    private static Reaction cleanOne(Reaction rxn) throws Exception {
        //Check the chemicals for correctness, or fix them
        Long[] chemids = rxn.getSubstrates();

        for(Long along : chemids) {
            Chemical achem = api.getChemical(along);
            Chemical cleaned = InchiCleaner.clean(achem);
            if(cleaned == null) {
                log(rxn, RxnError.BAD_INCHIS, achem.getInChI());
                return null;
            } else {
                putInDB(cleaned);
            }
        }

        chemids = rxn.getProducts();

        for(Long along : chemids) {
            Chemical achem = api.getChemical(along);
            Chemical cleaned = InchiCleaner.clean(achem);
            if(cleaned == null) {
                log(rxn, RxnError.BAD_INCHIS, achem.getInChI());
                return null;
            } else {
                putInDB(cleaned);
            }
        }

        return rxn;
    }

    private static void putInDB(Reaction rxn) {
        System.out.println(rxn.getUUID() + " clean and going into biointerpretation db");
        //TODO:  put in DB
    }

    private static void putInDB(Chemical achem) {
        System.out.println(achem.getInChI() + " clean and going into biointerpretation db");
        //TODO:  put in DB
    }

    private static void log(Reaction rxn, RxnError errcode, String error) {
        System.err.println(errcode.toString() + "\n" + rxn.toString() + "\n" + error);
        //TODO:  append a list somewhere
    }

    private static void balanceOne(SimpleReaction rxn) throws Exception {
        System.out.println(rxn.toString());
        double subsrateBal = 0.0;
        for(String inchi : rxn.substrates) {
            Indigo indigo = new Indigo();
            IndigoInchi iinchi = new IndigoInchi(indigo);

            IndigoObject mol = iinchi.loadMolecule(inchi);
            subsrateBal += mol.monoisotopicMass();
        }
        System.out.println(subsrateBal);

        double productBal = 0.0;
        for(String inchi : rxn.products) {
            Indigo indigo = new Indigo();
            IndigoInchi iinchi = new IndigoInchi(indigo);

            IndigoObject mol = iinchi.loadMolecule(inchi);
            productBal += mol.monoisotopicMass();
        }
        System.out.println(productBal);

        if(subsrateBal == productBal) {
            System.err.println("balanced");
        } else {
            System.err.println("!!!!   Not Balanced");
        }

    }

}
