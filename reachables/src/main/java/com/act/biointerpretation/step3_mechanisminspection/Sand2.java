package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.ChemAxonUtils;
import com.act.biointerpretation.step3_stereochemistry.SplitReaction;
import com.act.biointerpretation.step3_stereochemistry.SubstructureMatcher;
import com.chemaxon.mapper.AutoMapper;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
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
        ChemAxonUtils.savePNGImage(ro, "output/images/sand2.png");
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
//            atom.setMassno(4+i);
        }

        //Map the substrate to the carbon skeleton
//        String backboneSmiles = MolExporter.exportToFormat(subC, "smiles");
        String backboneSmiles = "CCC[Xe]";
        String substrateSmiles = MolExporter.exportToFormat(substrate, "smiles");
        String ro = backboneSmiles + ">>" + substrateSmiles;
        RxnMolecule subToC = RxnMolecule.getReaction(MolImporter.importMol(ro));


        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        long start = new Date().getTime();
        String inchi = "InChI=1S/C8H6O4/c9-7(10)5-1-2-6(4-3-5)8(11)12/h1-4H,(H,9,10)(H,11,12)";
        String smarts = "OC=O";
        SubstructureMatcher searcher = new SubstructureMatcher();
        for(int i=0; i<100; i++) {

            searcher.match(inchi, smarts);
        }

        long end = new Date().getTime();
        long duration = (end-start);
        System.out.println(duration);


        mapper.map(subToC);
        return subToC;

    }
}
