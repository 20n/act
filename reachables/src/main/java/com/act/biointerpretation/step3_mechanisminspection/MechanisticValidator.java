package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.common.swing.io.util.FileUtil;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.FakeCofactorFinder;
import com.act.biointerpretation.operators.ROProjecter;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;
import org.json.JSONObject;

import java.util.*;

/**
 * Created by jca20n on 1/11/16.
 */
public class MechanisticValidator {
    private List<ROEntry> ros;

    private Map<String, CofactorEntry> cos1;
    private Map<String, CofactorEntry> cos2;
    private Map<String, CofactorEntry> cos3;
    private ROProjecter projector;
    private NoSQLAPI api;
    private FakeCofactorFinder FAKEfinder = new FakeCofactorFinder();

    public static void main(String[] args) {
        ChemAxonUtils.license();

        //Iniialize the validator and db
        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");
        MechanisticValidator validator = new MechanisticValidator(api);
        validator.initiate();

        //Initialize the output data file and list of found ids
        Set<Integer> seenRxnIds = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        try {
            String data = FileUtils.readFile("output/MechanisticValidator_dbscan.txt");
            data = data.replaceAll("\"", "");
            sb.append(data);
            String[] lines = data.split("\\r|\\r?\\n");
            for(int i=0; i<lines.length; i++) {
                String[] tabs = lines[i].split("\t");
                int rxnid = Integer.parseInt(tabs[0]);
                seenRxnIds.add(rxnid);
            }
        } catch(Exception err) {}

        //Iterate over all reactions in db (after Merging and Desalting/Standardization)
        int count = 0;
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Reaction rxn = iterator.next();

            //Skip it if done this rxn before
            if(seenRxnIds.contains(rxn.getUUID())) {
                continue;
            }

            //Scan over all the ROs (stopping if hits a 4)
            Report report = validator.validate(rxn, 4);
            count++;
            seenRxnIds.add(rxn.getUUID());

            //Output results
            System.out.print("rxnId: " + rxn.getUUID() + " : " + report.score);
            sb.append(rxn.getUUID()).append("\t").append(report.score);

            if(report.score > -1) {
                System.out.print(",\t" + report.bestRO.name);
                sb.append("\t").append(report.bestRO.id).append("\t").append(report.bestRO.name);
            }

            System.out.println();
            sb.append("\n");

            if(count % 100 == 0) {
                FileUtils.writeFile(sb.toString(), "output/MechanisticValidator_dbscan.txt");
            }
        }

        //Save the data file
        FileUtils.writeFile(sb.toString(), "output/MechanisticValidator_dbscan.txt");
    }

    public MechanisticValidator(NoSQLAPI api) {
        this.api = api;
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
        String rodata = FileUtils.readFile("data/MechanisticCleaner/2015_01_16-ROPruner_hchERO_list.txt");
        rodata = rodata.replaceAll("\"\"", "###");
        rodata = rodata.replaceAll("\"", "");
        lines = rodata.split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            String[] tabs = lines[i].split("\t");
            ROEntry entry = new ROEntry();
            entry.id = Integer.parseInt(tabs[0]);
            entry.category = tabs[1];
            entry.name = tabs[2];
            try {
                entry.ro = RxnMolecule.getReaction(MolImporter.importMol(tabs[3]));
            } catch (MolFormatException e) {
                System.out.println(tabs[3]);
            }
            ;
            entry.istrim = Boolean.parseBoolean(tabs[4]);
            entry.autotrim = Boolean.parseBoolean(tabs[5]);
            entry.dbvalidated = Boolean.parseBoolean(tabs[6]);
            entry.count = Integer.parseInt(tabs[7]);
            try {
                String json = tabs[8];
                json = json.replaceAll("###", "\"");
                entry.json = new JSONObject(json);
                entry.validation = entry.json.getBoolean("validation");
            } catch(Exception err) {
                System.out.println(tabs[8]);
            }
            ros.add(entry);
        }
    }

    public Report validateOne(Reaction rxn, RxnMolecule ro) {
        Report report = new Report();
        ROEntry entry = new ROEntry();
        entry.name = "test_entry";
        entry.ro = ro;
        entry.dbvalidated = false;
        entry.category = "";
        entry.validation = true;

        try {
            //Remove any cofactors
            preProcess(rxn, report);

            //Reformat the substrates to what ChemAxon needs
            Molecule[] substrates = packageSubstrates(report);

            //Remove the stereochemistry of the products for matching
            Set<String> simpleProdInchis = simplify(report.prodInchis, report);

            //Apply the ROs
            report.score = applyRO(entry, substrates, simpleProdInchis, report);
            System.out.println("rxnId " + rxn.getUUID() + "  " + report.score);
        } catch(Exception err) {
//            err.printStackTrace();
        }

        return report;

    }

    public Report validate(Reaction rxn, int limit) {
        Report report = new Report();

        try {
            //Remove any cofactors
            preProcess(rxn, report);

            //Reformat the substrates to what ChemAxon needs
            Molecule[] substrates = packageSubstrates(report);

            //Remove the stereochemistry of the products for matching
            Set<String> simpleProdInchis = simplify(report.prodInchis, report);

            //Apply the ROs
            report.score = -1;
            report.bestRO = null;
            for (ROEntry entry : ros) {
                try {
                    int score = applyRO(entry, substrates, simpleProdInchis, report);
                    if(score > -1) {
                        report.passingROs.add(entry);
                    }
                    if(score > report.score) {
                        report.score = score;
                        report.bestRO = entry;
                    }
                    if(score > limit) {
                        return report;
                    }
                } catch (Exception err) {}
            }

        } catch(Exception err) {
            report.log.add("Aborted validate");
        }
        return report;
    }

    private Molecule[] packageSubstrates(Report report) throws Exception {
        //Package up the substrates
        Molecule[] substrates = new Molecule[report.subInchis.size()];

        int index = 0;
        for(String inchi : report.subInchis) {
            Molecule molecule = null;
            try {
                molecule = MolImporter.importMol(inchi);
                for(int i=0; i<molecule.getAtomCount(); i++) {
//                    molecule.setChirality(i,0);
                }
            } catch(Exception err) {
                report.log.add("InChi import error");
                throw err;
            }
            substrates[index] = molecule;
            index++;
        }
        return substrates;
    }

    private int applyRO(ROEntry roentry, Molecule[] substrates, Set<String> simpleProdInchis, Report report) throws Exception {
        String smiles = ChemAxonUtils.toSmiles(substrates[0]);
        List<Set<String>> projection = projector.project(roentry.ro, substrates);

//        for(String inchi : simpleProdInchis) {
//            System.out.println(inchi+ "   expect");
//        }
        //If gets here then some reaction successfully applied, but usually the wrong reaction, so check
        for(Set<String> products : projection) {
            Set<String> simpleProjected = simplify(products, report);
//            for(String inchi : simpleProjected) {
//                System.out.println(inchi+ "   projected");
//            }
            if(simpleProjected.equals(simpleProdInchis)) {
                report.log.add("RO passed: " + roentry.name);
                if(roentry.dbvalidated == true) {
                    return 5;
                }
                if(roentry.category.equals("perfect")) {
                    return 4;
                }
                if(roentry.validation == true) {
                    return 3;
                }
                if(roentry.validation == null) {
                    return 2;
                }
                if(roentry.validation == false) {
                    return 0;
                }
                else {
                    return 1;
                }
            }
        }
        return -1;
    }

    /**
     * Pulls the chemicals from the database and sorts them into inchis and cofactors in the Report
     * Logs any exceptions, then rolls back the error
     *
     * @param rxn
     * @param report
     * @throws Exception
     */
    private void preProcess(Reaction rxn, Report report) throws Exception {
        //Pull the Chemicals for the rxn and pull out any FAKE cofactors
        processChems(rxn.getSubstrates(), report, true);
        processChems(rxn.getProducts(), report, false);

        //If any inchis appear on both sides (ie, a coenzyme), remove them
        Set<String> tossers = new HashSet<>();
        for(String inchi : report.subInchis) {
            if(report.prodInchis.contains(inchi)) {
                tossers.add(inchi);
            }
        }
        for(String tossme : tossers) {
            report.subInchis.remove(tossme);
            report.prodInchis.remove(tossme);
            report.log.add("Removed inchi from both sides of rxn: " + tossme);
        }

        //If either array is now empty, this is a dud
        if(report.subInchis.isEmpty() || report.prodInchis.isEmpty()) {
            report.log.add("There are either no substrates or no products");
            throw new Exception();
        }

        //Pull out any regular cofactors
        pullCofactors(report, true); //substrates
        pullCofactors(report, false); //products
    }

    private void processChems(Long[] chemIds, Report report, boolean issub) throws Exception {
        for(Long along : chemIds) {

            //Pull the chemical and its inchi
            Chemical achem = null;
            String inchi = null;
            try {
                achem = api.readChemicalFromInKnowledgeGraph(along);
                inchi = achem.getInChI();
            } catch(Exception err) {
                report.log.add("Failed pulling inchi for chemid: " + along);
                report.score = -9001;
                throw err;
            }

            //Check for a null or empty inchi
            if(inchi==null || inchi.isEmpty()) {
                report.log.add("Chemid is null or empty: " + along);
                report.score = -9002;
                throw new Exception();
            }

            //See if the inchi is FAKE
            if(inchi.contains("FAKE")) {
                String term = FAKEfinder.scan(achem);

                //If the FAKE inchi is a cofactor, put it into the cofactors
                if(term != null) {
                    if (issub) {
                        report.subCofactors.add(term);
                        report.subInchis.remove(inchi);
                    } else {
                        report.prodCofactors.add(term);
                        report.prodInchis.remove(inchi);
                    }
                    report.log.add("Identified FAKE cofactor: " + term);
                    continue;
                }

                //Otherwise this is an abort situation; the FAKE inchi cannot be resolved
                else {
                    report.log.add("FAKE inchi not a cofactor for chemId: " + along);
                    report.score = -9003;
                    throw new Exception();
                }
            }

            //If got this far, put the inchi in the inchi list
            if (issub) {
                report.subInchis.add(inchi);
            } else {
                report.prodInchis.add(inchi);
            }
        }
    }

    private Set<String> simplify(Set<String> chems, Report report) throws Exception {
        Set<String> out = new HashSet<>();
        for(String inchi : chems) {
            try {
                Molecule amol = MolImporter.importMol(inchi);
                out.add(MolExporter.exportToFormat(amol, "inchi:AuxNone,Woff,SNon,DoNotAddH"));
            } catch(Exception err) {
                report.log.add("Error simplifying inchis to remove chirality: " + inchi);
                report.score = -9004;
                throw err;
            }
        }
        return out;
    }

    public void pullCofactors(Report report, boolean isSub) throws Exception {
        try {
            //Set the source inchis
            Set<String> inchis = null;
            Set<String> namesOut = null;
            if (isSub) {
                inchis = report.subInchis;
                namesOut = report.subCofactors;
            } else {
                inchis = report.prodInchis;
                namesOut = report.prodCofactors;
            }

            outer:
            while (true) {
                if (inchis.size() < 2) {
                    break outer;
                }

                String found = null;

                //Do all highest priority cos1 ros first
                for (String inchi : inchis) {
                    if (cos1.containsKey(inchi)) {
                        found = inchi;
                        break;
                    }
                }
                if (found != null) {
                    inchis.remove(found);
                    String name = cos1.get(found).name;
                    namesOut.add(name);
                    continue outer;
                }

                //Then cos2
                for (String inchi : inchis) {
                    if (cos2.containsKey(inchi)) {
                        found = inchi;
                        break;
                    }
                }
                if (found != null) {
                    inchis.remove(found);
                    String name = cos2.get(found).name;
                    namesOut.add(name);
                    continue outer;
                }

                //Then cos3
                for (String inchi : inchis) {
                    if (cos3.containsKey(inchi)) {
                        found = inchi;
                        break;
                    }
                }
                if (found != null) {
                    inchis.remove(found);
                    String name = cos3.get(found).name;
                    namesOut.add(name);
                    continue outer;
                }
                break outer;
            }
        } catch(Exception err) {
            String msg = "Error pulling out cofactors on ";
            if(isSub) {
                msg+="substrates";
            } else {
                msg+="products";
            }
            report.log.add(msg);
            report.score = -9005;
            throw err;
        }
    }


    public static class CofactorEntry {
        public String set;
        public String name;
        public int rank;
    }

    public static class ROEntry {
        public int id;
        public String category;
        public String name;
        public RxnMolecule ro;
        public Boolean istrim;
        public Boolean autotrim;
        public Boolean dbvalidated;
        public Boolean validation;
        public int count;
        public JSONObject json;
    }

    public static class Report {
        public List<String> log = new ArrayList();
        public List<ROEntry> passingROs = new ArrayList<>();
        public int score = -9999;
        public Set<String> subCofactors = new HashSet<>();
        public Set<String> prodCofactors = new HashSet<>();
        public Set<String> subInchis = new HashSet<>();
        public Set<String> prodInchis = new HashSet<>();
        public ROEntry bestRO;
    }

}
