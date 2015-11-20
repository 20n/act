package com.act.biointerpretation.cofactors;

import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Created by jca20n on 11/9/15.
 */

public class SimpleReaction implements Serializable {
    private static final long serialVersionUID = -6151894164438402271L;

    public Set<String> subCofactors;
    public Set<String> prodCofactors;
    public Molecule substrate;
    public Molecule product;

    List<ChemicalInfo> substrateInfo;
    List<ChemicalInfo> productInfo;

    public RxnMolecule getRxnMolecule() throws Exception {
        String subSmiles = ChemAxonUtils.toSmiles(substrate);
        String prodSmiles = ChemAxonUtils.toSmiles(product);
        String smilesRxn = subSmiles + ">>" + prodSmiles;
        return RxnMolecule.getReaction(MolImporter.importMol(smilesRxn));
    }
}