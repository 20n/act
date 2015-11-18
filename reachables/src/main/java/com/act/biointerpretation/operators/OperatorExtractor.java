package com.act.biointerpretation.operators;

import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by jca20n on 11/16/15.
 */
public class OperatorExtractor {
    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

//        String reaction = "OCC(OP(O)(O)=O)C(O)=O>>OC(COP(O)(O)=O)C(O)=O"; //2-PG >> 3-PG
        String reaction = "CCCC=CC(=O)N>>CCCC=CC(=O)O"; //amide to acid
//        String reaction = "CCCc1ccccc1C(=O)N>>CCCc1ccccc1C(=O)O"; //amide to acid

        RxnMolecule rxn = RxnMolecule.getReaction(MolImporter.importMol(reaction));

        RxnMolecule mapped = new SkeletonMapper().map(rxn);
        ChemAxonUtils.saveSVGImage(mapped, "output/images/erocalc_mapped.svg");

//        RxnMolecule cro = new OperatorExtractor().calcCRO(mapped);
//        ChemAxonUtils.saveSVGImage(cro, "output/images/erocalc_cro.svg");

        RxnMolecule ero = new OperatorExtractor().calcERO(mapped);
        ChemAxonUtils.saveSVGImage(ero, "output/images/erocalc_ero.svg");
    }

    public RxnMolecule calcCRO(RxnMolecule mappedRxn) {
        RxnMolecule mapped = mappedRxn.clone();
        Set<MolBond> keepBonds = new HashSet<>();

        //Gather up the map indices of atoms that are reaction centers
        Set<Integer> rxnCenters = new HashSet<>();
        for(MolBond bond : mapped.getBondArray()) {
            if((bond.getFlags() & MolBond.REACTING_CENTER_MASK) == 0) {
                continue;
            }
            keepBonds.add(bond);
            MolAtom one = bond.getAtom1();
            rxnCenters.add(one.getAtomMap());
            MolAtom two = bond.getAtom2();
            rxnCenters.add(two.getAtomMap());
        }

        //Gather up all the atoms that aren't reaction centers
        Set<MolAtom>tossAtoms = new HashSet<>();
        for(int i=0; i<mapped.getAtomCount(); i++) {
            MolAtom atom = mapped.getAtom(i);
            if(!rxnCenters.contains(atom.getAtomMap())) {
                tossAtoms.add(atom);
            }
        }

        //Gather up all the unchanging bonds
        Set<MolBond>tossBonds = new HashSet<>();
        for(int i=0; i<mapped.getBondCount(); i++) {
            MolBond bond = mapped.getBond(i);
            if(!keepBonds.contains(bond)) {
                tossBonds.add(bond);
            }
        }

        //Remove the tossed bonds
        for(MolBond bond : tossBonds) {
            mapped.removeBond(bond);
        }

        //Remove tossed atoms
        for(MolAtom atom : tossAtoms) {
            mapped.removeAtom(atom);
        }

        return mapped;
    }

    /**
     * Inputs the RxnMolecule tagged with AAM on the CHANGING atoms
     * (ie, the CRO is labeled, and the remaining atoms are not)
     *
     * That mapping occurs from calling map from ChangeMapper or SkeletonMapper
     * @param mappedRxn
     * @return
     */
    public RxnMolecule calcERO(RxnMolecule mappedRxn) {
        RxnMolecule mapped = mappedRxn.clone();

        //Put the hybridization state on each atom
        mapped.calcHybridization();

        //Gather up the map indices of atoms that are reaction centers
        Set<MolBond> keepBonds = new HashSet<>();
        Set<Integer> rxnCenters = new HashSet<>();
        for(MolBond bond : mapped.getBondArray()) {
            if((bond.getFlags() & MolBond.REACTING_CENTER_MASK) == 0) {
                continue;
            }
            keepBonds.add(bond);
            MolAtom one = bond.getAtom1();
            rxnCenters.add(one.getAtomMap());
            MolAtom two = bond.getAtom2();
            rxnCenters.add(two.getAtomMap());
        }

        //Gather up all the atoms that aren't reaction centers
        Set<MolAtom> keepAtoms = new HashSet<>();
        Set<MolAtom> changers = new HashSet<>();
        for(int i=0; i<mapped.getAtomCount(); i++) {
            MolAtom atom = mapped.getAtom(i);
            if(rxnCenters.contains(atom.getAtomMap())) {
                keepAtoms.add(atom);
                changers.add(atom);
            }
        }

        //For each changer, add the sigma-attached bonds and atoms
        for(MolAtom croAtom : changers) {
            for(int i=0; i<croAtom.getBondCount(); i++) {
                MolBond bond = croAtom.getBond(i);
                keepBonds.add(bond);
                keepAtoms.add(bond.getOtherAtom(croAtom));
            }
        }

        //For each keepAtom, add anything in conjugation
        Set<MolAtom> workList = new HashSet<>();
        workList.addAll(keepAtoms);

        for(MolAtom keeper : workList) {
            int sp = keeper.getHybridizationState();
            if(sp == MolAtom.HS_SP2  || sp == MolAtom.HS_SP) {
                keepBonds.addAll(addConjugated(keeper, keepAtoms));
            }
        }

        //Gather up bonds that should be tossed
        Set<MolBond> tossBonds = new HashSet<>();
        for(int i=0; i<mapped.getBondCount(); i++) {
            MolBond bond = mapped.getBond(i);
            if(!keepBonds.contains(bond)) {
                tossBonds.add(bond);
            }
        }

        //Gather up atoms that should be tossed
        Set<MolAtom> tossAtoms = new HashSet<>();
        for(int i=0; i<mapped.getAtomCount(); i++) {
            MolAtom atom = mapped.getAtom(i);
            if(!keepAtoms.contains(atom)) {
                tossAtoms.add(atom);
            }
        }

        //Remove the tossed bonds
        for(MolBond bond : tossBonds) {
            mapped.removeBond(bond);
        }

        //Remove the tossed atoms
        for(MolAtom tossme : tossAtoms) {
            mapped.removeAtom(tossme);
        }

        return mapped;
    }

    public Set<MolBond> addConjugated(MolAtom croAtom, Set<MolAtom> keepers) {
        Set<MolBond> out = new HashSet<>();

        for(int i=0; i<croAtom.getBondCount(); i++) {
            MolBond bond = croAtom.getBond(i);
            MolAtom nei = bond.getOtherAtom(croAtom);

            int sp = nei.getHybridizationState();
            if(sp == MolAtom.HS_SP2  || sp == MolAtom.HS_SP) {
                out.add(bond);
                if(!keepers.contains(nei)) {
                    keepers.add(nei);
                    out.addAll(addConjugated(nei, keepers));
                }
            }
        }
        return out;
    }
}
