package com.act.biointerpretation.moieties;

import chemaxon.common.util.Pair;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jca20n on 12/10/15.
 */
public class MakeSugarPatterns {

    Map<String,String> sugars;

    public static void main(String[] args) throws Exception {
        MakeSugarPatterns maker = new MakeSugarPatterns();

        String sugar = "b-D-Glucopyranose";
        int from = 1;
        int to = 6;
        Molecule result = maker.make(from, to, sugar);

        System.out.println(ChemAxonUtils.toInchi(result));
        System.out.println("(" + from + "-->" + to + ")" + sugar + "\t" + ChemAxonUtils.toSmiles(result));
    }

    public MakeSugarPatterns() {
        sugars = new HashMap<>();
        sugars.put("a-D-Glucopyranose", "[CH2:6]([C@@H:5]1[C@H:4]([C@@H:3]([C@H:2]([C@H:1](O1)O)O)O)O)O");
        sugars.put("b-D-Glucopyranose", "[CH2:6]([C@@H:5]1[C@H:4]([C@@H:3]([C@H:2]([C@@H:1](O1)O)O)O)O)O");
    }

    public Molecule make(int from, int to, String sugar) throws Exception {
        String smiles = sugars.get(sugar);
        Molecule mol = MolImporter.importMol(smiles);

        //Put the fromAtom into the Molecule unnattached
        MolAtom fromAtom = new MolAtom(6);
        mol.add(fromAtom);

        //Find the oxygen linked to the to position
        MolAtom toAtom = null;
        for(MolAtom atom : mol.getAtomArray()) {
            if(atom.getAtomMap() == to) {
                for(MolBond bond : atom.getBondArray()) {
                    MolAtom nei = bond.getOtherAtom(atom);
                    if(nei.getSymbol().equals("H")) {
                        continue;
                    }
                    if(nei.getSymbol().equals("C")) {
                        continue;
                    }
                    toAtom = nei;
                    break;

                }
            }
        }
        MolBond bond = new MolBond(fromAtom, toAtom);
        mol.add(bond);

        return mol;
    }
}
