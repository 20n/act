package com.act.biointerpretation.cofactors;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.ChemAxonUtils;
import com.act.biointerpretation.FileUtils;

import java.util.*;

/**
 * Created by jca20n on 11/7/15.
 */
public class ReactionSimplifier {

    private NoSQLAPI api;
    private Map<String, String> inchiToCofactorName;

    public static ReactionSimplifier generate(NoSQLAPI api) {
        String cofactorData = FileUtils.readFile("data/cofactor_data.txt");
        cofactorData = cofactorData.replace("\"", "");
        String[] lines = cofactorData.split("\\r|\\r?\\n");
        Map<String, String> cofs = new HashMap<>();
        for(String aline : lines) {
            String[] tabs = aline.trim().split("\t");
            cofs.put(tabs[0].trim(), tabs[1].trim());
        }
        ReactionSimplifier out = new ReactionSimplifier(cofs);
        out.api = api;
        return out;
    }

    private ReactionSimplifier(Map<String, String> cofactorInchis) {
        this.inchiToCofactorName = cofactorInchis;
    }

    public SimpleReaction simplify(Reaction rxn) throws Exception {
        SimpleReaction out = new SimpleReaction();
        out.subCofactors = new HashSet<>();
        out.prodCofactors = new HashSet<>();
        out.substrate = handle(rxn.getSubstrates(), out, true);
        out.product = handle(rxn.getProducts(), out, false);

        return out;
    }

    private Molecule handle(Long[] chems, SimpleReaction srxn, boolean isSubs) throws Exception {
        Set<String> cofactors = new HashSet<>();
        List<Molecule> nonCofactors = new ArrayList<>();
        List<ChemicalInfo> cheminfo = new ArrayList<>();

        //Scan through the chems and sort cofactors out
        for(Long along : chems) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(along);
            String inchi = achem.getInChI();
            String smiles = ChemAxonUtils.InchiToSmiles(inchi);
            String name = achem.getFirstName();

            //Create the ChemicalInfo
            ChemicalInfo info = new ChemicalInfo();
            info.id = Long.toString(along);
            info.name = name;
            info.inchi = inchi;
            info.smiles = smiles;
            cheminfo.add(info);

            //If it's a cofactor, pull it out
            if(inchiToCofactorName.containsKey(inchi)) {
                cofactors.add(inchiToCofactorName.get(inchi));
                continue;
            }

            if(inchi.contains("FAKE")) {
                throw new Exception();
            }

            Molecule mol = MolImporter.importMol(inchi);

            //Erase the chirality in the molecule
            for(int i=0; i<mol.getAtomCount(); i++) {
                mol.setChirality(i, 0);
            }

            nonCofactors.add(mol);
        }

        if(isSubs) {
            srxn.subCofactors = cofactors;
            srxn.substrateInfo = cheminfo;
        } else {
            srxn.prodCofactors = cofactors;
            srxn.productInfo = cheminfo;
        }

        if(nonCofactors.size() == 0) {
            return MolImporter.importMol("");
        } else if(nonCofactors.size() == 1) {
            return nonCofactors.get(0);
        } else {
            String multismiles = "";
            for(int i=0; i<nonCofactors.size(); i++) {
                Molecule amol = nonCofactors.get(i);
                if(cofactors.isEmpty()) {
                    System.out.println("   !!!! - " + ChemAxonUtils.toInchi(amol) + "    " + ChemAxonUtils.toSmiles(amol));
                }
                multismiles += ChemAxonUtils.toSmiles(amol);
                if(i!=nonCofactors.size()-1) {
                    multismiles+=".";
                }
            }
            Molecule compoundMol = MolImporter.importMol(multismiles);
            String compoundInchi = ChemAxonUtils.toInchi(compoundMol);
            return MolImporter.importMol(compoundInchi);
        }

    }

    public class ChemicalInfo {
        String id;
        String inchi;
        String smiles;
        String name;
    }

    public class SimpleReaction {
        public Set<String> subCofactors;
        public Set<String> prodCofactors;
        public Molecule substrate;
        public Molecule product;

        List<ChemicalInfo> substrateInfo;
        List<ChemicalInfo> productInfo;

        public RxnMolecule getRxnMolecule() throws Exception {
            String subSmiles = ChemAxonUtils.toSmiles(substrate);
            String prodSmiles = ChemAxonUtils.toSmiles(product);
            String smilesRxn = subSmiles + ">>" + prodSmiles;
            return RxnMolecule.getReaction(MolImporter.importMol(smilesRxn));
        }
    }
}
