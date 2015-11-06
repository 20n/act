package com.act.biointerpretation.step3_mechanisminspection;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.ChemAxonUtils;
import com.act.biointerpretation.step3_stereochemistry.SplitReaction;
import com.chemaxon.mapper.AutoMapper;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by jca20n on 11/5/15.
 */
public class Sand2 {
    public static void main(String[] args) throws Exception {
        SplitReaction.handleLicense();

        String reaction = "OCC(OP(O)(O)=O)C(O)=O>>OC(COP(O)(O)=O)C(O)=O"; //2-PG >> 3-PG


        RxnMolecule ro = bestMapping(reaction);
        System.out.println(ROExtractor.printOutReaction(ro));
        ChemAxonUtils.saveImageOfReaction(ro, "output/images/ro.svg");
    }

    private static RxnMolecule bestMapping(String smilesRxn) throws Exception {
        RxnMolecule reaction = RxnMolecule.getReaction(MolImporter.importMol(smilesRxn));
        AutoMapper mapper = new AutoMapper();

        Molecule substrate = reaction.getReactant(0);
        Molecule product = reaction.getProduct(0);

        //Remove all but carbons
        Molecule subC = substrate.clone();
        Set<MolAtom> tossers = new HashSet<>();
        for(int i=0; i<subC.getAtomCount(); i++) {
            MolAtom atom = subC.getAtom(i);
            if(atom.getAtno() != 6) {
                tossers.add(atom);
            }
        }
        for(MolAtom atom : tossers) {
            subC.removeAtom(atom);
        }

        //Reload to reset numbering
        String Cinchi = MolExporter.exportToFormat(subC, "inchi:AuxNone,Woff");
        subC = MolImporter.importMol(Cinchi);

        //Transfer the numbering to the mapping numbers
        for(int i=0; i<subC.getAtomCount(); i++) {
            MolAtom atom = subC.getAtom(i);
            atom.setAtomMap(i);
        }

        //Map the substrate to the carbon skeleton
        String backboneSmiles = MolExporter.exportToFormat(subC, "smiles");
        String substrateSmiles = MolExporter.exportToFormat(substrate, "smiles");
        String ro = backboneSmiles + ">>" + substrateSmiles;
        RxnMolecule subToC = RxnMolecule.getReaction(MolImporter.importMol(ro));

        mapper.map(subToC);
        return subToC;

    }
}
