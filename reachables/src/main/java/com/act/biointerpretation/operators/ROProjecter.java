package com.act.biointerpretation.operators;

import chemaxon.formats.MolExporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.RxnMolecule;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Uses ChemAxon to do an RO projection
 *
 * Created by jca20n on 10/31/15.
 */
public class ROProjecter {
    static {
        String licensepath = "licenses/license_PlatformIT.cxl";
        File afile = new File(licensepath);
        if(!afile.exists()) {
            System.err.println("No license file, put a valid one in /licenses");
        }

        String lics = FileUtils.readFile(licensepath);
        // System.out.println(lics);
        // LicenseManager.setLicense(lics);
        try {
            LicenseManager.setLicenseFile(afile.getAbsolutePath());
        } catch (LicenseProcessingException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        String ro = "[F,Cl,Br,I:3][C:1]=O.[H:4][O:2][#6]>>[#6][O:2][C:1]=O.[F,Cl,Br,I:3][H:4]";

        String[] reactants = new String[2];
        reactants[0] = "FC(=O)C1=CC2=C(C=CC(CC(Br)=O)=C2)N(C(Cl)=O)C3=C1C=CC=C3";
        reactants[1] = "CC(=O)OCCN1CCN(CCO)CC1";

        List<Set<String>> pdts = new ROProjecter().project(ro, reactants);
        for(int i=0; i<pdts.size(); i++) {
            System.out.println("Rxn at site index " + i);
            Set<String> inchis = pdts.get(i);
            for(String inchi : inchis) {
                System.out.println("\t" + inchi);
            }
        }
    }

    /**
     * Calculates the products of applying a reaction operator (expressed as a SMARTS reaction)
     * on an array of test substrates (expressed as SMILES)
     *
     * Returns a List of product sets.  The size of the List corresponds to the number of reactive
     * sites on the test substrates.  Each Set entry in the List is the inchis of the products of
     * that projection.
     *
     * @param ro
     * @param reactantSmiles
     * @return
     * @throws Exception
     */
    public List<Set<String>> project(String ro, String[] reactantSmiles) throws Exception {
        // create Reactor
        Reactor reactor = new Reactor();

        // put in the reaction
        RxnMolecule reaction = RxnMolecule.getReaction(MolImporter.importMol(ro));
        reactor.setReaction(reaction);

        // set the substrate
        Molecule[] reactants = new Molecule[reactantSmiles.length];
        for(int i=0; i<reactantSmiles.length; i++) {
            String asmile = reactantSmiles[i];
            Molecule reactant = MolImporter.importMol(asmile);
            reactant.aromatize();
            reactants[i] = reactant;
        }
        reactor.setReactants(reactants);

        // get the results
        Molecule[] result;
        List<Set<String>> out = new ArrayList<>();
        while ((result = reactor.react()) != null) {
            Set<String> inchisOut = new HashSet<>();
            for (Molecule product : result) {
                String inchi = MolExporter.exportToFormat(product, "inchi:AuxNone,Woff");
                inchisOut.add(inchi);
            }
            out.add(inchisOut);
        };

        return out;
    }

}
