package com.act.biointerpretation.step3_stereochemistry;

import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.sss.search.MolSearch;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;
import com.act.biointerpretation.FileUtils;

import java.io.File;

/**
 * Created by jca20n on 11/2/15.
 */
public class SubstructureMatcher {
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
        String inchi = "InChI=1S/C8H6O4/c9-7(10)5-1-2-6(4-3-5)8(11)12/h1-4H,(H,9,10)(H,11,12)";
        String smarts = "OC=O";
        new SubstructureMatcher().match(inchi, smarts);
    }

    public void match(String inchi, String smarts) throws Exception {
        MolSearch searcher = new MolSearch();

        // queryMode = true forces string to be imported as SMARTS
        // If SMILES import needed, set queryMode = false.
        MolHandler mh1 = new MolHandler(smarts, true);

        // The query molecule must be aromatized if it uses the
        // alternating single/double bonds for the description of
        // aromaticity.
        mh1.aromatize();
        Molecule query = mh1.getMolecule();
        searcher.setQuery(query);

        //Import the target chemical
        Molecule target = MolImporter.importMol(inchi);
        target.aromatize(true);
        searcher.setTarget(target);

        // search all matching substructures
        int[][] hits = searcher.findAll();

        //Print out restuls
        if (hits == null)
            System.out.println("No hits");
        else {
            for (int i = 0; i < hits.length; i++) {
                System.out.println("Hit " + (i + 1) + ":  ");

                int[] hit = hits[i];
                for (int j = 0; j < hit.length; j++) {
                    System.out.print(hit[j] + " ");
                }
                System.out.println();
                for (int j = 0; j < hit.length; j++) {
                    int targetIndex = hit[j];
                    MolAtom atom = target.getAtom(targetIndex);
                    System.out.print(atom.getSymbol() + " ");
                }
                System.out.println();
            }
        }// end else
    }
}
