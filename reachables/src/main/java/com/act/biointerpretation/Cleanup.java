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
                System.out.println("rxn:" + raw.getReactionName());
                // balanceOne(rxn);
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



            String formula = inchi.split("/")[1];
            if(formula.equals("p+1")) {
                formula = "H1";
            }

            String[] atomSplit = formula.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

            for(int i=0; i < atomSplit.length-1; i=i+2) {
                String atom = atomSplit[i];
                String scount = atomSplit[i+1];
                int count = 0;
                try {
                    count = Integer.parseInt(scount);
                } catch(Exception err) {
                    err.printStackTrace();
                }

            }
            System.out.println();
        }
    }

}
