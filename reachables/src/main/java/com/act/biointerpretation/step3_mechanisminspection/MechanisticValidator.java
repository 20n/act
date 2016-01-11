package com.act.biointerpretation.step3_mechanisminspection;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.io.formats.mdl.MolImport;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.operators.ROProjecter;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;
import org.json.JSONObject;

import java.util.*;

/**
 * Created by jca20n on 1/6/16.
 */
public class MechanisticValidator {
    private List<ROEntry> ros;

    private Map<String, CofactorEntry> cos1;
    private Map<String, CofactorEntry> cos2;
    private Map<String, CofactorEntry> cos3;

    private ROProjecter projector;



    public static void main(String[] args) {
        ChemAxonUtils.license();

        MechanisticValidator mv = new MechanisticValidator();
        mv.initiate();

        //Test a reaction using isValid method
        //NAD+ + propanol >> NADPH + propanal
        Set<String> subs = new HashSet<>();
//        subs.add("InChI=1S/C3H8O/c1-2-3-4/h4H,2-3H2,1H3");
//        subs.add("InChI=1S/C21H27N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1-4,7-8,10-11,13-16,20-21,29-32H,5-6H2,(H5-,22,23,24,25,33,34,35,36,37)/p+1/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1");

        subs.add("InChI=1S/C21H32O2/c1-13(22)17-6-7-18-16-5-4-14-12-15(23)8-10-20(14,2)19(16)9-11-21(17,18)3/h12-13,16-19,22H,4-11H2,1-3H3/t13-,16+,17-,18+,19+,20+,21-/m1/s1");

        Set<String> prods = new HashSet<>();
//        prods.add("InChI=1S/C21H29N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1,3-4,7-8,10-11,13-16,20-21,29-32H,2,5-6H2,(H2,23,33)(H,34,35)(H,36,37)(H2,22,24,25)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1");
//        prods.add("InChI=1S/C3H6O/c1-2-3-4/h3H,2H2,1H3");
        prods.add("InChI=1S/C21H30O2/c1-13(22)17-6-7-18-16-5-4-14-12-15(23)8-10-20(14,2)19(16)9-11-21(17,18)3/h12,16-19H,4-11H2,1-3H3/t16-,17+,18-,19-,20-,21+/m0/s1");
        prods.add("InChI=1S/C21H29N7O17P3/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(44-46(33,34)35)14(30)11(43-21)6-41-48(38,39)45-47(36,37)40-5-10-13(29)15(31)20(42-10)27-3-1-2-9(4-27)18(23)32/h1-4,7-8,10-11,13-16,20-21,29-31H,5-6H2,(H7-,22,23,24,25,32,33,34,35,36,37,38,39)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1");

        int result = mv.isValid(subs, prods);


        System.out.println("done " + result);
    }

    public void initiate() {
        projector = new ROProjecter();

        //Pull the cofactor list
        cos1 = new HashMap<>();
        cos2 = new HashMap<>();
        cos3 = new HashMap<>();
        String codata = FileUtils.readFile("data/MechanisticCleaner/2015_12_21-Cofactors.txt");
        codata = codata.replaceAll("\"", "");
        String[] lines = codata.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            String[] tabs = lines[i].split("\t");
            CofactorEntry entry = new CofactorEntry();
            String inchi = tabs[0];
            entry.name = tabs[1];
            entry.set = tabs[2];
            entry.rank = Integer.parseInt(tabs[3]);

            if(entry.rank==1) {
                cos1.put(inchi, entry);
            } else if(entry.rank==2) {
                cos2.put(inchi, entry);
            } else if(entry.rank==3) {
                cos3.put(inchi, entry);
            }
        }

        //Pull the RO list
        ros = new ArrayList<>();
        String rodata = FileUtils.readFile("data/MechanisticCleaner/2016_01_06-ROPruner_ro_list.txt");
        rodata = rodata.replaceAll("\"\"", "###");
        rodata = rodata.replaceAll("\"", "");
        lines = rodata.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            String[] tabs = lines[i].split("\t");
            ROEntry entry = new ROEntry();
            entry.category = tabs[0];
            entry.name = tabs[1];
            try {
                entry.ro = RxnMolecule.getReaction(MolImporter.importMol(tabs[2]));
            } catch (MolFormatException e) {
                System.out.println(tabs[2]);
            }
            ;
            entry.istrim = Boolean.parseBoolean(tabs[3]);
            entry.autotrim = Boolean.parseBoolean(tabs[4]);
            entry.dbvalidated = Boolean.parseBoolean(tabs[5]);
            entry.count = Integer.parseInt(tabs[6]);
            try {
                String json = tabs[7];
                json = json.replaceAll("###", "\"");
                entry.json = new JSONObject(json);
                entry.validation = entry.json.getBoolean("validation");
            } catch(Exception err) {
                System.out.println(tabs[7]);
            }
            ros.add(entry);
        }
    }

    private class CofactorEntry {
        String set;
        String name;
        int rank;
    }

    private class ROEntry {
        String category;
        String name;
        RxnMolecule ro;
        Boolean istrim;
        Boolean autotrim;
        Boolean dbvalidated;
        Boolean validation;
        int count;
        JSONObject json;
    }

    /**
     *
     *Returns:
     *
     * 5:  valid with a curated RO (dbvalidated == true)
     * 4:  valid with a "perfect" RO  (category == perfect)
     * 3:  valid with an valid RO  (validation == true)
     * 2:  valid with an unvalidated RO (validation == null)
     * 0:  no match
     * -1:  generated an error
     * -2:  cofactors cannot be abstracted without eliminating all reactants/products
     * -3:  match to an invalid RO (validation == false)
     * -4:  missing substrate or product
     * -5:  FAKE inchis
     * -6:  product and substrate arrays are identical, ie, no reaction occurred
     *
     * @param subInchis
     * @param prodInchis
     * @return
     */
    public int isValid(Set<String> subInchis, Set<String> prodInchis) {
        try {
            if(subInchis.isEmpty() || prodInchis.isEmpty()) {
                return -4;
            }

            //If anything contains FAKE, return -5
            for(String inchi : subInchis) {
                if(inchi.contains("FAKE")) {
                    return -5;
                }
                if(ChemAxonUtils.InchiToSmiles(inchi)==null) {
                    System.err.println("inchi error");
                    return -1;
                }
            }
            for(String inchi : prodInchis) {
                if(inchi.contains("FAKE")) {
                    return -5;
                }
                if(ChemAxonUtils.InchiToSmiles(inchi)==null) {
                    System.err.println("inchi error");
                    return -1;
                }
            }

            //If any inchis appear on both sides (ie, a coenzyme), remove them
            Set<String> tossers = new HashSet<>();
            for(String inchi : subInchis) {
                if(prodInchis.contains(inchi)) {
                    tossers.add(inchi);
                }
            }
            for(String tossme : tossers) {
                subInchis.remove(tossme);
                prodInchis.remove(tossme);
            }

            //If either array is now empty, this is a dud
            if(subInchis.isEmpty() || prodInchis.isEmpty()) {
                return -6;
            }

            //Pull out cofactors
            Set<String> subCos = pullCofactors(subInchis);
            Set<String> prodCos = pullCofactors(prodInchis);

            System.out.println(subCos);
            System.out.println(prodCos);

            //Package up the substrates
            Molecule[] substrates = new Molecule[subInchis.size()];

            int index = 0;
            for(String inchi : subInchis) {
                Molecule molecule = null;
                try {
                    molecule = MolImporter.importMol(inchi);
                } catch(chemaxon.formats.MolFormatException err) {
                    System.err.println("InChi import error");
                    return -1;
                }
                substrates[index] = molecule;
                index++;
            }

            //Scan through the ROs in order and project, first that works is "success"
            ROEntry success = null;
            outer: for (ROEntry entry : ros) {
                try {
                    if(entry.name.equals("alcohol_beta_elimination_dehydration_to_alkene")) {
                        System.out.println();
                    }
                    //Project this RO
                    List<Set<String>> projection = projector.project(entry.ro, substrates);

                    //If gets here then some reaction successfully applied, but usually the wrong reaction, so check
                    Set<String> simpleProdInchis = simplify(prodInchis);
                    for(Set<String> products : projection) {
                        Set<String> simpleProducts = simplify(products);
                        if(simpleProducts.equals(simpleProdInchis)) {
                            success = entry;
                            break outer;
                        }
                    }
                } catch(Exception err) {}
            }

            //Determine how to score the output
            if (success != null) {
                if(success.dbvalidated == true) {
                    return 5;
                }
                if(success.category.equals("perfect")) {
                    return 4;
                }
                if(success.validation == true) {
                    return 3;
                }
                if(success.validation == null) {
                    return 2;
                }
                if(success.validation == false) {
                    return -3;
                }
            } else {
                return 0;
            }
        } catch(Exception err) {
            err.printStackTrace();
            return -1;
        }
        return 100000;
    }

    private Set<String> simplify(Set<String> products) throws Exception {
        Set<String> out = new HashSet<>();
        for(String inchi : products) {
            Molecule amol = MolImporter.importMol(inchi);
            for(int i=0; i<amol.getAtomCount(); i++) {
                amol.setChirality(i, 0);
            }
            out.add(ChemAxonUtils.toInchi(amol));
        }
        return out;
    }

    /**
     * Removes cofactors until only one remains, and returns whatever is pulled out
     * @param inchis
     * @return
     */
    public Set<String> pullCofactors(Set<String> inchis) {
        Set<String> namesOut = new HashSet<>();
        outer: while(true) {
            if(inchis.size() < 2) {
                break outer;
            }

            String found = null;

            //Do all highest priority cos1 ros first
            for(String inchi : inchis) {
                if(cos1.containsKey(inchi)) {
                    found = inchi;
                    break;
                }
            }
            if(found!=null) {
                inchis.remove(found);
                String name = cos1.get(found).name;
                namesOut.add(name);
                continue outer;
            }

            //Then cos2
            for(String inchi : inchis) {
                if(cos2.containsKey(inchi)) {
                    found = inchi;
                    break;
                }
            }
            if(found!=null) {
                inchis.remove(found);
                String name = cos2.get(found).name;
                namesOut.add(name);
                continue outer;
            }

            //Then cos3
            for(String inchi : inchis) {
                if(cos3.containsKey(inchi)) {
                    found = inchi;
                    break;
                }
            }
            if(found!=null) {
                inchis.remove(found);
                String name = cos3.get(found).name;
                namesOut.add(name);
                continue outer;
            }
            break outer;
        }
        return namesOut;
    }
}
