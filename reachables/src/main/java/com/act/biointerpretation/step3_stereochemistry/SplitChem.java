package com.act.biointerpretation.step3_stereochemistry;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * Created by jca20n on 10/26/15.
 */
public class SplitChem {
    String inchiBase;
    Chirality[] stereos;

    public enum Chirality {
        r, s, u
    }

    private SplitChem(String inchiBase, Chirality[] stereos) {
        this.inchiBase = inchiBase;
        this.stereos = stereos;
    }

    public static SplitChem generate(SplitChem duplicate) {
        return new SplitChem(duplicate.inchiBase, duplicate.stereos);
    }

    public static SplitChem generate(String concreteInchi) {
        try {
            Molecule mol = MolImporter.importMol(concreteInchi);
            int atomcount = mol.getAtomCount();

            //Pull out the stereos
            List<Chirality> stereoList = new ArrayList<>();
            for(int i=0; i<atomcount ; i++) {
                int chirality = mol.getChirality(i);
                if(chirality == 8) {
                    stereoList.add(Chirality.r);
                } else if(chirality == 16) {
                    stereoList.add(Chirality.s);
                } else if(chirality == 3) {
                    stereoList.add(Chirality.u);
                }
            }
            Chirality[] stereos = new Chirality[stereoList.size()];
            for(int i=0; i<stereoList.size(); i++) {
                stereos[i] = stereoList.get(i);
            }

            //Remove any stereochemistry in the inchi
            for(int i=0; i<atomcount ; i++) {
                mol.setChirality(i, 0);
            }

            String inchi = MolExporter.exportToFormat(mol, "inchi:AuxNone,Woff");
            return new SplitChem(inchi, stereos);
        } catch(Exception err) {
            err.printStackTrace();
        }
        return null;
    }

    public String getInchi() {
        try {
            Molecule mol = MolImporter.importMol(this.inchiBase);
            int index = 0;
            for(int i=0; i<mol.getAtomCount(); i++) {
                if(mol.getChirality(i) > 0) {
                    Chirality correctValue = this.stereos[index];
                    switch(correctValue) {
                        case r:
                            mol.setChirality(i, 8);
                            break;
                        case s:
                            mol.setChirality(i, 16);
                            break;
                    }
                    index++;
                }
            }
            return MolExporter.exportToFormat(mol, "inchi:AuxNone,Woff,SAbs");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String toString() {
        String out = "";
        out+= "inchiBase: " + this.inchiBase + "\n";
        out+= "stereos:" + "\n";
        for (int i = 0; i < this.stereos.length; i++) {
            out += i + " : " + this.stereos[i]+ "\n";
        }
        return out;
    }

    public static void main(String[] args) {
        String[] tests = new String[5];
        tests[0] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1-,2-/m1/s1"; //l tartrate
        tests[1] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1-,2-/m0/s1"; //d tartrate
        tests[2] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1-,2+";  //meso tartrate
        tests[3] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1?,2?";  //explicitly unstated
        tests[4] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)";  //left blank

        for (String inchi : tests) {
            SplitChem achem = SplitChem.generate(inchi);

            System.out.println("\nStarted with:");
            System.out.println(inchi);

            System.out.println("\nRepresented as:");
            System.out.println(achem.toString());

            System.out.println("\nReconstructed:");
            String reconstructed = achem.getInchi();
            System.out.println(reconstructed);

            System.out.println("worked? - " + inchi.equals(reconstructed));
        }
    }
}
