package com.act.biointerpretation.moieties;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.act.biointerpretation.utils.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Created by jca20n on 2/1/16.
 */
public class ExtractChemicalsByType {
    private NoSQLAPI api;

    public static void main(String[] args) {
        ExtractChemicalsByType ecbt = new ExtractChemicalsByType();

        String db = "synapse";

        ecbt.initiate(db);
        JSONArray drugChems = ecbt.extractDrugChems();
        System.out.println(drugChems.length());
        FileUtils.writeFile(drugChems.toString(), "output/moieties/ExtractChemicalsByType_" + db + "_drugs.json");
    }

    public JSONArray extractDrugChems() {
        JSONArray out = new JSONArray();
        Iterator<Chemical> it = api.readChemsFromInKnowledgeGraph();
        while(it.hasNext()) {
            Chemical achem = it.next();
            Long id = achem.getUuid();

            JSONObject data = null;
            boolean[] isDrug = new boolean[3];

            data = achem.getRef(Chemical.REFS.KEGG_DRUG);
            if(data==null) {
                isDrug[0] = false;
            } else {
                isDrug[0] = true;
            }

            data = achem.getRef(Chemical.REFS.DRUGBANK);
            if(data==null) {
                isDrug[1] = false;
            } else {
                isDrug[1] = true;
            }

            data = achem.getRef(Chemical.REFS.WHO);
            if(data==null) {
                isDrug[2] = false;
            } else {
                isDrug[2] = true;
            }

            for(boolean bool : isDrug) {
                if(bool) {
                    System.out.println(id);
                    System.out.println(achem.getFirstName());
                    System.out.println(achem.getInChI());
                    for(boolean bool2 : isDrug) {
                        System.out.print(bool2 + ", ");
                    }
                    System.out.println("\n");

                    //Bundle up the data
                    JSONObject json = new JSONObject();
                    json.put("name", achem.getFirstName());
                    json.put("id", achem.getUuid());
                    json.put("inchi", achem.getInChI());
                    json.put("KEGG_DRUG", isDrug[0]);
                    json.put("DRUGBANK", isDrug[1]);
                    json.put("WHO", isDrug[2]);
                    out.put(json);
                    break;
                }
            }
        }
        return out;
    }

    public void initiate(String db) {
        api = new NoSQLAPI(db, db);  //read only for this method
    }
}
