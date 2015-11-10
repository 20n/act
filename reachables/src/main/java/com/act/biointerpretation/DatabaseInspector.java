package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.utils.FileUtils;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.Iterator;

/**
 * Prints out a tsv of some reaction contents
 * from the database indicated as db
 *
 * This is just a convenience thing, it does not modify the db
 *
 * Created by jca20n on 10/22/15.
 */
public class DatabaseInspector {

    private static final String db = "synapse";
    private static final int limit = 100;
    private static final boolean keepFAKE = false;

    private static NoSQLAPI api;
    private static Indigo indigo = new Indigo();
    private static IndigoInchi iinchi = new IndigoInchi(indigo);

    public static void main(String[] args) {
        StringBuilder fullSB = new StringBuilder();

        api = new NoSQLAPI(db, db);
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

        int count = 0;
        int dudCount = 0;
        rxnscan: while(iterator.hasNext()) {
            if(count>limit) {
                break;
            }

            StringBuilder sb = new StringBuilder();
            Reaction rxn = iterator.next();

            System.out.println(count);

            sb.append(rxn.getReactionName()).append("\n");
            sb.append("substrates:").append("\n");
            for(Long id : rxn.getSubstrates()) {
                boolean isGood = printout(sb, id);
                if(isGood==false) {
                    dudCount++;
                    if(keepFAKE==false) {
                        continue rxnscan;
                    }
                }
            }
            sb.append("products:").append("\n");
            for(Long id : rxn.getProducts()) {
                boolean isGood = printout(sb, id);
                if(isGood==false) {
                    dudCount++;
                    if(keepFAKE==false) {
                        continue rxnscan;
                    }
                }
            }
            fullSB.append(sb);
            count++;
        }

        if(keepFAKE) {
            FileUtils.writeFile(fullSB.toString(), "output/databaseContentsFull.txt");
            double score = (count -dudCount) / count;
            System.out.println("Percent non-FAKE rxnrs: " + score);
        } else {
            FileUtils.writeFile(fullSB.toString(), "output/databaseContentsNoFAKE.txt");
            double score = count / (count + dudCount);
            System.out.println("Percent non-FAKE rxnrs: " + score);
        }
    }

    /**
     * returns true or false based on whether the chem was resolved to smiles
     * @param sb
     * @param id
     * @return
     */
    private static boolean printout(StringBuilder sb, long id) {
        Chemical achem = api.readChemicalFromInKnowledgeGraph(id);
        String inchi = achem.getInChI();
        String name = achem.getFirstName();
        try {
            IndigoObject mol = iinchi.loadMolecule(inchi);
            String smiles = mol.canonicalSmiles();
            sb.append(id).append("\t").append(name).append("\t").append(inchi).append("\t").append(smiles).append("\n");
            return true;
        } catch(Exception err) {
            sb.append(id).append("\t").append(name).append("\t").append(inchi).append("\t").append("failed load\n");
            return false;
        }
    }
}
