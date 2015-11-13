package com.act.biointerpretation.cofactors;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.util.*;

/**
 * Created by jca20n on 11/7/15.
 */
public class SimpleReactionFactory {

    private NoSQLAPI api;
    private Map<String, String> inchiToCofactorName;
    private FakeCofactorFinder fakeFinder;

    public static SimpleReactionFactory generate(NoSQLAPI api) {
        String cofactorData = FileUtils.readFile("data/cofactor_data.txt");
        cofactorData = cofactorData.replace("\"", "");
        String[] lines = cofactorData.split("\\r|\\r?\\n");
        Map<String, String> cofs = new HashMap<>();
        for(String aline : lines) {
            String[] tabs = aline.trim().split("\t");
            cofs.put(tabs[0].trim(), tabs[1].trim());
        }
        SimpleReactionFactory out = new SimpleReactionFactory(cofs);
        out.fakeFinder = new FakeCofactorFinder();
        out.api = api;
        return out;
    }

    public List<String> getCofactorNames() {
        List<String> out = new ArrayList<>();
        for(String inchi : inchiToCofactorName.keySet()) {
            String name = inchiToCofactorName.get(inchi);
            out.add(name);
        }
        out.addAll(fakeFinder.getTerms());
        return out;
    }

    private SimpleReactionFactory(Map<String, String> cofactorInchis) {
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
                String term = fakeFinder.scan(achem);
                if(term==null) {
                    throw new Exception();
                }
                System.out.println("FAKE cofactor: " + term);
                cofactors.add(term);
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
//                if(cofactors.isEmpty()) {
//                    System.out.println("   !!!! - " + ChemAxonUtils.toInchi(amol) + "    " + ChemAxonUtils.toSmiles(amol));
//                }
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

}
