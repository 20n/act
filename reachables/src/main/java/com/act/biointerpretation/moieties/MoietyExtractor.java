package com.act.biointerpretation.moieties;

import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * I define moiety here as the first shell of atoms around a carbon skeleton
 * This class takes in a ChemAxon Molecule, and divides it into moieties
 * Created by jca20n on 12/9/15.
 */
public class MoietyExtractor {
    public static void main(String[] args) {
        MoietyExtractor extractor = new MoietyExtractor();
//        extractor.test1("InChI=1S/C6H13O9P/c7-1-2-3(8)4(9)5(10)6(14-2)15-16(11,12)13/h2-10H,1H2,(H2,11,12,13)/t2-,3-,4+,5-,6-/m1/s1","InChI=1S/C6H12O6/c7-1-2-3(8)4(9)5(10)6(11)12-2/h2-11H,1H2/t2-,3-,4+,5-,6+/m1/s1"); //glucose-1-phosphate
        extractor.test1("InChI=1S/C8H15NO6/c1-3(11)9-5-7(13)6(12)4(2-10)15-8(5)14/h4-8,10,12-14H,2H2,1H3,(H,9,11)/t4-,5-,6-,7-,8-/m1/s1", "");
    }

    private boolean test1(String inchiIn, String inchiOut) {
        try {
            Molecule mol = MolImporter.importMol(inchiIn);
            Set<Molecule> res = extract(mol);
            for(Molecule moiety : res) {
                System.out.println(ChemAxonUtils.toSMARTS(moiety));
                System.out.println(ChemAxonUtils.toInchi(moiety));
            }

        } catch(Exception err) {
        }
        return false;
    }

    public Set<Molecule> extract(Molecule mol) throws Exception {
        Set<Molecule> out = new HashSet<>();

        //Make a copy of the molecule with indices locked in atomMap
        Molecule molcopy = mol.clone();
        for(int i=0; i<molcopy.getAtomCount(); i++) {
            MolAtom atom = molcopy.getAtom(i);
            atom.setAtomMap(i+1);
        }

        //Reduce the molecule to just the carbond skeleton (likely disconnected)
        Molecule mergedSkeletons = extractSkeletons(molcopy);

        //Strip the skeletons into individual skeletons
        String smiles = ChemAxonUtils.toSmiles(mergedSkeletons);
        String[] skeletons = smiles.split("\\.");

        //For each skeleton, expand to include adjacent atoms and bonds
        for(String smile : skeletons) {
            System.out.println(smile);
            Molecule skel = MolImporter.importMol(smile);

            //Gather up all the indices of carbons in this moiety
            Set<Integer> carbonIndices = new HashSet<>();
            for(MolAtom acarb : skel.getAtomArray()) {
                int index = acarb.getAtomMap()-1;
                carbonIndices.add(index);
            }

            //Create a duplicate of the input molecule
            Molecule moiety = molcopy.clone();

            //Find all the carbons, bonds, and adjacent bonds in the moeity
            Set<MolAtom> keepAtoms = new HashSet<>();
            Set<MolBond> keepBonds = new HashSet<>();
            for(int i : carbonIndices) {
                MolAtom acarb = moiety.getAtom(i);
                keepAtoms.add(acarb);
                for(MolBond bond : acarb.getBondArray()) {
                    keepBonds.add(bond);
                    MolAtom attached = bond.getOtherAtom(acarb);
                    keepAtoms.add(attached);
                }
            }

            //Create tosser sets
            Set<MolBond> tossBonds = new HashSet<>();
            for(MolBond bond : moiety.getBondArray()) {
                if(!keepBonds.contains(bond)) {
                    tossBonds.add(bond);
                }
            }
            Set<MolAtom> tossAtoms = new HashSet<>();
            for(MolAtom atom : moiety.getAtomArray()) {
                if(!keepAtoms.contains(atom)) {
                    tossAtoms.add(atom);
                }
            }

            //Toss the tossers
            for(MolBond bond : tossBonds) {
                moiety.removeBond(bond);
            }
            for(MolAtom atom : tossAtoms) {
                moiety.removeAtom(atom);
            }

            out.add(moiety);
        }

        return out;
    }

    private Molecule extractSkeletons(Molecule mol) {
        Molecule out = mol.clone();
        Set<MolBond> keepBonds = new HashSet<>();
        Set<MolAtom> keepAtoms = new HashSet<>();

        //Gather up the carbon skeleton and all things attached to it
        for(int i=0; i<out.getAtomCount(); i++) {
            MolAtom atom = out.getAtom(i);

            //Toss if it's not carbon
            if(!atom.getSymbol().equals("C")) {
                continue;
            }

            //Keep all carbons
            keepAtoms.add(atom);

            //Keep all attached carbon bonds
            for(MolBond bond : atom.getBondArray()) {
                MolAtom attached = bond.getOtherAtom(atom);
                if(attached.getSymbol().equals("C")) {
                    keepBonds.add(bond);
                }
            }
        }

        //Create tosser sets
        Set<MolBond> tossBonds = new HashSet<>();
        for(MolBond bond : out.getBondArray()) {
            if(!keepBonds.contains(bond)) {
                tossBonds.add(bond);
            }
        }
        Set<MolAtom> tossAtoms = new HashSet<>();
        for(MolAtom atom : out.getAtomArray()) {
            if(!keepAtoms.contains(atom)) {
                tossAtoms.add(atom);
            }
        }

        //Toss the tossers
        for(MolBond bond : tossBonds) {
            out.removeBond(bond);
        }
        for(MolAtom atom : tossAtoms) {
            out.removeAtom(atom);
        }

        return out;
    }
}
