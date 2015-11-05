package com.act.biointerpretation;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Created by jca20n on 10/29/15.
 */
public class ChemAxonUtils {
    public static void main(String[] args) throws Exception {
//        String smiles = InchiToSmiles("InChI=1S/C2H6O/c1-2-3/h3H,2H2,1H3");
//        System.out.println(smiles);
        String inchi = SmilesToInchi("C[C@H](Cl)O");
        System.out.println(inchi);
    }

    public static String SmilesToInchi(String smiles) {
        try {
            Molecule mol = MolImporter.importMol(smiles);
            return MolExporter.exportToFormat(mol, "inchi:AuxNone,Woff");
        } catch(Exception err) {
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

    public static void saveImageOfReaction(Molecule mol, String filename) {
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
}
