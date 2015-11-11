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
        String reaction = "CCC(=O)NC>>CCC(=O)O"; //an easy case
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
//        String reaction = "O[1C][2C](OP(O)(O)=O)[3C](O)=O>>OC(COP(O)(O)=O)C(O)=O";

        RxnMolecule rxn = RxnMolecule.getReaction(MolImporter.importMol(reaction));
        RxnMolecule ro = new ROExtractor().calcCRO(rxn);
        System.out.println(printOutReaction(ro));
        ChemAxonUtils.saveSVGImage(ro, "output/images/ROExtractor.svg");
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

    public RxnMolecule calcCRO(RxnMolecule reaction) throws Exception {
        //Use ChemAxon's CHANGING option on AutoMapper to calculate an RO
        AutoMapper mapper = new AutoMapper();
        mapper.setMappingStyle(Mapper.MappingStyle.CHANGING);
        mapper.map(reaction);

        //Remove all the atoms that are label 0 (non-changing)
        Set<MolAtom> tossers = new HashSet<>();
        for(int i=0; i<reaction.getAtomCount(); i++) {
            MolAtom atom = reaction.getAtom(i);
            if(atom.getAtomMap() == 0) {
                tossers.add(atom);
            }
        }
        for(MolAtom atom : tossers) {
            reaction.removeAtom(atom);
        }

        return reaction;
    }
}
