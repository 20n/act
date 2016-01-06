package com.act.biointerpretation.operators;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

/**
 * Created by jca20n on 12/21/15.
 */
public class ConsolidatedCurationPart2 {
    //These are all parallel (same indices) info about an ERO that got curated
    List<JSONObject> curation;  //All the curation json, with no filtering applied
    List<RORecord> hcEROs;  //All the hcEROs wrapped in a more useful form, indices correspond to curation json, still unfiltered
    Map<Integer, File> idToRxn; //Mapping of rxnId to the ReactionInterpretation file

    public static void main(String[] args) {
        ConsolidatedCurationPart2 cc = new ConsolidatedCurationPart2();
        cc.initiate();


        List<Integer> testfiles = cc.generateTestSet();
        System.out.println("Have x test files: " + testfiles.size());

        List<RORecord> perfectROs = cc.generatePerfectROs();
        System.out.println("Have x perfect ros: " + perfectROs.size());

        System.out.println("done");
    }

    public ReactionInterpretation getRxn(Integer id) {
        File afile = this.idToRxn.get(id);
        String data = FileUtils.readFile(afile.getAbsolutePath());
        ReactionInterpretation out = ReactionInterpretation.parse(data);
        return out;
    }

    public List<Integer> generateTestSet() {
        List<Integer> out = new ArrayList<>();
        /**
         criteria:  Should be correct data, only uncertainty is the Ro abstraction

         confidence  -  present and not low
         validation  -  true
         hydrate -  null
         error   - null
         mixed_products  -  null
         desalting    -  null
         tautomer    -  null
         */

        for(int i=0; i<curation.size(); i++) {
            JSONObject json = curation.get(i);
            try {
                boolean validation = json.getBoolean("validation");
                if(validation==false) {
                    continue;
                }
            } catch(Exception err) {
                continue;
            }

            try {
                String confidence = json.getString("confidence");
                if(confidence.equals("low")) {
                    continue;
                }
            } catch(Exception err) {}

            try {
                boolean hydrate = json.getBoolean("hydrate");
                continue;
            } catch(Exception err) {}

            try {
                boolean error = json.getBoolean("error");
                continue;
            } catch(Exception err) {}

            try {
                boolean mixed_products = json.getBoolean("mixed_products");
                continue;
            } catch(Exception err) {}

            try {
                boolean desalting = json.getBoolean("desalting");
                continue;
            } catch(Exception err) {}

            try {
                boolean tautomer = json.getBoolean("tautomer");
                continue;
            } catch(Exception err) {}

            //If got through that, the data should be included in test set
            RORecord record = this.hcEROs.get(i);
            out.addAll(record.expectedRxnIds);
        }
        return out;
    }

    public List<RORecord> generatePerfectROs() {
        List<RORecord> out = new ArrayList<>();

        //Gauntlet of curation criteria for keeping an RO
        for(int i=0; i<curation.size(); i++) {
            JSONObject json = curation.get(i);

            //Needs 3 keys
            if(json.keySet().size() < 3) {
                continue;
            }

            try {
                boolean validation = json.getBoolean("validation");
                if(validation==false) {
                    continue;
                }
            } catch(Exception err) {
                continue;
            }

            try {
                String confidence = json.getString("confidence");
                if(confidence.equals("low")) {
                    continue;
                }
            } catch(Exception err) {}

            try {
                boolean hydrate = json.getBoolean("hydrate");
                continue;
            } catch(Exception err) {}

            try {
                boolean error = json.getBoolean("error");
                continue;
            } catch(Exception err) {}

            try {
                boolean mixed_products = json.getBoolean("mixed_products");
                continue;
            } catch(Exception err) {}

            try {
                boolean desalting = json.getBoolean("desalting");
                continue;
            } catch(Exception err) {}

            try {
                boolean tautomer = json.getBoolean("tautomer");
                continue;
            } catch(Exception err) {}

            try {
                boolean cofactor = json.getBoolean("cofactor");
                continue;
            } catch(Exception err) {}

            try {
                boolean aam = json.getBoolean("aam");
                continue;
            } catch(Exception err) {}

            try {
                boolean nro = json.getBoolean("nro");
                continue;
            } catch(Exception err) {}

            try {
                boolean NRO = json.getBoolean("NRO");
                continue;
            } catch(Exception err) {}

            try {
                boolean twotimes = json.getBoolean("twotimes");
                continue;
            } catch(Exception err) {}

            try {
                boolean twosites = json.getBoolean("twosites");
                continue;
            } catch(Exception err) {}

            try {
                boolean twostep = json.getBoolean("twostep");
                continue;
            } catch(Exception err) {}

            try {
                boolean coenzyme = json.getBoolean("coenzyme");
                continue;
            } catch(Exception err) {}

            //If got through all that, it's "perfect"
            out.add(hcEROs.get(i));
        }

        return out;
    }

    public void initiate() {
        curation = new ArrayList<>();
        hcEROs = new ArrayList<>();
        idToRxn = new HashMap<>();

        //Pull out all the curated hcEROs
        File dir = new File("output/hmEROs");

        for(File croDir : dir.listFiles()) {
            if(!croDir.isDirectory()) {
                continue;
            }

            for(File hmEROdir : croDir.listFiles()) {
                if(!hmEROdir.isDirectory()) {
                    continue;
                }

                for(File hcEROdir : hmEROdir.listFiles()) {
                    if(!hcEROdir.isDirectory()) {
                        continue;
                    }

                    try {
                        //Pull out the json
                        File jsonFile = new File( hcEROdir.getAbsolutePath() + "/hcERO.json");

                        //If the json doesn't exist, then it was never curated, so ignore
                        if(!jsonFile.exists()) {
                            continue;
                        }
                        JSONObject json = new JSONObject(FileUtils.readFile(jsonFile.getAbsolutePath()));
                        curation.add(json);

                        //Create the RORecord and populate the RO itself
                        RORecord record = new RORecord();
                        hcEROs.add(record);
                        record.hcERO  = FileUtils.readFile(hcEROdir.getAbsolutePath() + "/hcERO.txt");

                        //Populate the isTrim field
                        Boolean trim = null;
                        try {
                            trim = json.getBoolean("trim");
                        } catch (Exception err) {}

                        if(trim==null) {
                            try {
                                trim = json.getBoolean("tirm");
                            } catch(Exception err) {}
                        }
                        record.isTrim = trim;

                        //Populate all the reactions
                        record.expectedRxnIds = new HashSet<>();
                        for(File afile : hcEROdir.listFiles()) {
                            if(!afile.getName().endsWith(".txt")) {
                                continue;
                            }
                            if(afile.getName().startsWith("hcERO")) {
                                continue;
                            }
                            ReactionInterpretation ri = ReactionInterpretation.parse(FileUtils.readFile(afile.getAbsolutePath()));
                            record.expectedRxnIds.add(ri.rxnId);
                            idToRxn.put(ri.rxnId, afile);
                        }


                    } catch(Exception err) {
                        err.printStackTrace();
                        System.out.println("error on: " + hcEROdir.getAbsolutePath());
                    }

                }


            }
        }
    }
}
