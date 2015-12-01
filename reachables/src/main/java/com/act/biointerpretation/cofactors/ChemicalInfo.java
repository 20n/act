package com.act.biointerpretation.cofactors;

import java.io.Serializable;

/**
 * Created by jca20n on 11/9/15.
 */
public class ChemicalInfo implements Serializable {
    private static final long serialVersionUID = -2151894164438402271L;

    String id;
    String inchi;
    String smiles;
    String name;
}