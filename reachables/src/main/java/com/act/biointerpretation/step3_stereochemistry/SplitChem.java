package com.act.biointerpretation.step3_stereochemistry;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * Created by jca20n on 10/26/15.
 */
public class SplitChem {
    final String inchiBase;
    final Chirality[] stereos;

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
                //If this atom is achiral, don't bother calculating the rest
                if(mol.getChirality(i) == 0) {
                    continue;
                }

                /**
                 * These steps of making a copy and zeroing out the chirality of the other stereocenters is
                 * to guarantee that the chirality returned is not contextualized on the chirality at
                 * the other positions
                 */

                //Make a copy of the molecule, then set all the other stereocenters to unknown
                Molecule molCopy = mol.clone();
                for(int ind=0; ind<atomcount; ind++) {
                    if(ind==i) {
                        continue;
                    }
                    molCopy.setChirality(ind, 0);
                }

                //Calculate the chirality of the atom based on the otherwise racemic structure
                int chirality = molCopy.getChirality(i);

                //Extract the stereochemistry of each stereocenter, in order
                if(chirality == 8) {
                    stereoList.add(Chirality.r);
                } else if(chirality == 16) {
                    stereoList.add(Chirality.s);
                } else if(chirality == 3) {
                    stereoList.add(Chirality.u);
                }
            }

            //Reexpress the stereocenters as an array
            Chirality[] stereos = new Chirality[stereoList.size()];
            for(int i=0; i<stereoList.size(); i++) {
                stereos[i] = stereoList.get(i);
            }

            //Remove any stereochemistry in the inchi
            for(int i=0; i<atomcount ; i++) {
                mol.setChirality(i, 0);
            }
            String inchi = MolExporter.exportToFormat(mol, "inchi:AuxNone,Woff");

            //Create the object
            return new SplitChem(inchi, stereos);
        } catch(Exception err) {
            err.printStackTrace();
        }
        return null;
    }

    public boolean isMeso() {
            //Scan through each stereocenter and set just one stereocenter, put inchi in a Set
            Set<String> taggedInchis = new HashSet<>();
            int stereoCount = 0;
            try {
                Molecule mol = MolImporter.importMol(this.inchiBase);
                for(int i=0; i<mol.getAtomCount(); i++) {
                    Molecule molCopy = mol.clone();
                    if(mol.getChirality(i) == 0) {
                        continue;
                    }
                    molCopy.setChirality(i, 8);
                    String inchi = MolExporter.exportToFormat(molCopy, "inchi:AuxNone,Woff");
                    taggedInchis.add(inchi);
                    stereoCount++;
                    System.out.println(inchi);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            //See if any of those inchis collapsed, and if so, it's meso
            if(taggedInchis.size() < stereoCount) {
                return true;
            }

            return false;
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
}
