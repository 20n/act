package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.Iterator;

public class Cleanup {
    private static NoSQLAPI api = new NoSQLAPI();


    public static void main(String[] args) {
        checkBalance();
    }

    public static void checkBalance() {
        Iterator iterator = api.getRxns();
        while(iterator.hasNext()) {
            try {
                Reaction raw = (Reaction) iterator.next();
                SimpleReaction rxn = SimpleReaction.factory(raw);
//                System.out.println("rxn:" + raw.getReactionName());
                 balanceOne(rxn);
            } catch(Exception err) {
                err.printStackTrace();
            }


        }
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
