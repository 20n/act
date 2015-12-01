package com.act.biointerpretation.utils;

import chemaxon.calculations.hydrogenize.Hydrogenize;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.standardizer.Standardizer;
import chemaxon.struc.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by jca20n on 10/29/15.
 */
public class ChemAxonUtils {
    public static void main(String[] args) throws Exception {
        license();
//        String smiles = InchiToSmiles("InChI=1S/C2H6O/c1-2-3/h3H,2H2,1H3");
//        System.out.println(smiles);
//        String inchi = SmilesToInchi("C[C@H](Cl)O");
//        System.out.println(inchi);

        String smarts = "[C:1]([H])([H])O>>[C:1]([H])([H])N";

        RxnMolecule rxn = RxnMolecule.getReaction(MolImporter.importMol(smarts));

        System.out.println(toSMARTS(rxn));
    }

    public static String SmilesToInchi(String smiles) {
        try {
            Molecule mol = MolImporter.importMol(smiles);
            return MolExporter.exportToFormat(mol, "inchi:AuxNone,Woff");
        } catch(Exception err) {
            return null;
        }
    }

    public static String toInchi(Molecule mol) {
        try {
            return MolExporter.exportToFormat(mol, "inchi:AuxNone,Woff");
        } catch (IOException e) {
            return null;
        }
    }


    public static String toSMARTS(RxnMolecule input) {
        try {
            Molecule mol = input.clone();
            Standardizer std = new Standardizer("removeexplicith");
            std.standardize(mol);

            return MolExporter.exportToFormat(mol, "smarts:as");
        } catch(Exception err) {
//            err.printStackTrace();
            return null;
        }
    }

    public static String toSmiles(Molecule mol) {
        try {
            return MolExporter.exportToFormat(mol, "smiles:a-H");
        } catch(Exception err) {
//            err.printStackTrace();
            return null;
        }
    }


    public static String toSmilesSimplify(Molecule input) {
        try {
            Molecule mol = input.clone();
            for(int i=0; i<mol.getAtomCount(); i++) {
                mol.getAtom(i).clear();
            }
            for(int b=0; b<mol.getBondCount(); b++) {
                mol.getBond(b).setType(1);
            }
            return MolExporter.exportToFormat(mol, "smiles:a-H");
        } catch(Exception err) {
            err.printStackTrace();
            return null;
        }
    }

    public static String InchiToSmiles(String inchi) {
        try {
            Molecule mol = MolImporter.importMol(inchi);
            return MolExporter.exportToFormat(mol, "smiles:a-H");
        } catch(Exception err) {
            return null;
        }
    }

    public static void saveSVGImage(Molecule mol, String filename) {
        //https://docs.chemaxon.com/display/FF/Image+Export+in+Marvin#ImageExportinMarvin-exportOptions
        try {
            byte[] graphics = MolExporter.exportToBinFormat(mol, "svg:w300,h150,amap");
            File gfile = new File(filename);
            FileOutputStream fos = null;
            fos = new FileOutputStream(gfile);
            fos.write(graphics);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void savePNGImage(Molecule mol, String filename) {
        //https://docs.chemaxon.com/display/FF/Image+Export+in+Marvin#ImageExportinMarvin-exportOptions
        try {
            byte[] graphics = MolExporter.exportToBinFormat(mol, "png:w900,h450,amap");
            File gfile = new File(filename);
            FileOutputStream fos = null;
            fos = new FileOutputStream(gfile);
            fos.write(graphics);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void license() {
            String licensepath = "licenses/license_PlatformIT.cxl";
            File afile = new File(licensepath);
            if(!afile.exists())

            {
                System.err.println("No license file, put a valid one in /licenses");
                return;
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

    public static RxnMolecule SmilesToRxnMolecule(String smilesRxn) {
        RxnMolecule reaction = null;
        try {
            reaction = RxnMolecule.getReaction(MolImporter.importMol(smilesRxn));
        } catch (MolFormatException e) {
        }
        return reaction;
    }

}
