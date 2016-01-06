package com.act.biointerpretation.operators;

import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.io.File;
import java.util.*;

/**
 * Created by jca20n on 12/22/15.
 */
public class ROPruner {
    private List<RORecord> ros;
    private List<File> testset;

    public static void main(String[] args) {
        ChemAxonUtils.license();

        ConsolidatedCuration cc = new ConsolidatedCuration();
//        cc.initiate();
//        List<File> testfiles = cc.generateTestSet();
//        List<RORecord> perfectROs = cc.generatePerfectROs();
//
//        ROPruner pruner = new ROPruner(perfectROs, testfiles);
//        pruner.run();
//
//        System.out.println("done");
    }

    public ROPruner(List<RORecord> ros, List<File> testset) {
        this.ros = ros;
        this.testset = testset;
    }

    public void run() {
//        for(int i=0; i<testset.size(); i++) {
        for(int i=0; i<100; i++) {
            File afile = testset.get(i);
            String data = FileUtils.readFile(afile.getAbsolutePath());
            ReactionInterpretation rxn = ReactionInterpretation.parse(data);
            System.out.println(rxn.rxnId);

            for(RORecord record : ros) {
                boolean keeper = false;
                try {
                    keeper = testOne(record.hcERO, rxn);
                } catch(Exception err) {
                    continue;
                }

                if(keeper) {
                    record.projectedRxnIds.add(i);
                }
            }
        }
    }

    public boolean testOne(String ro, ReactionInterpretation rxn) throws Exception {
        ROProjecter projecter = new ROProjecter();

        RxnMolecule rxnmol = RxnMolecule.getReaction(MolImporter.importMol(rxn.mapping));
        for(MolAtom atom : rxnmol.getAtomArray()) {
            atom.setAtomMap(0);
        }
        Molecule[] substrates = rxnmol.getReactants();

        //Run the projection and tally up all the potential inchis
        List<Set<String>> projection = null;
        try {
            projection = projecter.project(ro, substrates);
        } catch(Exception err) {
            throw err;
        }
        Set<String> combinedInchis = new HashSet<>();
        for(Set<String> aproj : projection) {
            combinedInchis.addAll(aproj);
        }

        //For each product smiles, convert to inchi and see if it's in the projection
        for(Molecule aprod : rxnmol.getProducts()) {
            String inchi = ChemAxonUtils.toInchi(aprod);
            //If any product inchis are not accounted for, the RO does not apply
            if(combinedInchis.contains(inchi)) {
                return true;
            }
        }
        return false;
    }
}
