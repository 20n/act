package com.act.biointerpretation.purchasables;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.act.biointerpretation.step2_desalting.Desalter;
import com.act.biointerpretation.utils.ChemAxonUtils;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

/**
 * Created by jca20n on 12/16/16.
 */
public class Purchasables {
    public Map<String, String> getPurchasables() throws Exception {
        Map<String, String> out = new HashMap<>();

        //Initializae chemaxon license
        ChemAxonUtils.license();

        //Initialize Desalter
        Desalter desalter = new Desalter();

        //Connect to the database
        NoSQLAPI api = new NoSQLAPI("actv01_vijay_proteins", "actv01_vijay_proteins");
        Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();

        //Pull out all the wikipedia inchis
        Map<String, String> wikiInchisToName = new HashMap<>();

        while(iterator.hasNext()) {
            Chemical achem = iterator.next();

            JSONObject json = achem.getRef(Chemical.REFS.WIKIPEDIA);
            if(json!=null) {
                wikiInchisToName.put(achem.getInChI(), achem.getFirstName());
            }
        }

        //Parse all the MolPort chems
        File textfile = new File("/Users/jca20n/act/reachables/data/ConvertMolport/iis_smiles.txt");
        String data = FileUtils.readFileToString(textfile);
        String[] lines = data.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            try {
                String line = lines[i];
                String[] tabs = line.split("\t");
                String smiles = tabs[0];

//                System.out.println(smiles);

                //Load into Chemaxon
                String dirtyinchi = ChemAxonUtils.SmilesToInchi(smiles);
                Set<String> cleaninchis = desalter.clean(dirtyinchi);
                for(String inchi : cleaninchis) {
                    if(wikiInchisToName.containsKey(inchi)) {
                        out.put(inchi, wikiInchisToName.get(inchi));
                    }
                }
            } catch(Exception err) {
                System.err.println("Exception on line" + lines[i]);
            }
        }
        
        return out;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> inchisToNames = new Purchasables().getPurchasables();
        System.out.println(inchisToNames.size());

        StringBuilder sb = new StringBuilder();
        for(String inchi : inchisToNames.keySet()) {
            String name = inchisToNames.get(inchi);
            sb.append(inchi).append("\t").append(name).append("\n");
        }

        File outfile = new File("output/purchasables.txt");
        FileUtils.writeStringToFile(outfile, sb.toString());

        System.out.println();
    }
}
