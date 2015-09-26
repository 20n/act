package com.act.biointerpretation.cleanchems;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

/**
 * Created by jca20n on 9/25/15.
 */
public class ChemInspecter {
    private NameInspecter nameInspecter;
    private InchiInspecter inchiInspecter;
    private StereochemistryInspecter stereochemistryInspecter;

    private Indigo indigo;
    private IndigoInchi iinchi;

    public ChemInspecter(NoSQLAPI api) {
        nameInspecter = new NameInspecter(api);
        inchiInspecter = new InchiInspecter(api);
        stereochemistryInspecter = new StereochemistryInspecter(api);

        indigo = new Indigo();
        iinchi = new IndigoInchi(indigo);
    }

    public void inspect(Chemical achem) {
        //Check if the inchi is "FAKE" or null or empty and route accordingly
        String inchi = achem.getInChI();

        if(inchi == null || inchi.isEmpty()) {
            System.err.println("inchi is empty: I don't think this ever happens");
            System.exit(0);
        }

        if(inchi.contains("FAKE")) {
//            System.out.println("FAKE         "+ inchi);
            nameInspecter.inspect(achem);
            return;
        }

        //See if the inchi can be interpretted by indigo and route accordingly
        try {
            IndigoObject mol = iinchi.loadMolecule(inchi);
//            System.out.println("PassedIndigo "+ inchi);
            stereochemistryInspecter.inspect(achem);
            return;
        } catch(Exception err) {
//            System.out.println("FailedIndigo "+ inchi);
            inchiInspecter.inspect(achem);
        }
    }

    public void postProcess() {
        stereochemistryInspecter.postProcess();
        nameInspecter.postProcess();
        inchiInspecter.postProcess();
    }
}
