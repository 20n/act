package com.act.biointerpretation.operators;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by jca20n on 11/21/15.
 */
public class temporary {
    public static void main(String[] args) {
        ChemAxonUtils.license();
        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        SimpleReactionFactory simplifier = SimpleReactionFactory.generate(api);

        //Collate all the indices of pre-mapped reactions
        Set<Integer> rxnIds = new HashSet<>();
        File ssdir = new File("output/mapped_reactions");
        for(File mappingScheme : ssdir.listFiles()) {
            String dirname = mappingScheme.getName();
            if (dirname.equals("skeleton") || dirname.equals("automap")) {
            } else {
                continue;
            }
            for (File range : mappingScheme.listFiles()) {
                if (!range.getName().startsWith("range")) {
                    continue;
                }
                for (File afile : range.listFiles()) {
                    String name = afile.getName();
                    if (!name.endsWith(".ser")) {
                        continue;
                    }
                    String sid = name.replaceAll(".ser", "");
                    int id = Integer.parseInt(sid);
                    rxnIds.add(id);
                }
            }
        }

        //For each rxnId, pull from DB and serialize the SimpleReaction
        for(Integer rxnId : rxnIds) {
            //Pull the rxn
            long i = rxnId;
            Reaction rxn = null;
            try {
                rxn = api.readReactionFromInKnowledgeGraph(i);
            } catch(Exception err) {
                System.out.println("error pulling rxn " + i);
                continue;
            }

            if(rxn==null) {
                System.out.println("null rxn " + i);
                continue;
            }

            try {
                double dround = Math.floor(rxnId / 1000);
                int iround = (int) dround;

                SimpleReaction srxn = simplifier.simplify(rxn);

                //Serialize the SimpleReaction
                File dir = new File("output/simple_reactions/" + "/range" + iround + "/");
                if(!dir.exists()) {
                    dir.mkdir();
                }
                File file = new File("output/simple_reactions/" + "/range" + iround + "/" + rxnId + ".ser");

                FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
                ObjectOutputStream out = new ObjectOutputStream(fos);
                out.writeObject(srxn);
                out.close();
                fos.close();

            } catch(Exception err) {
                System.out.println("error processing " + rxnId);
            }
        }
        System.out.println("done!");
    }
}
