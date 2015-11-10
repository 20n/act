package com.act.biointerpretation.stereochemistry;

import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
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
        String smiles = "CC(C)CONC(C)C";
        Molecule target = MolImporter.importMol(smiles);
        String smarts = "CC(C)CONC(C)C";
        SubstructureMatcher matcher = new SubstructureMatcher();
        int[][] hits = matcher.matchVague(target, smarts);
        matcher.printHits(hits, target);
    }



    public int[][] matchVague(Molecule target, String smarts) throws Exception {
        //From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options
        MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
        searchOptions.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4 );
        MolSearch searcher = new MolSearch();
        searcher.setSearchOptions(searchOptions);

        // queryMode = true forces string to be imported as SMARTS
        // If SMILES import needed, set queryMode = false.
        MolHandler mh1 = new MolHandler(smarts, true);

        Molecule query = mh1.getMolecule();
        searcher.setQuery(query);

        //Import the target chemical
        searcher.setTarget(target);

        // search all matching substructures
        int[][] hits = searcher.findAll();

        return hits;
    }

    public int[][] match(Molecule target, String smarts) throws Exception {
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
        target.aromatize(true);
        searcher.setTarget(target);

        // search all matching substructures
        int[][] hits = searcher.findAll();

        return hits;
    }

    public int[][] matchVague(String inchi, String smarts) throws Exception {
        Molecule target = MolImporter.importMol(inchi);
        return matchVague(target, smarts);
    }

    public void printHits(int[][] hits, Molecule target) {
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
