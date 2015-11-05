package com.act.biointerpretation;

import act.server.Molecules.RxnTx;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jca20n on 9/15/15.
 */
public class Sandbox {
    public static void main(String[] args) {

    }

    public static void something1() {
        String smiles = "CC(Cl)O";

        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        IndigoObject mol = indigo.loadMolecule(smiles);
        IndigoObject atom = mol.getAtom(1);
        atom.setIsotope(2);

        System.out.println(atom.symbol());
        System.out.println(iinchi.getInchi(mol));
    }

    public static void aamExample() {
        String smiles = "[C@H](Cl)(Br)O";
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
//        indigo.setOption("inchi-options", "/SUU");

//        String ro = "CO>>COP";
        String ro = "[C:1]([Br:3])[O:4]>>[C:1]([Br:3])[O:4]C";
        IndigoObject rxn = indigo.loadReaction(ro);
//        rxn.automap("keep");
        for(IndigoObject mol : rxn.iterateMolecules()) {
            System.out.println("New mol starting:");
            for(IndigoObject atom : mol.iterateAtoms()) {
//                System.out.format("Atom %s %d %d\n", atom.symbol(), atom.index(), rxn.atomMappingNumber(atom));
            }
        }
        List<String> substrates = new ArrayList<>();
        substrates.add(smiles);


        //Do the projection of the ro
        List<String> products = new ArrayList<>();
        try {
            List<List<String>> pdts = RxnTx.expandChemical2AllProducts(substrates, ro, indigo, new IndigoInchi(indigo));
            for(List<String> listy : pdts) {
                for(String entry : listy) {
                    if(!products.contains(entry)) {
                        products.add(entry);
                    }
                }
            }
        } catch(Exception err) {
        }


        IndigoObject mol = indigo.loadMolecule(smiles);
        System.out.println("substrate:" + iinchi.getInchi(mol));
        mol.clearStereocenters();
        System.out.println("abstract: " + iinchi.getInchi(mol));
        for(String asmiles : products) {
            mol = indigo.loadMolecule(asmiles);
            System.out.println("product:  " + iinchi.getInchi(mol));
            mol.clearStereocenters();
            System.out.println("abstract: " + iinchi.getInchi(mol));
        }
    }
    public static void convert() {
        String smiles = "S(ON=C(S[C@H]1[C@H](O)[C@@H](O)[C@H](O)[C@@H](CO)O1)CCCCCCCS(=O)C)(O)(=O)=O";
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);

        iinchi.resetOptions();

        IndigoObject mol = indigo.loadMolecule(smiles);
        System.out.println(iinchi.getInchi(mol));
    }

    public static void jkalsdfkw() {
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        String inchi = "InChI=1S/C15H22N6O5S/c1-27(3-2-7(16)15(24)25)4-8-10(22)11(23)14(26-8)21-6-20-9-12(17)18-5-19-13(9)21/h5-8,10-11,14,22-23H,2-4,16H2,1H3,(H2-,17,18,19,24,25)/p+1/t7-,8+,10+,11+,14+,27?/m0/s1";

        indigo.setOption("standardize-charges", true);

        IndigoObject mol = iinchi.loadMolecule(inchi);
        String resultInchi = iinchi.getInchi(mol);
        compare(inchi, resultInchi);
    }

    public static void compare(String one, String two) {
        int shorter = Math.min(one.length(), two.length());
        System.out.println(one);
        for(int i=0; i<shorter; i++) {
            if (one.charAt(i) == two.charAt(i)) {
                System.out.print(" ");
            } else {
                System.out.print("|");
            }
        }
        System.out.print("\n");
        System.out.println(two);
    }

    public static void k2jl23j() {
        FileUtils.writeFile("", "blahblah.txt");
    }

    public static void junk0() {
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        IndigoObject mol = indigo.loadMolecule("CN(C=C/1)C=CC1=C2C=CN(C)C=C/2");

        System.out.println(iinchi.getInchi(mol));
    }
}
