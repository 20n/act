package com.act.biointerpretation.operators;

import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.stereochemistry.SubstructureMatcher;
import com.chemaxon.mapper.AutoMapper;
import com.chemaxon.mapper.Mapper;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by jca20n on 11/5/15.
 */
public class SkeletonMapper {
    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

//        String reaction = "OCC1OC(OC2C(O)C(O)C(O)OC2CO)C(O)C(O)C1O>>OCC1OC(OC2OC(CO)C(O)C(O)C2O)C(O)C(O)C1O";
//        String reaction = "CCC(=O)NC>>CCC(=O)O"; //an easy case, but fails bc losing a carbon
//        String reaction = "CCFC(C)O>>COCNCBr"; //really non-plausible reaction keeps all atoms
//        String reaction = "OC(=O)CCC(=O)O>>COOCOOCC";  //A bad case, where atom topology is problem, but count is right
//        String reaction = "CCCCCC(O)C=CC1C2CC(OO2)C1CC=CCCCC(O)=O>>CCCCCC(O)C=CC1C(O)CC(=O)C1CC=CCCCC(O)=O"; //an interesting example, breaks O-O bond
//        String reaction = "CC(C)=CCC\\C(C)=C/COP(O)(=O)OP(O)(O)=O>>CC1(C)C2CCC1(C)C(C2)OP(O)(=O)OP(O)(O)=O"; //terpenoid cyclases give crazy ros, but still "works"
//        String reaction = "[O-]N(=O)=O>>[O-]N=O"; //Neighbors of orphan atoms also need to move
//        String reaction = "OC(=O)C1NCC(C=C)=C1>>CCCC1CNC(C1)C(O)=O";  //neighbors of orphan atoms also need to move
//        String reaction = "CCCCC>>C=CCCC"; //propene to propane
//        String reaction = "NC1C(O)OC(COP(O)(O)=O)C(O)C1O>>NC1C(O)C(O)C(CO)OC1OP(O)(O)=O";  //Finds wrong solutions
//        String reaction = "OCC(OP(O)(O)=O)C(O)=O>>OC(COP(O)(O)=O)C(O)=O"; //2-PG >> 3-PG
//        String reaction = "CC1OC(O)C(O)C(O)C1O>>CC(O)C(O)C(O)C(=O)CO"; //Finds wrong solution
//        String reaction = "CCCCCCCC>>CCCCCCCCO";
        String reaction = "C=CCl>>C=CO"; //ex

        RxnMolecule rxn = RxnMolecule.getReaction(MolImporter.importMol(reaction));
        RxnMolecule ro = new SkeletonMapper().map(rxn);

        if(ro==null) {
            System.out.println("Failed");
            RxnMolecule original = RxnMolecule.getReaction(MolImporter.importMol(reaction));
            ChemAxonUtils.saveSVGImage(original, "output/images/dud.svg");
        } else {
            System.out.println("\nresult:\n" + ChangeMapper.printOutReaction(ro));
            ChemAxonUtils.saveSVGImage(ro, "output/images/skeletonmapper.svg");
        }
    }

    public RxnMolecule  map(RxnMolecule rxn) throws Exception {
        RxnMolecule reaction = rxn.clone();
        Molecule substrate = reaction.getReactant(0);
        Molecule product = reaction.getProduct(0);
//        System.out.println("subs: " + ChemAxonUtils.toSmiles(substrate));
//        System.out.println("prod: " + ChemAxonUtils.toSmiles(product));

        //Transfer the substrate coordinates to the atom map such that they are retained in the skeleton later
        Molecule Cskel = substrate.clone();
        for(int i=0; i<Cskel.getAtomCount(); i++) {
            MolAtom atom = Cskel.getAtom(i);
            atom.setAtomMap(i);
        }

//        1) Strip the substrate to just the carbon skeleton
        Set<MolAtom> tossers = new HashSet<>();
        for(int i=0; i<Cskel.getAtomCount(); i++) {
            MolAtom atom = Cskel.getAtom(i);
            if(atom.getAtno() != 6) {
                tossers.add(atom);
            }
        }
        for(MolAtom tossme : tossers) {
            Cskel.removeAtom(tossme);
        }

        String smarts = ChemAxonUtils.toSmilesSimplify(Cskel);
//        System.out.println("smarts: " + smarts);

//        2) MolSearch matchVague the carbon skeleton to the product
        SubstructureMatcher matcher = new SubstructureMatcher();
        int[][] hits = matcher.matchVague(product, smarts);
//        matcher.printHits(hits, product);

//        3) If it doesn't matchVague, then a rearrangement has occurred, and should do a different algorithm
//        3.5) If it returns more than one solution, then the substrate carbon skeleton is a substructure of the product beyond just one methyl group missing.
//        4) If it does matchVague, keep going
        if(hits==null || hits.length != 1) {
            return null;
        }

//        5) For each atom attached to the substrate's carbon skeleton, add that to the skeleton and matchVague the product. If it matches, keep it. Otherwise, remove it. This results in a carbon skeleton decorated with the adjacent atoms that exactly matchVague the product and omits any that have moved.
        //index out the atoms attached to carbons on the substrate
        Set<Integer> onCarbons = new HashSet<>();
        Set<Integer> carbons = new HashSet<>();

        //Index out the carbons
        for(int i=0; i<Cskel.getAtomCount(); i++) {
            MolAtom acarb = Cskel.getAtom(i);
            int subIndex = acarb.getAtomMap();
            carbons.add(subIndex);
        }

        //Index out the things attached to carbons (inefficient solution, but had trouble with losing indices)
        outer: for(int i=0; i<substrate.getAtomCount(); i++) {
            MolAtom atom = substrate.getAtom(i);
            MolAtom[] ligands = atom.getLigands();
            for(MolAtom ligand : ligands) {
                if(ligand.getAtno() == 6) {
                    onCarbons.add(i);
                    continue outer;
                }
            }
        }
        onCarbons.removeAll(carbons);

        //For each atom, see if it is consistent with substructure matching
        Molecule substruc = substrate.clone();;
        Set<Integer> keepers = new HashSet<>();
        for(int index : onCarbons) {
            substruc = substrate.clone();
            MolAtom atom = substruc.getAtom(index);
//            System.out.println(" * testing " + atom.getSymbol() + index);

            //Bin up all the atoms that should be removed
            tossers = new HashSet<>();
            for(int i=0; i<substruc.getAtomCount(); i++) {
                int tossIndex = i;
                MolAtom tosser = substruc.getAtom(tossIndex);
                if(tossIndex == index || keepers.contains(tossIndex) || carbons.contains(tossIndex)) {
//                    System.out.println("  - skipping " + tosser.getSymbol() + tossIndex);
                    continue;
                }
                tossers.add(tosser);
//                System.out.println("  + tossing " + tosser.getSymbol() + tossIndex);
            }

            //Remove those atoms
            for(MolAtom tosser : tossers) {
                substruc.removeAtom(tosser);
            }


            //See if that substructure matches the product
            String strucSmarts = ChemAxonUtils.toSmilesSimplify(substruc);
//            System.out.println("strucSmarts: " + strucSmarts);

            hits = matcher.matchVague(product, strucSmarts);
            if(hits==null || hits.length < 1) {
//                System.out.println("   - no match");
                continue;
            }

            //If it matched, keep that atom
//            System.out.println("   + match");
            keepers.add(index);
        }

        //Put all the keeper atoms into the final 'best' substructure
        tossers = new HashSet<>();
        substruc = substrate.clone();
        for(int i=0; i<substruc.getAtomCount(); i++) {
            MolAtom tosser = substruc.getAtom(i);
            if(keepers.contains(i) || carbons.contains(i)) {
                continue;
            }
            tossers.add(tosser);
        }
        for(MolAtom tosser : tossers) {
            substruc.removeAtom(tosser);
        }

//        System.out.println("substructure: " + ChemAxonUtils.toSmilesSimplify(substruc));

//        6) Fix the AAM of the decorated skeleton on the substrate and product
        //Clear any mappings present in substrate or product
        for(int i=0; i<reaction.getAtomCount(); i++) {
            reaction.getAtom(i).setAtomMap(0);
        }

        //Map the skeleton onto the substrate and transfer AAM
        String skeleton = ChemAxonUtils.toSmilesSimplify(substruc);
        hits = matcher.matchVague(substrate, skeleton);
        for(int i=0; i<hits[0].length; i++) {
            int subIndex = hits[0][i];
            substrate.getAtom(subIndex).setAtomMap(i);
        }
        hits = matcher.matchVague(product, skeleton);
        for(int i=0; i<hits[0].length; i++) {
            int prodIndex = hits[0][i];
            product.getAtom(prodIndex).setAtomMap(i);
        }

//        7) AutoMap the substrate and product to fill in numbering on remaining atoms
        AutoMapper mapper = new AutoMapper();
        mapper.setMappingStyle(Mapper.MappingStyle.MATCHING);
        mapper.setMarkBonds(true);
        mapper.map(reaction);

        return reaction;
    }
}
