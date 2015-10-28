package com.act.biointerpretation.step3_stereochemistry;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.*;

/**
 * Created by jca20n on 10/26/15.
 */
public class SplitReaction {
    SplitChem substrate;
    SplitChem product;
    int[] transforms;
    boolean[] inversions;

    public static void main(String[] args) {

        //Our reaction is serine being methylated to methylserine.  The SAM is already dropped by inspection.
        String serine = "InChI=1S/C3H7NO3/c4-2(1-5)3(6)7/h2,5H,1,4H2,(H,6,7)/t2-/m1/s1";
        String methylserine = "InChI=1S/C4H9NO3/c1-5-3(2-6)4(7)8/h3,5-6H,2H2,1H3,(H,7,8)/t3-/m0/s1";

        //However, the provided reaction is stated as racemic, represented in SUU form with ?'s
        String racemicSerine = "InChI=1S/C3H7NO3/c4-2(1-5)3(6)7/h2,5H,1,4H2,(H,6,7)/t2?";
        String racemicMethylSerine = "InChI=1S/C4H9NO3/c1-5-3(2-6)4(7)8/h3,5-6H,2H2,1H3,(H,7,8)/t3?";

        //Create the reaction
        SplitReaction rxn = new SplitReaction(racemicSerine, racemicMethylSerine);
        System.out.println(rxn.toString());
    }

    /**
     * This assumes that these abstract subInchi and prodInchi, are
     * represented SUU style, ie, the all-? representation
     * @param subInchi
     * @param prodInchi
     */
    public SplitReaction(String subInchi, String prodInchi) {
//        System.out.println("inputs:");
//        System.out.println("\tSubstrate: " + subInchi);
//        System.out.println("\tProduct  : " + prodInchi);

        this.substrate = new SplitChem(subInchi);
        this.product = new SplitChem(prodInchi);

        //Do atom to atom mapping to figure our the transforms
        transforms = calculateTransforms(substrate.inchiBase, product.inchiBase);

        inversions = calculateInversions();
    }

    private boolean[] calculateInversions() {
        //TODO:  FIGURE OUT WHAT TO DO HERE

        return new boolean[transforms.length];
    }

    private int[] calculateTransforms(String subInchi, String prodInchi) {
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        indigo.setOption("inchi-options", "/SUU");

        //Construct a reaction String representing the transformation on the abstact molecules
        IndigoObject mol = iinchi.loadMolecule(subInchi);
        String subSmiles = mol.canonicalSmiles();
        mol = iinchi.loadMolecule(prodInchi);
        String prodSmiles = mol.canonicalSmiles();
        String ro = subSmiles + ">>" + prodSmiles;

        //Use indigo to create an atom-to-atom mapping
        IndigoObject rxn = indigo.loadReaction(ro);
        rxn.automap("keep");

        Map<Integer,Integer> subIndexToBin = new HashMap<>();
        Map<Integer,Integer> prodBinToIndex = new HashMap<>();

        IndigoObject subMol = rxn.iterateReactants().next();
        IndigoObject prodMol = rxn.iterateProducts().next();

        for (IndigoObject atom : subMol.iterateAtoms()) {
            subIndexToBin.put(atom.index(), rxn.atomMappingNumber(atom));
        }

        for (IndigoObject atom : prodMol.iterateAtoms()) {
            prodBinToIndex.put(rxn.atomMappingNumber(atom), atom.index());
        }

        //Get the relevant substrate indices from the t term
        String tTerm = this.extractTerm("t", subInchi);
        String[] subIndices = tTerm.substring(1).split("[\\?,]+");
        tTerm = this.extractTerm("t", prodInchi);
        String[] prodIndices = tTerm.substring(1).split("[\\?,]+");

        //Put all the inchi positions in a Map
        Map<Integer, Integer> prodIndexToInchiPosition = new HashMap<>();
        for(int i=0; i<prodIndices.length; i++) {
            String sindex = prodIndices[i];
            int index = Integer.parseInt(sindex) - 1;
            prodIndexToInchiPosition.put(index, i);
        }

        //Create the transform for all indices
        int[] out = new int[subIndices.length];
        for(int i=0; i<subIndices.length; i++) {
            String sindex = subIndices[i];
            int index = Integer.parseInt(sindex) - 1;
            int bin = subIndexToBin.get(index);
            int prodIndex = prodBinToIndex.get(bin);
            int prodPos = prodIndexToInchiPosition.get(prodIndex);
            out[i] = prodPos;
        }

        return out;
    }

    public String toString() {
        String out = "Reaction data:\n\n";
        out += ">substrate:\n" + this.substrate.toString() + "\n";
        out += ">product:\n" + this.product.toString() + "\n";
        out += ">transform:\n";
        for(int i=0; i<transforms.length; i++) {
            out += i + " : " + transforms[i] + "\n";
        }

        out += "\n>inversions:\n";
        for(int i=0; i<inversions.length; i++) {
            out += i + " : " + inversions[i] + "\n";
        }
        return out;
    }

    private String extractTerm(String term, String inchi) {
        String[] splitted = inchi.split("/");
        for(String region : splitted) {
            if(region.startsWith(term)) {
                return region;
            }
        }
        return null;
    }

    public String abstractifyInchi(String inchi) {
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        indigo.setOption("inchi-options", "/SUU");
        IndigoObject mol = iinchi.loadMolecule(inchi);

        //Drop all stereos
        mol.clearStereocenters();

        return iinchi.getInchi(mol);
    }

    public static String SmilesToInchi(String smiles) {
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        indigo.setOption("inchi-options", "/SUU");
        IndigoObject mol = indigo.loadMolecule(smiles);
        return iinchi.getInchi(mol);
    }
}
