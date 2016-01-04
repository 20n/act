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
public class ConsolidatedCuration {
    //These are all parallel (same indices) info about an ERO that got curated
    List<JSONObject> curation;
    List<String> hcEROs;
    List<String> dirPaths;


    List<String> curationKeys;


    public static void main(String[] args) {
        ConsolidatedCuration cc = new ConsolidatedCuration();
        cc.initiate();

        for(String key : cc.curationKeys) {
            System.out.println(key);
        }

        String[][] inchis = cc.extractPotentialCofactors();

        StringBuilder sb = new StringBuilder();
        for(int i=0; i< inchis.length; i++) {
            String inchi = inchis[i][0];
            String smile = inchis[i][1];
            sb.append(inchi).append("\t");
            sb.append(smile).append("\n");
        }

//        FileUtils.writeFile(sb.toString(), "output/potentialCofactors.txt");

        List<File> testfiles = cc.generateTestSet();
        System.out.println("Have x test files: " + testfiles.size());

        Map<String, Boolean> perfectROs = cc.generatePerfectROs();
        System.out.println("Have x perfect ros: " + perfectROs.size());

        System.out.println("done");
    }

    /**
     * Scan through all the curation and deal with any things tagged as
     * having previously-unknown cofactors.
     *
     * Returns a list of InChis that are *potential* cofactors
     *
     * @return
     */
    public String[][] extractPotentialCofactors() {
        Set<String> inchisOut = new HashSet<>();
        for(int i=0; i<curation.size(); i++) {
            JSONObject json = curation.get(i);
            try {
                boolean hasco = json.getBoolean("cofactor");
                if(hasco==false) {
                    System.out.println("have a false hasco");
                    continue;
                }

                String path = dirPaths.get(i);
                File dir = new File(path);

                //Create a mapping of inchis to their count in this hcERO class
                Map<String, Integer> potentialCofactors = new HashMap<>();

                //Scan through each reaction and count up all the inchis
                for(File afile : dir.listFiles()) {
                    if(!afile.getName().endsWith(".txt")) {
                        continue;
                    }
                    if(afile.getName().startsWith("hcERO")) {
                        continue;
                    }

                    String rxninfo = FileUtils.readFile(afile.getAbsolutePath());
                    ReactionInterpretation rxn = ReactionInterpretation.parse(rxninfo);
                    RxnMolecule rxnMolecule = RxnMolecule.getReaction(MolImporter.importMol(rxn.mapping));

                    int reactantcount = rxnMolecule.getReactantCount();
                    int productCount = rxnMolecule.getProductCount();

                    if(reactantcount > 1) {
                        for(int j=0; j<reactantcount; j++) {
                            Molecule amol = rxnMolecule.getReactant(j);
                            String inchi = ChemAxonUtils.toInchi(amol);
                            Integer count = potentialCofactors.get(inchi);
                            if(count==null) {
                                count = 1;
                            }
                            potentialCofactors.put(inchi, count);
                        }
                    }
                    if(productCount > 1) {
                        for(int j=0; j<productCount; j++) {
                            Molecule amol = rxnMolecule.getProduct(j);
                            String inchi = ChemAxonUtils.toInchi(amol);
                            Integer count = potentialCofactors.get(inchi);
                            if(count==null) {
                                count = 1;
                            }
                            potentialCofactors.put(inchi, count);
                        }
                    }
                }

                //Choose the top five inchis observed
                for(int x=0; x<3; x++) {
                    int bestcount = 0;
                    String bestinchi = "";
                    for (String inchi : potentialCofactors.keySet()) {
                        int count = potentialCofactors.get(inchi);
                        if (count > bestcount) {
                            bestcount = count;
                            bestinchi = inchi;
                        }
                    }
                    if(!bestinchi.equals("")) {
                        inchisOut.add(bestinchi);
                        potentialCofactors.remove(bestinchi);
                    }
                }
            } catch(Exception err) {
                //Means it doesn't have the cofactor key, which is normal
            }

        }

        String[][] out = new String[inchisOut.size()][2];
        int count = 0;
        for(String inchi : inchisOut) {
            try {
                Molecule amol = MolImporter.importMol(inchi);
                String smiles = ChemAxonUtils.toSmilesSimplify(amol);
                out[count][0] = inchi;
                out[count][1] = smiles;
            } catch (MolFormatException e) {
            }
            count++;
        }

        return out;
    }

    public List<File> generateTestSet() {
        List<File> out = new ArrayList<>();
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
            String dirpath = this.dirPaths.get(i);
            File dir = new File(dirpath);
            for(File afile : dir.listFiles()) {
                if(afile.getName().startsWith("hcERO")) {
                    continue;
                }
                if(!afile.getName().endsWith(".txt")) {
                    continue;
                }
                out.add(afile);
            }

        }
        return out;
    }

    /**
     * Goes through json data and pulls out all the "perfect" ROs
     *
     * Returns a map of RO (as smarts string) to trim value (true, false, or null)
     * @return
     */
    public Map<String, Boolean> generatePerfectROs() {
        Map<String, Boolean> out = new HashMap<>();

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

            Boolean trim = null;
            try {
                trim = json.getBoolean("trim");
            } catch (Exception err) {}

            if(trim==null) {
                try {
                    trim = json.getBoolean("tirm");
                } catch(Exception err) {}
            }

            String hcero = this.hcEROs.get(i);
            out.put(hcero, trim);
        }

        return out;
    }

    public void initiate() {
        curation = new ArrayList<>();
        curationKeys = new ArrayList<>();
        hcEROs = new ArrayList<>();
        dirPaths = new ArrayList<>();

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
                        File jsonFile = new File(hcEROdir.getAbsolutePath() + "/hcERO.json");
                        JSONObject json = new JSONObject(FileUtils.readFile(jsonFile.getAbsolutePath()));

                        for(Object okey : json.keySet()) {
                            String key = (String) okey;
                            if(!curationKeys.contains(key)) {
                                curationKeys.add(key);
                            }
                        }

                        String hcero = FileUtils.readFile(hcEROdir.getAbsolutePath() + "/hcERO.txt");
                        curation.add(json);
                        hcEROs.add(hcero);
                        dirPaths.add(hcEROdir.getAbsolutePath());
                    } catch(Exception err) {

                    }

                }


            }
        }
    }
}
