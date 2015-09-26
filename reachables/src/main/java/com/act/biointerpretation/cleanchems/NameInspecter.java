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
    if(true) {
        return;
    }

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

        System.out.print("NameInspecter:" + achem.getInChI());
        System.out.print("  ");
        for(String aname : names) {
            System.out.print(aname);
            System.out.print(", ");
        }
        System.out.print("\n");

    }

    public void postProcess() {
    }
}
