package com.act.biointerpretation;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

/**
 * Created by jca20n on 9/15/15.
 */
public class Sandbox {
    public static void main(String[] args) {
        String smiles = "C(CC)(Cl)(Br)C";
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        IndigoObject mol = indigo.loadMolecule(smiles);
        System.out.println(iinchi.getInchi(mol));
    }

    public static void convert() {
        String smiles = "S(ON=C(S[C@H]1[C@H](O)[C@@H](O)[C@H](O)[C@@H](CO)O1)CCCCCCCS(=O)C)(O)(=O)=O";
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
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
