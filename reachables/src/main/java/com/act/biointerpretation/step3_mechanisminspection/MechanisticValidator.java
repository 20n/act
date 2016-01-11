package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.FakeCofactorFinder;
import com.act.biointerpretation.operators.ROProjecter;
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
        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");
        MechanisticValidator validator = new MechanisticValidator(api);
        validator.initiate();
        Reaction rxn = api.readReactionFromInKnowledgeGraph(7l);
        validator.validate(rxn);
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

    public Report validateOne(Reaction rxn, RxnMolecule ro) {
        Report report = new Report();
        try {
            preProcess(rxn, report);

            //Apply the RO
            //TODO

        } catch(Exception err) {
            report.log.add("Aborted validate");
        }
        return report;
    }

    public Report validate(Reaction rxn) {
        Report report = new Report();

        try {
            preProcess(rxn, report);

            //Apply the ROs
            //TODO

        } catch(Exception err) {
            report.log.add("Aborted validate");
        }

        return report;
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
                throw err;
            }

            //Check for a null or empty inchi
            if(inchi==null || inchi.isEmpty()) {
                report.log.add("Chemid is null or empty: " + along);
                throw new Exception();
            }

            //See if the inchi is FAKE
            if(inchi.contains("FAKE")) {
                String term = FAKEfinder.scan(achem);

                //If the FAKE inchi is a cofactor, put it into the cofactors
                if(term != null) {
                    if (issub) {
                        report.subCofactors.add(term);
                    } else {
                        report.prodCofactors.add(term);
                    }
                    report.log.add("Identified FAKE cofactor: " + term);
                    continue;
                }

                //Otherwise this is an abort situation; the FAKE inchi cannot be resolved
                else {
                    report.log.add("FAKE inchi not a cofactor for chemId: " + along);
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
            throw err;
        }
    }


    static class CofactorEntry {
        String set;
        String name;
        int rank;
    }

    static class ROEntry {
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

    static class Report {
        List<String> log = new ArrayList();
        Set<String> subCofactors = new HashSet<>();
        Set<String> prodCofactors = new HashSet<>();
        Set<String> subInchis = new HashSet<>();
        Set<String> prodInchis = new HashSet<>();
    }

}
