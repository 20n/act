package com.act.biointerpretation.cofactors;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Created by jca20n on 11/12/15.
 */
public class FakeCofactorFinder {

    public static void main(String[] args) {
        FakeCofactorFinder finder = new FakeCofactorFinder();

        NoSQLAPI api = new NoSQLAPI("synapse", "nextone");
        Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Chemical achem = iterator.next();
            if(!achem.getInChI().contains("FAKE")) {
                continue;
            }
            String term = finder.scan(achem);
            if(term!=null) {
                System.out.println(term);
            }
        }
    }

    public String scan(Chemical achem) {
        Set<String> names = new HashSet<>();
        names.addAll(achem.getBrendaNames());
        names.addAll(achem.getSynonyms());
        names.addAll(achem.getPubchemNames().keySet());
        names.addAll(achem.getPubchemNameTypes());
        names.addAll(achem.getKeywords());
        names.addAll(achem.getCaseInsensitiveKeywords());

        JSONObject metacycData = achem.getRef(Chemical.REFS.METACYC);
        if(metacycData!=null) {
            JSONArray meta = metacycData.getJSONArray("meta");
            JSONObject first = meta.getJSONObject(0);
            JSONArray metaNames = first.getJSONArray("names");
            for(int i=0; i<metaNames.length(); i++) {
                String aname = metaNames.getString(i);
                names.add(aname);
            }
        }

        StringBuilder sb = new StringBuilder();
        for(String name : names) {
            sb.append(name).append("\t");
        }

        String allNames = sb.toString();

        for(String term : terms.keySet()) {
            if(allNames.contains(term)) {
                return terms.get(term);
            }
        }

        return null;
    }

    private LinkedHashMap<String, String> terms;

    public FakeCofactorFinder() {
        terms = new LinkedHashMap<>();

        terms.put("NAD(P)H", "NAD(P)H");
        terms.put("NAD(P)+", "NAD(P)+");

        terms.put("5,10-methylene-tetrahydrofolate", "tetrahydrofolate");
        terms.put("tetrahydrofolate", "tetrahydrofolate");
        terms.put("folate", "folate");

        terms.put("flavin", "flavin");

        terms.put("coenzyme F", "coenzyme F");

        terms.put("ubiquinone", "ubiquinone");
        terms.put("electron-transfer quinone", "quinone");
        terms.put("menaquinone", "menaquinone");
        terms.put("dihydroubiquinone", "quinone");
        terms.put("quinone", "quinone");

        terms.put("ubiquinol", "ubiquinol");
        terms.put("electron-transfer quinol", "quinol");
        terms.put("menaquinol", "menaquinol");
        terms.put("quinol", "quinol");

        terms.put("reduced electron acceptor", "reduced electron acceptor");
        terms.put("ferrocytochrome", "ferrocytochrome");

        terms.put("glutathione", "glutathione");
        terms.put("tetrahydropteroylpolyglutamate", "tetrahydropteroylpolyglutamate");

        terms.put("oxidized factor F", "oxidizing agent");
        terms.put("oxidiz", "oxidizing agent");
        terms.put("reduc", "reducing agent");
        terms.put("electron", "redox agent");
    }

    public List<String> getTerms() {
        List<String> out = new ArrayList<>();
        out.addAll(terms.values());
        return out;
    }
}
