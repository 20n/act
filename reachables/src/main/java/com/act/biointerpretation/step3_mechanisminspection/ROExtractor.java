package com.act.biointerpretation.step3_mechanisminspection;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.ChemAxonUtils;
import com.act.biointerpretation.step3_stereochemistry.SplitReaction;
import com.chemaxon.mapper.AutoMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by jca20n on 11/2/15.
 */
public class ROExtractor {
    public static void main(String[] args) throws Exception {
        SplitReaction.handleLicense();

//        String reaction = "OCC1OC(OC2C(O)C(O)C(O)OC2CO)C(O)C(O)C1O>>OCC1OC(OC2OC(CO)C(O)C(O)C2O)C(O)C(O)C1O";
//        String reaction = "CCC(=O)NC>>CCC(=O)O"; //an easy case
//        String reaction = "CCFC(C)O>>COCNCBr"; //really non-plausible reaction keeps all atoms
        String reaction = "OC(=O)CCC(=O)O>>COOCOOCC";  //A bad case, where atom topology is problem, but count is right

        String ro = new ROExtractor().extract(reaction);
        System.out.println(ro);
    }

    public String extract(String smartsRxn) throws Exception {
        //Use ChemAxon to create an atom-to-atom mapping
        RxnMolecule reaction = RxnMolecule.getReaction(MolImporter.importMol(smartsRxn));
        AutoMapper mapper = new AutoMapper();
        mapper.map(reaction);

        Molecule sub = reaction.getReactant(0);
        Molecule prod = reaction.getProduct(0);

        //Put all the indices in maps
        Map<Integer,Integer> binToSub = new HashMap<>();
        Map<Integer,Integer> subToBin = new HashMap<>();

        Map<Integer,Integer> prodToBin = new HashMap<>();
        Map<Integer,Integer> binToProd = new HashMap<>();

            //Index the substrates
            for(int i=0; i<sub.getAtomCount(); i++) {
                //Hash the atom:  key is the index in the ChemAxon Molecule, the 'Bin' is the index in the atom-to-atom map
                MolAtom atom = sub.getAtom(i);
                int binIndex = atom.getAtomMap();
                subToBin.put(i, binIndex);
                binToSub.put(binIndex, i);
            }

            //Index the products
            for(int i=0; i<prod.getAtomCount(); i++) {
                //Hash the atom:  key is the index in the ChemAxon Molecule, the 'Bin' is the index in the atom-to-atom map
                MolAtom atom = prod.getAtom(i);
                int binIndex = atom.getAtomMap();
                binToProd.put(binIndex, i);
                prodToBin.put(i, binIndex);
            }

        //Sort the indices into sets of orphans and atomToAtom matches (all in atom indices in Molecule)
        Map<Integer, Integer> atomToAtom = new HashMap<>();
        Set<Integer> subOrphan = new HashSet<>();
        Set<Integer> prodOrphan = new HashSet<>();

            //Sort the substrates
            for(Integer subIndex : subToBin.keySet()) {
                int binIndex = subToBin.get(subIndex);
                Integer prodIndex = binToProd.get(binIndex);
                if(prodIndex == null) {
                    subOrphan.add(subIndex);
                } else {
                    atomToAtom.put(subIndex, prodIndex);
                }
            }

            //Sort the products
            for(Integer prodIndex : prodToBin.keySet()) {
                int binIndex = prodToBin.get(prodIndex);
                Integer subIndex = binToSub.get(binIndex);
                if(subIndex == null) {
                    prodOrphan.add(prodIndex);
                }
            }

        //Remove all atoms that atom to atom match
        Set<MolAtom> subRemove = new HashSet<>();
        Set<MolAtom> prodRemove = new HashSet<>();
        for(int subIndex : atomToAtom.keySet()) {
            int prodIndex = atomToAtom.get(subIndex);
            subRemove.add(sub.getAtom(subIndex));
            prodRemove.add(prod.getAtom(prodIndex));
        }

        for(MolAtom atom : subRemove) {
            sub.removeAtom(atom);
        }
        for(MolAtom atom : prodRemove) {
            prod.removeAtom(atom);
        }

        String subsmiles = MolExporter.exportToFormat(sub, "smiles");
        String prodsmiles = MolExporter.exportToFormat(prod, "smiles");
        return subsmiles + ">>" + prodsmiles;

    }
}
