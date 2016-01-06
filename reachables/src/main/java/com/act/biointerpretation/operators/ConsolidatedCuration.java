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
 * Consolidates the JSON curation of hcEROs
 * outputs all the tags observed
 * outputs things that may be cofactors that missed the first pass of curation
 *
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

        FileUtils.writeFile(sb.toString(), "output/potentialCofactors.txt");

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
