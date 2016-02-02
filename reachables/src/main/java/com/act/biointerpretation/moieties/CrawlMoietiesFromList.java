package com.act.biointerpretation.moieties;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Created by jca20n on 2/1/16.
 */
public class CrawlMoietiesFromList {
    public static void main(String[] args) {
        runReachables();
    }

    private static void runReachables() {
        String filepath = "output/moieties/ExtractChemicalsByType_lucille_drugs.json";

        String data = FileUtils.readFile(filepath);
        JSONArray jsonarray = new JSONArray(data);
        List<String> inchis = new ArrayList<>();
        for(int i=0; i<jsonarray.length(); i++) {
            JSONObject json = jsonarray.getJSONObject(i);
            inchis.add(json.getString("inchi"));
        }

        CrawlMoietiesFromList crawler = new CrawlMoietiesFromList(inchis);
        Map<String, Set<String>> moieties = crawler.extractMoieties();

        //Package that up as JSON
        JSONObject json = new JSONObject();
        for(String moiInchi : moieties.keySet()) {
            if(moiInchi==null) {
                continue;
            }
            Set<String> parents = moieties.get(moiInchi);
            JSONArray jarray = new JSONArray();
            for(String parent : parents) {
                jarray.put(parent);
            }
            json.put(moiInchi, jarray);
        }
        FileUtils.writeFile(json.toString(), "output/moieties/CrawlMoietiesFromList_lucille_drugs.json");

        System.out.println("done");
    }

    public static Map<String, Set<String>> runLucille() {
        String filepath = "output/moieties/ExtractChemicalsByType_lucille_drugs.json";

        String data = FileUtils.readFile(filepath);
        JSONArray jsonarray = new JSONArray(data);
        List<String> inchis = new ArrayList<>();
        for(int i=0; i<jsonarray.length(); i++) {
            JSONObject json = jsonarray.getJSONObject(i);
            inchis.add(json.getString("inchi"));
        }

        CrawlMoietiesFromList crawler = new CrawlMoietiesFromList(inchis);
        Map<String, Set<String>> moieties = crawler.extractMoieties();

        //Package that up as JSON
        JSONObject json = new JSONObject();
        for(String moiInchi : moieties.keySet()) {
            if(moiInchi==null) {
                continue;
            }
            Set<String> parents = moieties.get(moiInchi);
            JSONArray jarray = new JSONArray();
            for(String parent : parents) {
                jarray.put(parent);
            }
            json.put(moiInchi, jarray);
        }
        FileUtils.writeFile(json.toString(), "output/moieties/CrawlMoietiesFromList_lucille_drugs.json");

        System.out.println("done");
        return moieties;
    }

    private List<String> inchis;
    private MoietyExtractor extractor;

    public CrawlMoietiesFromList(List<String> inchis) {
        this.inchis = inchis;
        extractor = new MoietyExtractor();
    }

    public Map<String, Set<String>> extractMoieties() {
        Map<String, Set<String>> out = new HashMap<>();
        for(String inchi : inchis) {

            try {
                //Do the conversion of inchi and moiety extraction
                Molecule amol = MolImporter.importMol(inchi);
                Set<Molecule> results = extractor.extract(amol);

                //Index out all the moieties
                for(Molecule moiety : results) {
                    String moiInch = ChemAxonUtils.toInchi(moiety);
                    Set<String> moiSet = out.get(moiInch);
                    if(moiSet == null) {
                        moiSet = new HashSet<>();
                    }
                    moiSet.add(inchi);
                    out.put(moiInch, moiSet);
                }
            } catch (Exception e) {
                System.out.println("Failure to extract: " + inchi);
            }

        }
        return out;
    }
}
