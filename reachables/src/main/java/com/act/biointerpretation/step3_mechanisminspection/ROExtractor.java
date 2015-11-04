package com.act.biointerpretation.step3_mechanisminspection;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
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
//        String reaction = "OC(=O)CCC(=O)O>>COOCOOCC";  //A bad case, where atom topology is problem, but count is right
//        String reaction = "CCCCCC(O)C=CC1C2CC(OO2)C1CC=CCCCC(O)=O>>CCCCCC(O)C=CC1C(O)CC(=O)C1CC=CCCCC(O)=O"; //an interesting example, breaks O-O bond
//        String reaction = "CC(C)=CCC\\C(C)=C/COP(O)(=O)OP(O)(O)=O>>CC1(C)C2CCC1(C)C(C2)OP(O)(=O)OP(O)(O)=O"; //terpenoid cyclases give crazy ros, but still "works"
//        String reaction = "[O-]N(=O)=O>>[O-]N=O"; //Neighbors of orphan atoms also need to move
//        String reaction = "OC(=O)C1NCC(C=C)=C1>>CCCC1CNC(C1)C(O)=O";  //neighbors of orphan atoms also need to move
        String reaction = "OCC(OP(O)(O)=O)C(O)=O>>OC(COP(O)(O)=O)C(O)=O"; //Finds the wrong solution

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

        //Identify all atoms in the substrate that have bonds that have changed
        Set<Integer> binMovers = new HashSet<>();

            //Index out the substate's bonds
            Map<Integer,Set<Integer>>  subBinPositionTobinPositions = new HashMap<>();
            for(int i=0; i<sub.getBondCount(); i++) {
                MolBond bond = sub.getBond(i);
                int toIndex = bond.getAtom1().getAtomMap();
                int fromIndex = bond.getAtom2().getAtomMap();

                //Index in one direction of bond
                Set<Integer> existing = subBinPositionTobinPositions.get(toIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(fromIndex);
                subBinPositionTobinPositions.put(toIndex, existing);

                //Repeat in the other direction
                existing = subBinPositionTobinPositions.get(fromIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(toIndex);
                subBinPositionTobinPositions.put(fromIndex, existing);
            }

            //Index out the products's bonds
            Map<Integer,Set<Integer>>  prodBinPositionTobinPositions = new HashMap<>();
            for(int i=0; i<prod.getBondCount(); i++) {
                MolBond bond = prod.getBond(i);
                int toIndex = bond.getAtom1().getAtomMap();
                int fromIndex = bond.getAtom2().getAtomMap();

                //Index in one direction of bond
                Set<Integer> existing = prodBinPositionTobinPositions.get(toIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(fromIndex);
                prodBinPositionTobinPositions.put(toIndex, existing);

                //Repeat in the other direction
                existing = prodBinPositionTobinPositions.get(fromIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(toIndex);
                prodBinPositionTobinPositions.put(fromIndex, existing);
            }

            //For every bin in the sub index, see if it has any bonds not present in the product
            Set<Integer> allBins = new HashSet<>();
            allBins.addAll(binToSub.keySet());
            allBins.addAll(binToProd.keySet());
            for(int bin : allBins) {
                Set<Integer> subBonds = subBinPositionTobinPositions.get(bin);
                Set<Integer> prodBonds = prodBinPositionTobinPositions.get(bin);

                //For orphans, add all their neighbors; if both null, ignore this bin
                if(subBonds != null && prodBonds == null) {
                    binMovers.addAll(subBonds);
                    continue;
                } else if(subBonds == null && prodBonds != null) {
                    binMovers.addAll(prodBonds);
                    continue;
                } else if(subBonds == null && prodBonds == null) {
                    continue;
                }

                //Otherwise, both are non-null, so compare them and keep the diff
                Set<Integer> adders = new HashSet<>();
                adders.addAll(subBonds);
                adders.removeAll(prodBonds);
                binMovers.addAll(adders);

                adders = new HashSet<>();
                adders.addAll(prodBonds);
                adders.removeAll(subBonds);
                binMovers.addAll(adders);
            }

        //Remove all atoms that atom to atom match
        Set<MolAtom> subRemove = new HashSet<>();
        Set<MolAtom> prodRemove = new HashSet<>();
        for(int subIndex : atomToAtom.keySet()) {

            //Don't remove if this atom is one of the bond movers
            int binIndex = subToBin.get(subIndex);
            if(binMovers.contains(binIndex)) {
                continue;
            }

            //Otherwise, remove it
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
