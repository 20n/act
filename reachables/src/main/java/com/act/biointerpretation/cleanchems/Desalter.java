package com.act.biointerpretation.cleanchems;

import act.api.NoSQLAPI;
import act.server.Molecules.RxnTx;
import act.shared.Chemical;
import com.act.biointerpretation.FileUtils;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by jca20n on 10/6/15.
 */
public class Desalter {
    private Indigo indigo;
    private IndigoInchi iinchi;
    private List<DesaltRO> ros;

    public static void main(String[] args) {
//        Desalter cnc = new Desalter();
//        List<String> inchis = cnc.getSaltyInchis();
//        for(String inchi : inchis) {
//            System.out.println(inchi);
//        }
        Desalter cnc = new Desalter();
        cnc.test();

    }

    private class DesaltRO {
        String ro;
        String name;
        List<String> testInputs = new ArrayList<>();
        List<String> testOutputs = new ArrayList<>();
        List<String> testNames = new ArrayList<>();
    }

    public void test() {
        for(DesaltRO ro : ros) {
            String roSmarts = ro.ro;

            for(int i=0; i<ro.testInputs.size(); i++) {
                String input = ro.testInputs.get(i);
                String output = ro.testOutputs.get(i);
                String name = ro.testNames.get(i);
                System.out.println("Testing: " + name + "  " + input);
                String cleaned = this.clean(input);

                assertTrue(output.equals(cleaned));
                System.out.println("\n" + name + " is ok\n");
            }
        }
    }

    public void assertTrue(boolean isit) {
        if(isit == false) {
            throw new RuntimeException();
        }
    }

    private void testOne(String inputInchi, String expectedInchi) {

        String result = clean(inputInchi);  //sodium benzoate

        assert(expectedInchi.equals(result));
        IndigoObject mol = iinchi.loadMolecule(inputInchi);
        System.out.println("\n\n-input: " + mol.canonicalSmiles());
        mol = iinchi.loadMolecule(result);
        System.out.println("-result: " + mol.canonicalSmiles());
        mol = iinchi.loadMolecule(expectedInchi);
        System.out.println("-expect: " + mol.canonicalSmiles());
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

                if(inchi.contains("Na") || inchi.contains("K") || inchi.contains("Ca") || inchi.contains("Mg") || inchi.contains("p-")  || inchi.contains("p+") || inchi.contains("q-")  || inchi.contains("q+")) {

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

    private String clean(String inputInchi) {
        String out = inputInchi;
        inputInchi = null;

        //First try dividing the molecule up
        String smiles = InchiToSmiles(out);
        String[] splitted = smiles.split("\\.");
        List<String> mols = new ArrayList<>();
        for(String str : splitted) {
            mols.add(str);
        }
        out = resolveMixtureOfSmiles(mols);

        //Then try all the ROs
        while(!out.equals(inputInchi)) {
            inputInchi = out;

            for (DesaltRO ro : ros) {
                List<String> results = project(inputInchi, ro);
                if (results == null || results.isEmpty()) {
                    continue;
                }
                out = resolveMixtureOfSmiles(results);
            }
        }

        System.out.println("cleaned:" + out);
        return out;
    }

    private String resolveMixtureOfSmiles(List<String> smiles) {
        String bestInchi = null;
        int bestCount = 0;
        for(String smile : smiles) {
            IndigoObject mol = indigo.loadMolecule(smile);
            int count = mol.countHeavyAtoms();
//            System.out.println(smile + " Heavy: " + count);
            if(count > bestCount) {
                bestCount = count;
                bestInchi = iinchi.getInchi(mol);
            }
        }
        return bestInchi;
    }

    private List<String> project(String inchi, DesaltRO dro) {
        String ro = dro.ro;
        System.out.println("\n\tprojecting: " + dro.name);
        System.out.println("\tro :" + ro);
        System.out.println("\tinchi :" + inchi);


        String smiles = InchiToSmiles(inchi);
        System.out.println("\tsmiles :" + smiles);
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
                        System.out.println("\t+result:" + entry);
                    }
                }
            }
            return products;
        } catch(Exception err) {
            System.out.println("\t-result: no projection");
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
