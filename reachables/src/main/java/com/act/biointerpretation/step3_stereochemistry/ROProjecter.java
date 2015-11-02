package com.act.biointerpretation.step3_stereochemistry;

import chemaxon.formats.MolExporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.RxnMolecule;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.FileUtils;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
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

        new ROProjecter().project(ro, reactants);
    }

    private Set<String> project(String ro, String[] reactantSmiles) throws Exception {
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
        Molecule[] products = reactor.react();
        Set<String> inchisOut = new HashSet<>();
        for (Molecule product : products) {
            System.out.println(MolExporter.exportToFormat(product, "smiles:a-H"));
            String inchi = MolExporter.exportToFormat(product, "inchi:AuxNone,Woff");
            inchisOut.add(inchi);
        }

        return inchisOut;
    }

}