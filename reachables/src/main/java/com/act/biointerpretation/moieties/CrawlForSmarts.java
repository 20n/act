package com.act.biointerpretation.moieties;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.stereochemistry.SubstructureMatcher;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.io.File;
import java.util.*;

/**
 * Searches the synapse database for products of reactions that contain a smarts, and dumps the rxn info
 * Created by jca20n on 12/9/15.
 */
public class CrawlForSmarts {
    private NoSQLAPI api;
//    private final String smarts = "CCCCCC";
    private final String smarts = "[#8]1-[#6]=[#6]-[#8]-[#6]-1([H])([H])";
    private SubstructureMatcher matcher;

    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        CrawlForSmarts abstractor = new CrawlForSmarts();
        abstractor.initiate();
        abstractor.run();
        abstractor.printout();
    }

    public void printout() throws Exception {
        StringBuilder moieties = new StringBuilder();
        StringBuilder chemIds = new StringBuilder();
    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        matcher = new SubstructureMatcher();
    }

    public void run() {
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Reaction rxn = iterator.next();
            Long[] rxnids = rxn.getProducts();
            for(Long id : rxnids) {

                try {
                    Chemical achem = api.readChemicalFromInKnowledgeGraph(id);
                    String inchi = achem.getInChI();
                    Molecule target = MolImporter.importMol(inchi);

                    //Extract the moieties
                    int[][] hits = matcher.matchVague(target, smarts);
                    if (hits != null) {
                        System.out.println("*************\nHit on:\nrxn:" + id + " \n" +  achem.getFirstName() + " \n" + inchi+ " \n" );
                        System.out.println(rxn.toStringDetail());
                        System.out.println("substrates:");
                        for(long chemid : rxn.getSubstrates()) {
                            Chemical ach = api.readChemicalFromInKnowledgeGraph(chemid);
                            System.out.println("\t" + ach.getFirstName() + "\t" + ach.getInChI());
                        }
                        System.out.println("products:");
                        for(long chemid : rxn.getProducts()) {
                            Chemical ach = api.readChemicalFromInKnowledgeGraph(chemid);
                            System.out.println("\t" + ach.getFirstName() + "\t" + ach.getInChI());
                        }
                    }
                } catch (Exception err) {
//                System.out.println("dud" + achem.getUuid());
                }
            }
        }
    }

}
