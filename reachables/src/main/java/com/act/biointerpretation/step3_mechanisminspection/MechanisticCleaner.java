package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.operators.OperatorExtractor;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;
import com.act.biointerpretation.cofactors.MolViewer;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.operators.SkeletonMapper;

import java.util.*;

/**
 * Created by jca20n on 11/2/15.
 */
public class MechanisticCleaner {
    Map<String, String> operators;
    Set<String> cofactors;
    NoSQLAPI api;

    public static void main(String[] args) {
        ChemAxonUtils.license();

        MechanisticCleaner cleaner = new MechanisticCleaner();
        cleaner.initiate();
        cleaner.flowAllReactions();
    }

    public void initiate() {
        //Read in the bag of operators
        operators = new HashMap<>();
        String roData = FileUtils.readFile("data/MechanisticCleaner/oneToOnes.txt");
        String[] lines = roData.split("\\r|\\r?\\n");
        for(String aline : lines) {
            String[] tabs = aline.split("\t");
            operators.put(tabs[0].trim(), tabs[1].trim());
        }

        //Read in the cofactors
        cofactors = new HashSet<>();
        String coData = FileUtils.readFile("data/MechanisticCleaner/cofactors.txt");
        lines = coData.split("\\r|\\r?\\n");
        for(String aline : lines) {
            cofactors.add(aline.trim());
        }
    }

    public void flowAllReactions() {
        Map<String, Set<Long>> observedROs = new HashMap<>(); //For counting up instances of new ROs

        this.api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        SimpleReactionFactory simplifier = SimpleReactionFactory.generate(api);
        int count = 0;
        outer: while(iterator.hasNext()) {
            try {
                Reaction rxn = iterator.next();

                System.out.println("id:" + rxn.getUUID() + "\n");
                SimpleReaction srxn = simplifier.simplify(rxn);

                System.out.println("\nsubstrate cofactors:");
                for(String name : srxn.subCofactors) {
                    System.out.println("  " + name);
                }
                System.out.println("\nproduct cofactors:");
                for(String name : srxn.prodCofactors) {
                    System.out.println("  " + name);
                }
                System.out.println();

                String subsmiles = ChemAxonUtils.toSmiles(srxn.substrate);
                String prodsmiles = ChemAxonUtils.toSmiles(srxn.product);

                String subINchi = MolExporter.exportToFormat(srxn.substrate, "inchi:AuxNone,Woff");
                String prodINchi = MolExporter.exportToFormat(srxn.product, "inchi:AuxNone,Woff");
                if(subINchi.equals(prodINchi)) {
                    continue;
                }

                String reaction = subsmiles + ">>" + prodsmiles;
                System.out.println("reaction:  " + reaction);

                //Calculate the RO
                try {
                    RxnMolecule original = RxnMolecule.getReaction(MolImporter.importMol(reaction));
                    RxnMolecule mapped = new SkeletonMapper().map(original);
                    RxnMolecule ro = new OperatorExtractor().calc_hcCRO(mapped);

                    if(ro==null) {
                        System.out.println("Failed\n\n");
                        ChemAxonUtils.saveSVGImage(original, "output/images/dud.svg");
                        continue;
                    }

                    //Show the GUI for trigger cases
                    MolViewer.show(ro, srxn);
                    count++;
                    if(count > 100) {
                        break outer;
                    }

                    //Hash the RO and store in the map
//                    String hash = ChangeMapper.getReactionHash(ro);
//                    System.out.println(hash);
//                    System.out.println();
//                    Set<Long> existing = observedROs.get(hash);
//                    if(existing == null) {
//                        existing = new HashSet<>();
//                    }
//
//                    Long along = Long.valueOf(rxn.getUUID());
//                    existing.add(along);
//                    observedROs.put(hash, existing);

                    ChemAxonUtils.saveSVGImage(ro, "output/images/rxn.svg");

                    System.out.println("ok\n\n");
                } catch(Exception err) {
                    err.printStackTrace();
                }
                System.out.println();
            } catch(Exception err) {

            }
        }
    }
}
