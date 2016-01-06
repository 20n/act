package com.act.biointerpretation.operators;

import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.io.*;
import java.util.*;

/**
 * Created by jca20n on 12/22/15.
 */
public class TestSetCrossROs implements Serializable {
    private static final long serialVersionUID = -3241894164438402271L;

    List<RORecord> ros;  //"perfect" ros
    List<Integer> testset;
    ConsolidatedCurationPart2 cc;

    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        ConsolidatedCurationPart2 cc = new ConsolidatedCurationPart2();
        cc.initiate();
        List<Integer> testfiles = cc.generateTestSet();
        List<RORecord> perfectROs = cc.generatePerfectROs();

        TestSetCrossROs tests = new TestSetCrossROs(perfectROs, testfiles, cc);
        tests.run();
        tests.serialize("output/TestSetCrossROs.ser");

        System.out.println("done");
    }

    public TestSetCrossROs(List<RORecord> ros, List<Integer> testset, ConsolidatedCurationPart2 cc) {
        this.ros = ros;
        this.testset = testset;
        this.cc = cc;
    }

    public void run() {
        for(int i=0; i<testset.size(); i++) {
//        for(int i=0; i<100; i++) {
            int rxnId = testset.get(i);
            System.out.println("rxnId: " + rxnId);
            ReactionInterpretation rxn = cc.getRxn(rxnId);

            for(RORecord record : ros) {
                boolean keeper = false;
                try {
                    keeper = testOne(record.hcERO, rxn);
                } catch(Exception err) {
                    continue;
                }

                if(keeper) {
                    record.projectedRxnIds.add(rxnId);
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

    public void serialize(String filename) throws Exception {
        FileOutputStream fos = new FileOutputStream(filename);
        ObjectOutputStream out = new ObjectOutputStream(fos);
        out.writeObject(this);
        out.close();
        fos.close();
    }

    public static TestSetCrossROs deserialize(String filename) throws Exception {
        FileInputStream fis = new FileInputStream(filename);
        ObjectInputStream ois = new ObjectInputStream(fis);
        TestSetCrossROs out = (TestSetCrossROs) ois.readObject();
        ois.close();
        fis.close();
        return out;
    }
}
