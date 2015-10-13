package com.act.biointerpretation.cleanchems;

import act.api.NoSQLAPI;
import act.server.Molecules.RxnTx;
import act.shared.Chemical;
import com.act.biointerpretation.FileUtils;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.*;

/**
 * Desalter tries to remove any ionization or secondary ions from an inchi.
 * To use, create an instance of Desalter then use the clean method
 * to convert one inchi to a desalted version.  One Desalter can be reused.
 *
 * TODO: Edge cases that remain to be handled are:  sulfites, multi-component organics, radioactive.
 * See Desalter_modified_alldb_checked for examples of errors that remain
 *
 * TODO:  Iterate through reactions and examine chemicals only in reactions to determine
 * if the multi-component stuff is also present in reaction data, or limited to drugs
 *
 * TODO:  Add as positive tests the 'ok' things in Desalter_modified_alldb_checked
 *
 * Created by jca20n on 10/6/15.
 */
public class Desalter {
    private Indigo indigo;
    private IndigoInchi iinchi;
    private List<DesaltRO> ros;

    private StringBuilder log = new StringBuilder();

    public static void main(String[] args) {
        Desalter cnc = new Desalter();
        try {
            cnc.test();
        } catch (Exception e) {
        }
        cnc.startFlow();
    }

    private class DesaltRO {
        String ro;
        String name;
        List<String> testInputs = new ArrayList<>();
        List<String> testOutputs = new ArrayList<>();
        List<String> testNames = new ArrayList<>();
    }

    /**
     * TODO: Replace with JUnit test or equivalent
     *
     * Iterates through all the ROs and their curated
     * tests, and confirms that the product inchi
     * matches the expected inchi
     */
    public void test() throws Exception {
        //Test all the things that should get cleaned for proper cleaning
        for(DesaltRO ro : ros) {
            String roSmarts = ro.ro;

            for(int i=0; i<ro.testInputs.size(); i++) {
                String input = ro.testInputs.get(i);
                String output = ro.testOutputs.get(i);
                String name = ro.testNames.get(i);
                System.out.println("Testing: " + name + "  " + input);

                String cleaned = null;
                try {
                    cleaned = this.clean(input);
                } catch(Exception err) {
                    System.out.println("!!!!error cleaning:" + cleaned + "  " + output);
                    log = new StringBuilder();
                    throw err;
                }

                try {
                    assertTrue(output.equals(cleaned));
                } catch(Exception err) {
                    System.out.println(log.toString());
                    log = new StringBuilder();
                    System.out.println("!!!!error cleaning test:" + cleaned + "  " + output);
                    throw err;
                }
                System.out.println("\n" + name + " is ok\n");
            }
        }

        //Check things that should not be cleaned for identity
        String data = FileUtils.readFile("data/desalter_constants.txt");
        String[] lines = data.split("\\r|\\r?\\n");
        for(String inchi : lines) {
            String cleaned = null;
            try {
                cleaned = this.clean(inchi);
            } catch(Exception err) {
                System.out.println("!!!!error cleaning constant test:" + cleaned + "  " + inchi);
                log = new StringBuilder();
                throw err;
            }

            try {
                assertTrue(inchi.equals(cleaned));
            } catch(Exception err) {
                System.out.println(log.toString());
                log = new StringBuilder();
                System.out.println("!!!!error cleaning constant: " + inchi);
                System.out.println("raw:" + InchiToSmiles(inchi));
                System.out.println("cleaned:" + InchiToSmiles(cleaned));
                throw err;
            }
        }
    }

    public void assertTrue(boolean isit) {
        if(isit == false) {
            throw new RuntimeException();
        }
    }

    /**
     * This method is used for testing Desalter
     * It pulls 10,000 salty inchis from the database,
     * then cleans them and sorts them as to whether
     * they fail, clean to the same inchi, or get modified
     */
    public void startFlow() {
        List<String> salties = getSaltyInchis();
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        for(int i=0; i<salties.size(); i++) {
            log = new StringBuilder();
            String salty = salties.get(i);
            //System.out.println("Working on " + i + ": " + salty);
            String saltySmile = null;
            try {
                saltySmile = InchiToSmiles(salty);
            } catch(Exception err) {
                errors.append("InchiToSmiles1\t" + salty + "\r\n");
                continue;
            }
            String cleaned = null;



            try {
                cleaned = clean(salty);
            } catch(Exception err) {
                errors.append("cleaned\t" + salty + "\r\n");
                System.out.println(log.toString());
                log = new StringBuilder();
                err.printStackTrace();
                continue;
            }
            String cleanSmile = null;
            try {
                cleanSmile = InchiToSmiles(cleaned);
            } catch(Exception err) {
                errors.append("InchiToSmiles2\t" + salty + "\r\n");
            }

            if (!salty.equals(cleaned)) {
                sb.append(salty + "\t" + cleaned + "\t" + saltySmile + "\t" + cleanSmile + "\r\n");
            } else {
                sb2.append(salty + "\t" + saltySmile + "\r\n");
            }
        }
        FileUtils.writeFile(sb.toString(), "data/Desalter_modified.txt");
        FileUtils.writeFile(sb2.toString(), "data/Desalter_unchanged.txt");
        FileUtils.writeFile(errors.toString(), "data/Desalter_errors.txt");
    }

    private List<String> getSaltyInchis() {
        List<String> out = new ArrayList<>();

        int count = 0;
        int limit = 10000;

        //First inspect all the chemicals
        NoSQLAPI api = new NoSQLAPI("lucille", "synapse");  //just reading lucille
        Iterator<Chemical> allChems = api.readChemsFromInKnowledgeGraph();
        while(allChems.hasNext()) {
            if(count > limit) {
                break;
            }
            try {
                Chemical achem = allChems.next();
                String inchi = achem.getInChI();

                if(inchi.contains("FAKE")) {
                    continue;
                }

                String chopped = inchi.substring(6); //Chop off the Inchi= bit
                if(chopped.contains(".") || chopped.contains("I") || chopped.contains("Cl") || chopped.contains("Br") || chopped.contains("Na") || chopped.contains("K") || chopped.contains("Ca") || chopped.contains("Mg") || chopped.contains("Fe") || chopped.contains("Mn") || chopped.contains("Mo") || chopped.contains("As") || chopped.contains("Mb") || chopped.contains("p-")  || chopped.contains("p+") || chopped.contains("q-")  || chopped.contains("q+")) {

                    try {
                        iinchi.loadMolecule(inchi);
                        out.add(inchi);
                        count++;
                    } catch (Exception err) {
                        continue;
                    }

                }
            } catch(Exception err) {
                System.err.println("Error inspecting chemicals");
                err.printStackTrace();
            }
        }

        return out;
    }

    public Desalter() {
        indigo = new Indigo();
        iinchi = new IndigoInchi(indigo);
        ros = new ArrayList<>();

        String data = FileUtils.readFile("data/desalting_ros.txt");
        data = data.replace("\"", "");
        String[] regions = data.split("###");
        for(String region : regions) {
            if(region==null || region.equals("")) {
                continue;
            }
            DesaltRO ro = new DesaltRO();
            String[] lines = region.split("(\\r|\\r?\\n)");
            ro.name = lines[0].trim();
            ro.ro = lines[1].trim();
            for(int i=2; i<lines.length; i++) {
                String line = lines[i];
                String[] tabs = line.split("\t");
                ro.testInputs.add(tabs[0].trim());
                ro.testOutputs.add(tabs[1].trim());
                try {
                    ro.testNames.add(tabs[2].trim());
                } catch(Exception err) {
                    ro.testNames.add("noname");
                }
            }
            ros.add(ro);
        }
    }

    private String clean(String inchi) throws Exception{
        String out = inchi;
        String inputInchi = null;

        //First try dividing the molecule up
        String smiles = InchiToSmiles(out);
        String[] pipes = smiles.split("\\|");
        if(pipes.length==2 || pipes.length==3) {
            smiles = pipes[0].trim();
        }
        if(pipes.length > 3) {
            log.append("pipes length off: " + pipes.length + "\t" + smiles + "\n");
            log.append("\n");
        }
        String[] splitted = smiles.split("\\.");
        List<String> mols = new ArrayList<>();
        for(String str : splitted) {
            mols.add(str);
        }

        try {
            out = resolveMixtureOfSmiles(mols);
        } catch(Exception err) {
            log.append("\n");
            log.append("Error resolving smiles: \" + smiles + \"\\t\" + inchi\n");
            out = inchi;  //If this fails, just revert to the original inchi, this only fails for 3 things of 10,000
        }

        //Then try all the ROs
        Set<String> seenBefore = new HashSet<>();
        Outer: while(!out.equals(inputInchi)) {
            //Check that it's not in a loop
            if(seenBefore.contains(inputInchi)) {
                log.append("Encountered a loop for\n");
                for(String str : seenBefore) {
                    log.append("\t" + str + "\n");
                }
                throw new Exception();
            } else {
                seenBefore.add(inputInchi);
            }

            inputInchi = out;

            for (DesaltRO ro : ros) {
                List<String> results = project(inputInchi, ro);
                if (results == null || results.isEmpty()) {
                    continue;
                }
                try {
                    out = resolveMixtureOfSmiles(results);
                    if(!out.equals(inputInchi)) {
                        continue Outer;
                    }
                } catch(Exception err) {
                    log.append("Error resolving smiles during projection loop: " + out + "\n");
                    out = inchi; //Abort any projections, very rare
                    break Outer;
                }
            }
        }

        log.append("cleaned:" + out + "\n");
        return out;
    }

    private String resolveMixtureOfSmiles(List<String> smiles) {
        String bestInchi = null;
        int bestCount = 0;
        for(String smile : smiles) {
            IndigoObject mol = null;
            try {
                mol = indigo.loadMolecule(smile);
            } catch(Exception err) {
                log.append("Error parsing split smile of: " + smile + "\n");
                throw err;
            }
            int count = mol.countHeavyAtoms();
//            log.append(smile + " Heavy: " + count);
            if(count > bestCount) {
                bestCount = count;
                bestInchi = iinchi.getInchi(mol);
            }
        }
        return bestInchi;
    }

    private List<String> project(String inchi, DesaltRO dro) {
        String ro = dro.ro;
        log.append("\n\tprojecting: " + dro.name + "\n");
        log.append("\tro :" + ro+ "\n");
        log.append("\tinchi :" + inchi+ "\n");


        String smiles = InchiToSmiles(inchi);
        log.append("\tsmiles :" + smiles + "\n");
        List<String> substrates = new ArrayList<>();
        substrates.add(smiles);


        //Do the projection of the ro
        Indigo indigo = new Indigo();
        try {
            List<List<String>> pdts = RxnTx.expandChemical2AllProducts(substrates, ro, indigo, new IndigoInchi(indigo));
            List<String> products = new ArrayList<>();
            for(List<String> listy : pdts) {
                for(String entry : listy) {
                    if(!products.contains(entry)) {
                        products.add(entry);
                        log.append("\t+result:" + entry + "\n");
                    }
                }
            }
            return products;
        } catch(Exception err) {
            log.append("\t-result: no projection\n");
        }

        return null;
    }

    public static String InchiToSmiles(String inchi) {
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        IndigoObject mol = iinchi.loadMolecule(inchi);
        return mol.canonicalSmiles();
    }

}
