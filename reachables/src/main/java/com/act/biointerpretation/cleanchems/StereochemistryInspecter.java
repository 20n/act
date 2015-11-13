package com.act.biointerpretation.cleanchems;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jca20n on 9/25/15.
 */
public class StereochemistryInspecter {

    private Indigo indigo;
    private IndigoInchi iinchi;
    private Map<String,Map<String,Integer>> inchiTrie;

    public StereochemistryInspecter(NoSQLAPI api) {
        indigo = new Indigo();
        iinchi = new IndigoInchi(indigo);
        inchiTrie = new HashMap<>();
    }

    public void inspect(Chemical achem) {
        String inchi = achem.getInChI();
        String[] zones = inchi.split("/");
        String nostereo = zones[0];
        String btms = "";
        for(int i=1; i<zones.length; i++) {
            String zone = zones[i];
            if(zone.startsWith("b") || zone.startsWith("t") || zone.startsWith("m") || zone.startsWith("s")) {
                btms+="/" + zone;
            } else {
                nostereo+="/" + zone;
            }
        }
        Map<String, Integer> stereoVariants = inchiTrie.get(nostereo);
        if(stereoVariants == null) {
            stereoVariants = new HashMap<>();
        }
        int count = 1;
        if(stereoVariants.containsKey(btms)) {
            count+=stereoVariants.get(btms);
        }
        stereoVariants.put(btms, count);
        inchiTrie.put(nostereo, stereoVariants);
    }

    /**
     * Called once after done with inspecting
     */
    public void postProcess() {

        for(String nostereo : inchiTrie.keySet()) {
            Map<String, Integer> stereos = inchiTrie.get(nostereo);
            if(stereos.size() == 1) {
                continue;
            }
            System.out.println(">" + nostereo);
            for(String btms : stereos.keySet()) {
                System.out.println(stereos.get(btms) + "  " + btms);
            }
        }

        System.out.println();


    }


    private void printData(IndigoObject mol, String name, String inchi) {

        System.out.println("\n.\n");

        System.out.println("StereochemistryInspecter.inspect:\n" + name);
        System.out.println(inchi);
//        System.out.println("checkAmbiguousH: " + mol.checkAmbiguousH());  //always empty
//        System.out.println("checkBadValence: " + mol.checkBadValence());  //always empty
        //System.out.println("cml: " + mol.cml());
//        System.out.println("dbgInternalType: " + mol.dbgInternalType());  //always 02
//        System.out.println("countDataSGgroups: " + mol.countDataSGgroups());  //always zero
//        System.out.println("countGenericSGroups: " + mol.countGenericSGroups());  //always zero
        System.out.println("countHydrogens: " + mol.countHydrogens());  //usually > 0
        System.out.println("countImplicitHydrogens: " + mol.countImplicitHydrogens());  //usually > 0
//        try {
//            System.out.println("countMolecules: " + mol.countMolecules());  //never > zero
//        } catch(Exception err) {}
//        System.out.println("countPseudoatoms: " + mol.countPseudoatoms());  //always zero
//        System.out.println("countRSites: " + mol.countRSites());  //always zero
        System.out.println("countStereocenters: " + mol.countStereocenters());  //sometimes > 0
        System.out.println("isChiral: " + mol.isChiral());
//        System.out.println("countRepeatingUnits: " + mol.countRepeatingUnits());  //always zero
        System.out.println("countSSSR: " + mol.countSSSR());  //sometimes > 0
//        System.out.println("countSuperatoms: " + mol.countSuperatoms());  //always zero
//        try {
//            System.out.println("explicitValence: " + mol.explicitValence());  //always fails
//        } catch(Exception err) {}
//        try {
//            System.out.println("isHighlighted: " + mol.isHighlighted());  //always fails
//        } catch(Exception err) {}
//        try {
//            System.out.println("isotope: " + mol.isotope());  //always fails
//        } catch(Exception err) {}
//        try {
//            System.out.println("isPseudoatom: " + mol.isPseudoatom());  //always fails
//        } catch(Exception err) {}
//        try {
//            System.out.println("isRSite: " + mol.isRSite());  //always fails
//        } catch(Exception err) {}


        System.out.println("markEitherCisTrans: " + mol.markEitherCisTrans());  //sometimes > 0

//        try {
//            System.out.println("radical: " + mol.radical()); //always fails
//        } catch(Exception err) {}

//        try {
//            System.out.println("radicalElectrons: " + mol.radicalElectrons()); //always fails
//        } catch(Exception err) {}

//        try {
//            System.out.println("stereocenterType: " + mol.stereocenterType()); //always fails
//        } catch(Exception err) {}

//        try {
//            System.out.println("topology: " + mol.topology()); //always fails
//        } catch(Exception err) {}

//        try {
//            System.out.println("valence: " + mol.valence());  //always fails
//        } catch(Exception err) {}

        System.out.println("done");
    }

    public static void main(String[] args) {
        ChemicalCleaner.main(args);
    }
}
