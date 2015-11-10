package com.act.biointerpretation.stereochemistry;

import act.api.NoSQLAPI;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a variant of an inchi that explicitly states stereo info at each chiral center,
 * and not at achiral ones.
 *
 * Created by jca20n on 10/25/15.
 */
public class StereoAbstracter {
    private Indigo indigo;
    private IndigoInchi iinchi;

    public static void main(String[] args) throws Exception {
        StereoAbstracter SA = new StereoAbstracter();
        SA.test();
    }

    public void test() throws Exception {
        String input = "InChI=1S/C10H16N5O13P3/c11-8-5-9(13-2-12-8)15(3-14-5)10-7(17)6(16)4(26-10)" +
                "1-25-30(21,22)28-31(23,24)27-29(18,19)20/h2-4,6-7,10,16-17H,1H2,(H,21,22)(H,23,24)" +
                "(H2,11,12,13)(H2,18,19,20)/t4-,6-,7-,10-/m1/s1"; //ATP
//        String input = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)"; //tartaric acid, racemic
//        String input = "InChI=1S/C5H9NO2/c7-5(8)4-2-1-3-6-4/h4,6H,1-3H2,(H,7,8)"; //proline, racemic
//        String input = "InChI=1S/C6H12O2/c7-5-3-1-2-4-6(5)8/h5-8H,1-4H2"; //1,2 cylohexane diol, racemic
        String cleaned = clean(input);
        System.out.println(cleaned);
    }

    private void testDB() {
        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");  //just reading synapse, no write
    }

    public StereoAbstracter() {
        indigo = new Indigo();
        iinchi = new IndigoInchi(indigo);

        //This option makes it show ?'s in the inchi at ambiguous stereochemistry positions
        //http://lifescience.opensource.epam.com/indigo/options/inchi.html#
        indigo.setOption("inchi-options", "/SUU");
    }

    public String clean(String inputInchi) throws Exception {
        StringBuilder log = new StringBuilder();
        log.append("StereoAbstracter cleaning: " + inputInchi).append("\n\t");

        //Detect chiral indices
        IndigoObject mol = iinchi.loadMolecule(inputInchi);
        boolean[] indices = getChiralIndices(mol);
        for(int index = 0; index < indices.length; index++) {
            boolean bool = indices[index];
            log.append(index).append(":").append(bool).append(", ");
        }
        log.append("\n");

        //Scan through indices and abstract the chirality
        for(int index = 0; index < indices.length; index++) {
            log.append("Modifying index " + index).append("\n\t");

            boolean isChiral = indices[index];
            IndigoObject atom = mol.getAtom(index);

            //If it's achiral, clear any stereo info
            if(isChiral == false) {
                atom.resetStereo();
                log.append("is achiral, erased info\n");
                continue;
            }

            //Otherwise it is chiral

            //If chirality is already present on this atom, leave it alone
            if(atom.stereocenterType() == Indigo.ABS) {
                log.append("already has stereo info\n");
                continue;
            }

            //Otherwise set the stereochemistry to anything goes
            try {
                List<Integer> neighbors = new ArrayList<>();
                for(IndigoObject nei : atom.iterateNeighbors()) {
                    neighbors.add(nei.index());
                }
                atom.resetStereo();
                if(neighbors.size() == 4) {
                    atom.addStereocenter(Indigo.EITHER, neighbors.get(0), neighbors.get(1), neighbors.get(2), neighbors.get(3));
                } else if(neighbors.size() == 3) {
                    atom.addStereocenter(Indigo.EITHER, neighbors.get(0), neighbors.get(1), neighbors.get(2));
                } else {
                    System.err.println("Wrong number of neighbors, this shouldn't happen");
                }

                log.append("is chiral, making ambiguous\n");
            } catch(Exception err) {
                log.append("error setting as chiral\n");
                throw err;
            }
        }
        String inchi = iinchi.getInchi(mol);
        String smiles = mol.canonicalSmiles();
        log.append("\noutputSmiles: " + smiles);
        log.append("\noutputInchi: " + inchi).append("\n");

//        System.out.println(log.toString());
        return inchi;
    }

    private boolean[] getChiralIndices(IndigoObject mol) {
        StringBuilder log = new StringBuilder();
        log.append("getChiralIndices for " + mol.canonicalSmiles()).append("\n");

        boolean[] out = new boolean[mol.countAtoms()];

        Set<IndigoObject> permutations = permute(mol);
        log.append("have permutations:\n");
        for(IndigoObject amol : permutations) {
            log.append("   ").append(amol.canonicalSmiles()).append("\n");
        }

        //Scan through each index, filter, and collapsee
        scan: for(int index=0; index<mol.countAtoms(); index++) {
            log.append(">working on atom: " + mol.getAtom(index).symbol() + index).append("\n");
            //Create two inchi sets to hold the variants that match at this atom
            Set<String> amolInchis = new HashSet<>();
            Set<String> enantioInchis = new HashSet<>();

            //Iterate through each permutated molecule
            for (IndigoObject amol : permutations) {
                //See if this index is already indicated as achiral
                IndigoObject atom = amol.getAtom(index);
                if(atom.stereocenterType() != Indigo.ABS) {
                    out[index] = false;
                    log.append("-index " + index + " is not indigo.ABS").append("\n");
                    continue scan;
                }

                //Pull one bond connected to that atom
                IndigoObject bond = null;
                for(IndigoObject nei : atom.iterateNeighbors()) {
                    bond = nei.bond();
                    break;
                }

                //Populate sets based on chirality of this bond
                int stereo = bond.bondStereo();
                String inchi = iinchi.getInchi(amol);
                if(stereo == Indigo.UP) {
                    amolInchis.add(inchi);
                } else {
                    enantioInchis.add(inchi);
                }
            }

            log.append("amolInchis").append("\n");
            for(String inchi : amolInchis) {
                log.append(inchi).append("\n");
            }

            log.append("enantioInchis").append("\n");
            for(String inchi : enantioInchis) {
                log.append(inchi).append("\n");
            }

            //See if the sets match exactly
            if(amolInchis.size() == enantioInchis.size()) {
                amolInchis.removeAll(enantioInchis);
                if (amolInchis.size() == 0) {
                    log.append("all inchis match, not a stereocenter\n");
                    out[index] = false;
                } else {
                    log.append("inchis don't match, is a stereocenter\n");
                    out[index] = true;
                }
            } else {
                log.append("inchis have different #s, is a stereocenter\n");
                out[index] = true;
            }
        }

        //Log output
        log.append("\nreturning:\n");
        for(int index = 0; index < out.length; index++) {
            log.append(mol.getAtom(index).symbol() + index).append("\t").append(out[index]).append("\n");
        }

        System.out.println(log.toString());
        return out;
    }

    private Set<IndigoObject> permute(IndigoObject mol) {
        StringBuilder log = new StringBuilder();
        log.append(">> Permuting: " + iinchi.getInchi(mol)).append("\n");
        log.append("      smiles: " + mol.canonicalSmiles()).append("\n\n");

        Set<IndigoObject> out = new HashSet<>();
        out.add(mol.clone());

        //For each atom index in the molecule
        for(int index=0; index<mol.countAtoms(); index++) {
            IndigoObject atom =  mol.getAtom(index);
            log.append("working on atom " + atom.symbol()  + index).append("\n");

            //If it lacks the minimal requirements for being a chiral position, don't permute
            String hyb = getHybridization(atom);
            if(hyb == null || !hyb.equals("sp3")) {
                log.append("-not chiral, not sp3").append("\n\n");
                continue;
            }
            if(atom.degree() < 3) {
                log.append("-not chiral, degree is less tham 3").append("\n\n");
                continue;
            }

            //If got here, atom may be chiral, so permute it
            log.append("+permuting for chirality").append("\n");

            Set<IndigoObject> worklist = new HashSet<>();
            for(IndigoObject amol : out) {
                //Set the stereochemistry of this index in amol
                IndigoObject aAtom = amol.getAtom(index);

                List<Integer> neighbors = new ArrayList<>();
                for(IndigoObject nei : atom.iterateNeighbors()) {
                    neighbors.add(nei.index());
                }

                aAtom.resetStereo();
                if(neighbors.size() == 4) {
                    aAtom.addStereocenter(Indigo.ABS, neighbors.get(0), neighbors.get(1), neighbors.get(2), neighbors.get(3));
                } else if(neighbors.size() == 3) {
                    aAtom.addStereocenter(Indigo.ABS, neighbors.get(0), neighbors.get(1), neighbors.get(2));
                } else {
                    System.err.println("Wrong number of neighbors, this shouldn't happen");

                }
                log.append(" created amol: " + amol.canonicalSmiles()).append("\n");

                //Create a copy and invert the stereochemistry at this index
                IndigoObject enantiomer = amol.clone();
                IndigoObject enantioAtom = enantiomer.getAtom(index);
                enantioAtom.invertStereo();
                log.append(" created enantiomer: " + enantiomer.canonicalSmiles()).append("\n");

                //Put the new molecules in the worklist
                worklist.add(amol);
                worklist.add(enantiomer);
            }

            //Replace last round's mol's with the new mols
            out = worklist;
        }

//        System.out.println(log.toString());
        return out;
    }

    private static String getHybridization(IndigoObject atom) {
        int Hs = atom.countHydrogens();
        int degree = atom.degree();
        int groups = Hs + degree;
//        System.out.println("atom hybriization: " + atom.symbol() +  atom.index() + " groups " + groups + " hydrogs " + Hs + " degree " + degree);
        int atomic = atom.atomicNumber();

        //Only consider nitrogen and carbons
        if(atomic < 6 || atomic > 7) {
            return null;
        }

        switch(groups) {
            case 2:
                return "sp1";
            case 3:
                return "sp2";
            case 4:
                return "sp3";
            case 5:
                return "sp3";
        }
        return null;
    }

}
