package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import org.json.JSONObject;
import org.json.JSONArray;


import java.io.File;
import java.util.*;

/**
 * This creates Synapse from Dr. Know.  Synapse is the database in which all Chemicals have either been
 * repaired or deleted.
 *
 * Created by jca20n on 9/12/15.
 */
public class ChemicalCleaner {
    private NoSQLAPI api;
    private IndigoInchi iinchi;

    private Map<Long, Boolean> preChecked;
    private Map<Long, Long> oldToNew;

    private Map<Long, List<Long>> tossed;

    private Map<String,String> correctedInchis;
    private Map<String,Scenario> scenarioInchis;

    private long chemCount = 0;
    private long rxnCount = 0;
    private long dudChems = 0;
    private long dudRxns = 0;

    private static enum Scenario {
        UKNOWN_PRODUCTS,  //The identity of the chemical is unknown
        DEFINED_LIST_ABSTRACTION, //It corresponds to a specific list of chemicals
        COFACTOR_ABSTRACTION, //It corresponds to a specific list of cofactors
        AMINOACYL_TRNA, //It is an aminoacyl tRNA
        SPECIFIC_PROTEIN, //A protein of specific sequence
        PROTEIN_COFACTOR_CLASS, //A class of proteins behaving as a cofactor, ie ferredoxin
        ABSTRACT_BIOPOLYMER_SUBSTRATE, //Unspecified biomolecule is being modified
        LIPID_CLASS, //A class of molecules that are concrete despite variable fatty acid chain length
        POLYMER, //Describes a polymeric metabolite that does not reduce to a single inchi
        UNSPECIFIED_STEREOCHEMISTRY_OR_RACEMIC, //There is a non-isomerizing ? for a stereocenter in the molecule

        INCOMPREHENSIBLE  //No idea what it's talking about
    }

    public static void main(String[] args) {
        System.out.println("Hi there!");
        ChemicalCleaner cclean = new ChemicalCleaner();
        cclean.run();
    }

    public ChemicalCleaner() {
        this.api = new NoSQLAPI("drknow", "synapse");
        Indigo indigo = new Indigo();
        iinchi = new IndigoInchi(indigo);
        preChecked = new HashMap<>();
        oldToNew = new HashMap<>();
        tossed = new HashMap<>();
        correctedInchis = new HashMap<>();
        scenarioInchis = new HashMap<>();
    }

    public void run() {
        long start = 0;
        long end = 0;

        //Read in the corrected inchis
        File dir = new File("data");
        for(File afile : dir.listFiles()) {
            start = new Date().getTime();
            if(!afile.getName().startsWith("problematic_chems_")) {
                continue;
            }
            String data = FileUtils.readFile(afile.getAbsolutePath());
            JSONArray jsonarray = new JSONArray(data);

            //Hash out data in the json
            for(int i=0; i<jsonarray.length(); i++) {
                JSONObject rxnjson = jsonarray.getJSONObject(i);
                String inchi = rxnjson.getString("inchi");
                String fixed = rxnjson.getString("fixed");
                if(!fixed.equals("none")) {
                    correctedInchis.put(inchi, fixed);
                    continue; //If there is a corrected inchi, then don't consider scenarios
                }

                String sscene = rxnjson.getString("scenario");
                if(sscene.equals("none")) {
                    System.out.println("Untagged inchi " + inchi + " " + sscene);
                } else {
                    try {
                        Scenario scenario = Scenario.valueOf(sscene);
                        scenarioInchis.put(inchi, scenario);
                        continue; //If a scenario was provided, then don't consider further
                    } catch (Exception err) {
                        System.out.println("Broke parsing of scenario " + inchi + " " + sscene);
                        err.printStackTrace();
                    }
                }
                System.out.println("Should not have gotten this far" + inchi + " " + sscene);
            }
            end = new Date().getTime();
            System.out.println("Reading data from: " + afile.getName() + " took: " + (end - start) + " milliseconds");
        }


        start = new Date().getTime();

        //Gather up any dud reactions
        Iterator<Reaction> rxns = api.readRxnsFromInKnowledgeGraph();
        Outer: while (rxns.hasNext()) {
            try {
                Reaction rxn = rxns.next();

                List<Long> allChemIds = new ArrayList(Arrays.asList(rxn.getSubstrates()));
                allChemIds.addAll(Arrays.asList(rxn.getProducts()));

                for(long chemId : allChemIds) {
                    if(!isClean(chemId, rxn)) {
                        continue Outer;
                    }
                }

                //The reaction's chemicals passed inspection, so transfer them to new DB
                for(long chemId : allChemIds) {
                    //If it's already in preChecked, then it must be "true" and already saved to new DB
                    if(oldToNew.containsKey(chemId)) {
                        continue;
                    }

                    Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
//                    long newid = api.writeToOutKnowlegeGraph(achem); //unsilence me for realz
                    chemCount++;
                    preChecked.put(chemId, true);
//                    oldToNew.put(chemId, newid); //unsilence me for realz
                    oldToNew.put(chemId, (long) 5); //silence me for realz
                }

                //Update the reaction's chem ids, put it in the db
                Long[] chems = rxn.getSubstrates();
                Long[] newIds = new Long[chems.length];
                for(int i=0; i<chems.length; i++) {
                    newIds[i] = oldToNew.get(chems[i]);
                }
                rxn.setSubstrates(newIds);

                chems = rxn.getProducts();
                newIds = new Long[chems.length];
                for(int i=0; i<chems.length; i++) {
                    newIds[i] = oldToNew.get(chems[i]);
                }
                rxn.setProducts(newIds);

//                api.writeToOutKnowlegeGraph(rxn); //unsilence me for realz
                rxnCount++;

            } catch (Exception err) {
                err.printStackTrace();
            }
        }

        end = new Date().getTime();

        System.out.println("Cleaning out dud chemicals: " + (end-start)/1000 + " seconds and created x,x chems and rxns: " + chemCount + " , " + rxnCount);
        System.out.println("Found x dudChems, and y dudRxns: " + dudChems + " , " + dudRxns);

        //Tally up the error-logged inchis and sort them by count
        start = new Date().getTime();
        Map<Long, Integer> rxnCounts = new HashMap<>();  //<inchi, num reactions>
        for(Long chemId : tossed.keySet()) {
            List<Long> ids = tossed.get(chemId);
            rxnCounts.put(chemId, ids.size());
        }
        MyComparator comparator = new MyComparator(rxnCounts);

        TreeMap<Long, Integer> sorted = new TreeMap<Long, Integer>(comparator);
        sorted.putAll(rxnCounts);
        end = new Date().getTime();
        System.out.println("Sorting to prioritize: " + (end-start) + " milliseconds");

        //Print out the top 20
        start = new Date().getTime();
        int count = 0;
        JSONArray dudChems = new JSONArray();
        for(Long chemId : sorted.descendingKeySet()) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
            String inchi = achem.getInChI();

            //If the inchi is already curated, then don't include it again
            if(correctedInchis.containsKey(inchi)) {
                continue;
            }
            if(scenarioInchis.containsKey(inchi)) {
                continue;
            }

            //Currently not curating "FAKE" inchis, so dump those
            if(inchi.contains("FAKE")) {
                continue;
            }

            //Construct a JSON for each dud chemical
            JSONObject json = new JSONObject();
            json.put("name", achem.getFirstName());
            json.put("inchi", inchi);
            json.put("fixed", "none");
            json.put("scenario", "none");
            json.put("counts", rxnCounts.get(chemId));
            json.put("uuid", achem.getUuid());
            JSONArray synonyms = new JSONArray();
            for(String aname : achem.getSynonyms()) {
                synonyms.put(aname);
            }
            json.put("synonyms", synonyms);

            //Add some example reactions to the json in an array
            List<Long> rxnIds = tossed.get(chemId);
            JSONArray tenRxns = new JSONArray();
            for(int i=0; i<10; i++) {
                if(i >= rxnIds.size()) {
                    break;
                }
                Reaction rxn = api.readReactionFromInKnowledgeGraph(rxnIds.get(i));

                //Add info about each reaction
                JSONObject rxnObj = new JSONObject();
                JSONArray substrates = new JSONArray();
                Long[] subs = rxn.getSubstrates();
                for(long anId : subs) {
                    Chemical reactant = api.readChemicalFromInKnowledgeGraph(anId);
                    substrates.put(reactant.getFirstName());
                }
                rxnObj.put("substrates", substrates);

                JSONArray products = new JSONArray();
                Long[] prods = rxn.getProducts();
                for(long anId : prods) {
                    Chemical reactant = api.readChemicalFromInKnowledgeGraph(anId);
                    products.put(reactant.getFirstName());
                }
                rxnObj.put("products", products);

                tenRxns.put(rxnObj);
            }
            json.put("examples", tenRxns);
            dudChems.put(json);

            //Terminating at 20 dud chems
            count++;
            if(count > 20) {
                break;
            }
        }

        //Save the json to file
        String datetime = new Date().toString();
        FileUtils.writeFile(dudChems.toString(5), "data/problematic_chems_" + datetime + ".json");

        end = new Date().getTime();
        System.out.println("Writing out the top few as json: " + (end - start) / 1000 + " seconds");
        System.out.println("ChemicalCleaner done!");
    }

    private void log(Long chemId, Reaction rxn) {
        List<Long> ids = tossed.get(chemId);
        if(ids==null) {
            ids = new ArrayList<>();
            dudChems++;
        }
        ids.add(Long.valueOf(rxn.getUUID()));
        dudRxns++;
        tossed.put(chemId, ids);
    }


    private boolean isClean(long chemId, Reaction rxn) {
        if(preChecked.containsKey(chemId)) {
            boolean checked = preChecked.get(chemId);
            if(checked == false) {
                log(chemId, rxn);
            }
            return checked;
        }

        Chemical achem = api.readChemicalFromInKnowledgeGraph(chemId);
        String inchi = achem.getInChI();
        if(inchi==null) {
            System.err.println("Got a null inchi, not expecting");
            return false;
        }

        //See if the inchi has a curated corrected version, and if so replace
        if(correctedInchis.containsKey(inchi)) {
            String fixed = correctedInchis.get(inchi);
            achem.setInchi(fixed);
        //Otherwise see if a scenario was flagged for the inchi, in which case this chemId should be ignored
        } else if(scenarioInchis.containsKey(inchi)) {
            preChecked.put(chemId, false);
            log(chemId, rxn);
            return false;
        }

        //If it has "FAKE", or has a ? or R don't bother parsing
        if(inchi.contains("FAKE") || inchi.contains("?") || !inchi.startsWith("InChI=1S")) {
            preChecked.put(chemId, false);
            log(chemId, rxn);
            return false;
        }

        //Try to parse the structure
        try {
            IndigoObject mol = iinchi.loadMolecule(inchi);
        } catch(Exception err) {
            preChecked.put(chemId, false);
            log(chemId, rxn);
            return false;
        }
        return true;
    }

    class MyComparator implements Comparator<Object> {

        Map<Long, Integer> map;

        public MyComparator(Map<Long, Integer> map) {
            this.map = map;
        }

        public int compare(Object o1, Object o2) {
            int i1 = map.get(o1);
            int i2 = map.get(o2);

            if (i1 < i2) {
                return -1;
            }
            else return 1;
        }
    }

}
