package com.act.biointerpretation.cleanchems;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static act.shared.Chemical.REFS;

/**
 * Created by jca20n on 9/25/15.
 */
public class NameInspecter {

    public NameInspecter(NoSQLAPI api) {

    }

    public void inspect(Chemical achem) {

        Set<String> names = new HashSet<>();
        names.addAll(achem.getBrendaNames());
        names.addAll(achem.getSynonyms());
        names.addAll(achem.getPubchemNames().keySet());
        names.addAll(achem.getPubchemNameTypes());
        names.addAll(achem.getKeywords());
        names.addAll(achem.getCaseInsensitiveKeywords());

        JSONObject metacycData = achem.getRef(REFS.METACYC);
        if(metacycData!=null) {
            //System.out.println(metacycData.toString(5));
            JSONArray meta = metacycData.getJSONArray("meta");
            JSONObject first = meta.getJSONObject(0);
            JSONArray metaNames = first.getJSONArray("names");
            for(int i=0; i<metaNames.length(); i++) {
                String aname = metaNames.getString(i);
                names.add(aname);
            }
        }

        System.out.println("\nNameInspecter:\t" + achem.getUuid() + "\t" + achem.getInChI());
        for(String aname : names) {
            System.out.println("\t" + aname);
        }

        if(metacycData!=null) {
//            System.out.println(metacycData.toString(5));
        }

        JSONObject brendaData = achem.getRef(REFS.BRENDA);
        if(brendaData!=null) {
            System.out.println(brendaData.toString(5));
        }
    }

    public void postProcess() {
    }
}
