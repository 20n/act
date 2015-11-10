package com.act.biointerpretation.operators;

import chemaxon.common.util.Pair;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.stereochemistry.SplitReaction;
import com.chemaxon.mapper.AutoMapper;
import com.chemaxon.mapper.Mapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by jca20n on 11/2/15.
 */
public class ROExtractor {

    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

//        String reaction = "OCC1OC(OC2C(O)C(O)C(O)OC2CO)C(O)C(O)C1O>>OCC1OC(OC2OC(CO)C(O)C(O)C2O)C(O)C(O)C1O";
//        String reaction = "CCC(=O)NC>>CCC(=O)O"; //an easy case
//        String reaction = "CCFC(C)O>>COCNCBr"; //really non-plausible reaction keeps all atoms
//        String reaction = "OC(=O)CCC(=O)O>>COOCOOCC";  //A bad case, where atom topology is problem, but count is right
//        String reaction = "CCCCCC(O)C=CC1C2CC(OO2)C1CC=CCCCC(O)=O>>CCCCCC(O)C=CC1C(O)CC(=O)C1CC=CCCCC(O)=O"; //an interesting example, breaks O-O bond
//        String reaction = "CC(C)=CCC\\C(C)=C/COP(O)(=O)OP(O)(O)=O>>CC1(C)C2CCC1(C)C(C2)OP(O)(=O)OP(O)(O)=O"; //terpenoid cyclases give crazy ros, but still "works"
//        String reaction = "[O-]N(=O)=O>>[O-]N=O"; //Neighbors of orphan atoms also need to move
//        String reaction = "OC(=O)C1NCC(C=C)=C1>>CCCC1CNC(C1)C(O)=O";  //neighbors of orphan atoms also need to move
//        String reaction = "C=CCCC>>CCCCC"; //propene to propane
//        String reaction = "NC1C(O)OC(COP(O)(O)=O)C(O)C1O>>NC1C(O)C(O)C(CO)OC1OP(O)(O)=O";  //Finds wrong solutions
//        String reaction = "O[100C]C(OP(O)(O)=O)[13C](O)=O>>OC([100C]OP(O)(O)=O)[13C](O)=O"; //2-PG >> 3-PG
//        String reaction = "CC1OC(O)C(O)C(O)C1O>>CC(O)C(O)C(O)C(=O)CO"; //Finds wrong solution
//        String reaction = "CCCCCCCC>>CCCCCCCCO";
        String reaction = "O[1C][2C](OP(O)(O)=O)[3C](O)=O>>OC(COP(O)(O)=O)C(O)=O";

        RxnMolecule ro = new ROExtractor().bestMapping(reaction);
        System.out.println(printOutReaction(ro));
        ChemAxonUtils.saveSVGImage(ro, "output/images/ro.svg");
    }

    public static String printOutReaction(RxnMolecule rxn) throws Exception {
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<rxn.getReactantCount(); i++) {
            Molecule amol = rxn.getReactant(i);
            String smiles = MolExporter.exportToFormat(amol, "smiles");
            sb.append(smiles);
            if(i != rxn.getReactantCount() -1) {
                sb.append(".");
            }
        }
        sb.append(">>");
        for(int i=0; i<rxn.getProductCount(); i++) {
            Molecule amol = rxn.getProduct(i);
            String smiles = MolExporter.exportToFormat(amol, "smiles");
            sb.append(smiles);
            if(i != rxn.getProductCount() -1) {
                sb.append(".");
            }
        }
        return sb.toString();
    }

    public static String getReactionHash(RxnMolecule rxn) throws Exception {
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<rxn.getReactantCount(); i++) {
            Molecule amol = rxn.getReactant(i);
            String smiles = MolExporter.exportToFormat(amol, "smiles:a-H");
            sb.append(smiles);
            if(i != rxn.getReactantCount() -1) {
                sb.append(".");
            }
        }
        String reactInchi = ChemAxonUtils.SmilesToInchi(sb.toString());

        sb = new StringBuilder();
        for(int i=0; i<rxn.getProductCount(); i++) {
            Molecule amol = rxn.getProduct(i);
            String smiles = MolExporter.exportToFormat(amol, "smiles:a-H");
            sb.append(smiles);
            if(i != rxn.getProductCount() -1) {
                sb.append(".");
            }
        }
        String prodInchi = ChemAxonUtils.SmilesToInchi(sb.toString());
        return reactInchi + ">>" + prodInchi;
    }

    public RxnMolecule mapReaction(String smilesRxn) throws Exception {
        //Use ChemAxon's CHANGING option on AutoMapper to calculate an RO
        RxnMolecule reaction = RxnMolecule.getReaction(MolImporter.importMol(smilesRxn));
        AutoMapper mapper = new AutoMapper();
        mapper.setMappingStyle(Mapper.MappingStyle.CHANGING);
        mapper.map(reaction);
        return reaction;
    }

    public RxnMolecule bestMapping(String smilesRxn) throws Exception {
        RxnMolecule reaction = RxnMolecule.getReaction(MolImporter.importMol(smilesRxn));
        AutoMapper mapper = new AutoMapper();
//        mapper.setMappingStyle(Mapper.MappingStyle.CHANGING);

        //Generate an RO for C-C bonds only
        Set<String> allowed = new HashSet<>();
        allowed.add("6:6");

        RxnMolecule CCrxn = getOnly(reaction, allowed);

        mapper.map(CCrxn);


        return CCrxn;
    }

    private RxnMolecule getOnly(RxnMolecule rxn, Set<String> bondsAllowed) {
        RxnMolecule out = rxn.clone();
        restrictToAllowed(out.getReactant(0), bondsAllowed);
        restrictToAllowed(out.getProduct(0), bondsAllowed);
        ChemAxonUtils.saveSVGImage(out, "output/images/getOnly.svg");
        return out;
    }

    private void restrictToAllowed(Molecule mol, Set<String> bondsAllowed) {
        //Scan through the bonds and determine if its of allowed type
        Set<MolBond> tossers = new HashSet<>();
        Set<MolAtom> keepers = new HashSet<>();
        for(int i=0; i<mol.getBondCount(); i++) {
            MolBond bond = mol.getBond(i);
            MolAtom atom1 = bond.getAtom1();
            MolAtom atom2 = bond.getAtom2();
            Set<String> chars = new HashSet<>();
            int num1 = atom1.getAtno();
            int num2 = atom2.getAtno();

            String CC = Math.min(num1,num2) + ":" + Math.max(num1,num2);

            //If this bond's atom pairing is in the allowed bonds, add both atoms to the keepers
            if(!bondsAllowed.contains(CC)) {
                tossers.add(bond);
            } else {
                keepers.add(atom1);
                keepers.add(atom2);
            }
        }

        //Set the atom map of non-keeper atoms as 0
        for(int i=0; i<mol.getAtomCount(); i++) {
            MolAtom atom = mol.getAtom(i);

            if(keepers.contains(atom)) {
                continue;
            }
            atom.setAtomMap(0);
        }

//        //Break all the other bonds
//        for(MolBond bond : tossers) {
//            mol.removeBond(bond);
//        }

    }

    public RxnMolecule extract(String smilesRxn) throws Exception {
        //Use ChemAxon's CHANGING option on AutoMapper to calculate an RO
        RxnMolecule reaction = mapReaction(smilesRxn);

        //Gather up the atoms that received a zero in the mapping, for substrates
        Set<MolAtom> subRemove = new HashSet<>();
        for(int i=0; i<reaction.getReactant(0).getAtomCount(); i++) {
            MolAtom atom = reaction.getReactant(0).getAtom(i);
            if(atom.getAtomMap() != 0) {
                continue;
            }
            subRemove.add(atom);
        }

        //For products
        Set<MolAtom> prodRemove = new HashSet<>();
        for(int i=0; i<reaction.getProduct(0).getAtomCount(); i++) {
            MolAtom atom = reaction.getProduct(0).getAtom(i);
            if(atom.getAtomMap() != 0) {
                continue;
            }
            prodRemove.add(atom);
        }

        //Remove the tossers
        for(MolAtom atom : subRemove) {
            reaction.getReactant(0).removeAtom(atom);
        }
        for(MolAtom atom : prodRemove) {
            reaction.getProduct(0).removeAtom(atom);
        }
        return reaction;
    }

    public RxnMolecule extract_v0(String smartsRxn) throws Exception {
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
            Map<Integer,Set<Pair<Integer,Integer>>>  subBinPositionTobinPositions = new HashMap<>();  //Ints are bin index to set of <binIndex, bondType<
            for(int i=0; i<sub.getBondCount(); i++) {
                MolBond bond = sub.getBond(i);

                int bondtype = bond.getType();

                int toIndex = bond.getAtom1().getAtomMap();
                int fromIndex = bond.getAtom2().getAtomMap();

                //Index in one direction of bond
                Set<Pair<Integer,Integer>> existing = subBinPositionTobinPositions.get(toIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(new Pair<Integer,Integer>(fromIndex, bondtype));
                subBinPositionTobinPositions.put(toIndex, existing);

                //Repeat in the other direction
                existing = subBinPositionTobinPositions.get(fromIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(new Pair<Integer,Integer>(fromIndex, bondtype));
                subBinPositionTobinPositions.put(fromIndex, existing);
            }

            //Index out the products's bonds
            Map<Integer,Set<Pair<Integer,Integer>>>  prodBinPositionTobinPositions = new HashMap<>();
            for(int i=0; i<prod.getBondCount(); i++) {
                MolBond bond = prod.getBond(i);

                int bondtype = bond.getType();

                int toIndex = bond.getAtom1().getAtomMap();
                int fromIndex = bond.getAtom2().getAtomMap();

                //Index in one direction of bond
                Set<Pair<Integer,Integer>>  existing = prodBinPositionTobinPositions.get(toIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(new Pair<Integer,Integer>(fromIndex, bondtype));
                prodBinPositionTobinPositions.put(toIndex, existing);

                //Repeat in the other direction
                existing = prodBinPositionTobinPositions.get(fromIndex);
                if(existing == null) {
                    existing = new HashSet<>();
                }
                existing.add(new Pair<Integer,Integer>(fromIndex, bondtype));
                prodBinPositionTobinPositions.put(fromIndex, existing);
            }

            //For every bin in the sub index, see if it has any bonds not present in the product
            Set<Integer> allBins = new HashSet<>();
            allBins.addAll(binToSub.keySet());
            allBins.addAll(binToProd.keySet());
            for(int bin : allBins) {
                Set<Pair<Integer,Integer>> subBonds = subBinPositionTobinPositions.get(bin);
                Set<Pair<Integer,Integer>> prodBonds = prodBinPositionTobinPositions.get(bin);

                //For orphans, add all their neighbors; if both null, ignore this bin
                if(subBonds != null && prodBonds == null) {
                    for(Pair<Integer,Integer> apair : subBonds) {
                        binMovers.add(apair.left());
                        binMovers.add(bin);
                    }
                    continue;
                } else if(subBonds == null && prodBonds != null) {
                    for(Pair<Integer,Integer> apair : prodBonds) {
                        binMovers.add(apair.left());
                        binMovers.add(bin);
                    }
                    continue;
                } else if(subBonds == null && prodBonds == null) {
                    continue;
                }

                //Otherwise, both are non-null, so compare them and keep the diff, do the product
                Set<Pair<Integer,Integer>> adders = new HashSet<>();
                adders.addAll(subBonds);
                adders.removeAll(prodBonds);
                for(Pair<Integer,Integer> apair : adders) {
                    binMovers.add(apair.left());
                    binMovers.add(bin);
                }
                //Do the substrate
                adders = new HashSet<>();
                adders.addAll(prodBonds);
                adders.removeAll(subBonds);
                for(Pair<Integer,Integer> apair : adders) {
                    binMovers.add(apair.left());
                    binMovers.add(bin);
                }
            }

        //Remove all atoms that atom to atom matchVague
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

        return reaction;

    }
}
