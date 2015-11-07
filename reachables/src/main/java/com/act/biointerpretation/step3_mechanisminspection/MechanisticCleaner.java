package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.ChemAxonUtils;
import com.act.biointerpretation.FileUtils;
import com.act.biointerpretation.step3_stereochemistry.SplitReaction;

import java.util.*;

/**
 * Created by jca20n on 11/2/15.
 */
public class MechanisticCleaner {
    Map<String, String> operators;
    Set<String> cofactors;
    NoSQLAPI api;

    public static void main(String[] args) {
        SplitReaction.handleLicense();

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
        while(iterator.hasNext()) {
            try {
                Reaction rxn = iterator.next();
                List<Molecule> substrateInchis = extractAbstractMolecules(rxn.getSubstrates());
                List<Molecule> productInchis = extractAbstractMolecules(rxn.getProducts());

                if(substrateInchis.size() != 1) {
                    continue;
                }
                if(productInchis.size() != 1) {
                    continue;
                }

                String subsmiles = MolExporter.exportToFormat(substrateInchis.get(0), "smiles:a-H");
                String prodsmiles = MolExporter.exportToFormat(productInchis.get(0), "smiles:a-H");

                String subINchi = MolExporter.exportToFormat(substrateInchis.get(0), "inchi:AuxNone,Woff");
                String prodINchi = MolExporter.exportToFormat(productInchis.get(0), "inchi:AuxNone,Woff");
                if(subINchi.equals(prodINchi)) {
                    continue;
                }

                String reaction = subsmiles + ">>" + prodsmiles;
                System.out.println("reaction:  " + reaction);

                //Calculate the RO
                try {
                    RxnMolecule ro = new SkeletonMapper().map(reaction);

                    if(ro==null) {
                        System.out.println("Failed");
                        RxnMolecule original = RxnMolecule.getReaction(MolImporter.importMol(reaction));
                        ChemAxonUtils.saveSVGImage(original, "output/images/dud.svg");
                        continue;
                    }
                    System.out.println("      ro:  " + ROExtractor.printOutReaction(ro));

                    //Hash the RO and store in the map
                    String hash = ROExtractor.getReactionHash(ro);
                    System.out.println(hash);
                    System.out.println();
                    Set<Long> existing = observedROs.get(hash);
                    if(existing == null) {
                        existing = new HashSet<>();
                    }

                    Long along = Long.valueOf(rxn.getUUID());
                    existing.add(along);
                    observedROs.put(hash, existing);

                    ChemAxonUtils.saveSVGImage(ro, "output/images/rxn.svg");
                } catch(Exception err) {
                    err.printStackTrace();
                }
                System.out.println();
            } catch(Exception err) {

            }

        }
    }


    private List<Molecule> extractAbstractMolecules(Long[] chemIds) throws Exception {
        List<Molecule> out = new ArrayList<>();
        for(Long along : chemIds) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(along);


            String inchi = achem.getInChI();


            Molecule mol = null;
            mol = MolImporter.importMol(inchi);

            //Erase the chirality in the molecule
            for(int i=0; i<mol.getAtomCount(); i++) {
                mol.setChirality(i, 0);
            }

            out.add(mol);
        }
        return out;
    }
}
