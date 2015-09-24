package com.act.biointerpretation;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

/**
 * Created by jca20n on 9/15/15.
 */
public class Sandbox {
    public static void main(String[] args) {
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        IndigoObject mol = indigo.loadMolecule("CN(C=C/1)C=CC1=C2C=CN(C)C=C/2");

        System.out.println(iinchi.getInchi(mol));

    }
}
