/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.act.biointerpretation.sandbox;

import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author jca20n
 */
public class FindSugars {
    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        //Load all the reachables
        String data2 = FileUtils.readFile("Z:/Saurabh/reachables/r-2015-10-06.txt");
        if(data2==null || data2.isEmpty()) {
            data2 = FileUtils.readFile("/Users/jca20n/Documents/r-2015-10-06.txt");
        }
        Set<String[]> inchisNew = new HashSet<>();
        String[] lines = data2.split("\\r|\\r?\\n");
        for(String line : lines) {
            String[] tabs = line.split("\t");
            String[] entry = new String[2];
            entry[0] = tabs[2].trim();
            entry[1] = tabs[1].trim();
            inchisNew.add(entry);
        }

        //Score each one for 'sugar-ness'
        StringBuilder sb = new StringBuilder();

        FindSugars finder = new FindSugars();
        for(String[] achem : inchisNew) {
            String inchi = achem[0];
            try {
                double score = finder.score(inchi);
                sb.append(inchi);
                sb.append("\t");
                sb.append(achem[1]);
                sb.append("\t");
                String smiles = ChemAxonUtils.InchiToSmiles(inchi);
                sb.append(smiles);
                sb.append("\t");
                sb.append(score);
                sb.append("\n");

            } catch(Exception err) {
                System.out.println("\tunparsible -- " + inchi);
            }
        }
        FileUtils.writeFile(sb.toString(), "output/sugars_scored.txt");
//        System.out.println(new FindSugars().score("InChI=1S/C6H12O6/c7-1-3(9)5(11)6(12)4(10)2-8/h3,5-9,11-12H,1-2H2/t3-,5-,6+/m1/s1"));
    }

    private double score(String inchi) throws Exception {
        int Ccount = 0; //number of carbons
        int COHcount = 0;  //number of carbons with 4 attached, one of which is -O, -S, or -N, and the rest are C or H
        Molecule mol = MolImporter.importMol(inchi);
        mol.calcHybridization();

        for(int i=0; i<mol.getAtomCount(); i++) {
            MolAtom atom = mol.getAtom(i);
            if(!atom.getSymbol().equals("C")) {
                continue;
            }

            //If got here, we have a carbon atom
            Ccount ++;

            //Drop the atom if it isn't SP3
            int hyb = atom.getHybridizationState();
            if(hyb != MolAtom.HS_SP3) {
                continue;
            }

            //Drop the atom if it has unstated chirality
            if(mol.getChirality(i) == MoleculeGraph.PARITY_EITHER) {
                continue;
            }

            int NSOcount = 0;
            for(MolBond bond : atom.getBondArray()) {
                MolAtom attached = bond.getOtherAtom(atom);
                String symbol = attached.getSymbol();
                if(symbol.equals("O") || symbol.equals("S") || symbol.equals("N")) {
                    if(attached.getHybridizationState() == MolAtom.HS_SP3) {
                        NSOcount++;
                    }
                }
            }

            if(NSOcount == 1 || NSOcount == 2) {
                COHcount++;
            }
        }

        if(Ccount == 0) {
            return 0;
        }
        return 1.0*COHcount/(1.0*Ccount);
    }
}
