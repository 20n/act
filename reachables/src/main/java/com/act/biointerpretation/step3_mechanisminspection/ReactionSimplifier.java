package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
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

        //Scan through the chems and sort cofactors out
        for(Long along : chems) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(along);
            String inchi = achem.getInChI();

            //If it's a cofactor, pull it out
            if(inchiToCofactorName.containsKey(inchi)) {
                cofactors.add(inchiToCofactorName.get(inchi));
                continue;
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
        } else {
            srxn.prodCofactors = cofactors;
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

    public class SimpleReaction {
        Set<String> subCofactors;
        Set<String> prodCofactors;
        Molecule substrate;
        Molecule product;
    }
}
