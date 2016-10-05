package com.act.biointerpretation.carbonenumeration;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import chemaxon.calculations.hydrogenize.Hydrogenize;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.util.*;

/**
 * Created by jca20n on 12/9/15.
 */
public class CrawlCarbons {
    private NoSQLAPI api;

    Set<String> zeroAdjC = new HashSet<>();
    Set<String> oneAdjC = new HashSet<>();
    Set<String> twoAdjC = new HashSet<>();

    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        CrawlCarbons abstractor = new CrawlCarbons();
        abstractor.initiate();

//        Molecule mol = MolImporter.importMol("CCCO");
//        Molecule mol = MolImporter.importMol("InChI=1S/C4H10/c1-3-4-2/h3-4H2,1-2H3");
//        Molecule mol = MolImporter.importMol("InChI=1S/C3H8O3/c4-1-3(6)2-5/h3-6H,1-2H2");
//        Molecule mol = MolImporter.importMol("CCC(O)C(=O)C(N)CO");
//        abstractor.processMolecule(mol);

        try {
            abstractor.run();
        } catch(Exception err) {
            System.err.println("\n\n!!!!! Failed to complete !!!!! \n\n");
        }
        abstractor.printout();
    }

    public void printout() throws Exception {
        System.out.println(this.zeroAdjC.size());
        System.out.println(this.oneAdjC.size());
        System.out.println(this.twoAdjC.size());


        StringBuilder output1 = new StringBuilder();

        output1.append(">zeroAdjC\n");
        for(String imol : zeroAdjC) {
            String smiles = ChemAxonUtils.InchiToSmiles(imol);
            output1.append(imol).append("\t");
            output1.append(smiles).append("\t");

            Molecule amol = MolImporter.importMol(imol);
            output1.append(amol.getFormula()).append("\t");

            Double dmass = amol.getExactMass();
            output1.append(dmass).append("\n");
        }

        output1.append("\n>oneAdjC\n");
        for(String imol : oneAdjC) {
            String smiles = ChemAxonUtils.InchiToSmiles(imol);
            output1.append(imol).append("\t");
            output1.append(smiles).append("\t");


            Molecule amol = MolImporter.importMol(imol);
            output1.append(amol.getFormula()).append("\t");

            Double dmass = amol.getExactMass() - 1.0 * (12.000000 + 3*1.007276);
            output1.append(dmass).append("\n");
        }

        output1.append("\n>twoAdjC\n");
        for(String imol : twoAdjC) {
            String smiles = ChemAxonUtils.InchiToSmiles(imol);
            output1.append(imol).append("\t");
            output1.append(smiles).append("\t");

            Molecule amol = MolImporter.importMol(imol);
            output1.append(amol.getFormula()).append("\t");

            Double dmass = amol.getExactMass() - 2.0 * (12.000000 + 3*1.007276);
            output1.append(dmass).append("\n");
        }


        FileUtils.writeFile(output1.toString(), "CrawlCarbons_output_inchi.txt");
        System.out.println(output1.toString());
    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
//
//        File dir = new File("output/moieties");
//        if(!dir.exists()) {
//            dir.mkdir();
//        }
    }

    public void run() throws Exception {
        Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Chemical achem = iterator.next();
            try {
                //Load the next molecule
                long id = achem.getUuid();
                String inchi = achem.getInChI();
                Molecule mol = MolImporter.importMol(inchi);

                processMolecule(mol);


            } catch(Exception err) {
                System.out.println("dud" + achem.getUuid());
            }
        }
    }

    public void processMolecule(Molecule mol) throws Exception {
        Hydrogenize.addHAtoms(mol);

        int count = mol.getAtomCount();
//        System.out.println(count);

        //Set the indices as mapping numbers on each atom
        for(int i=0; i<count; i++) {
            MolAtom C0 = mol.getAtom(i);
            C0.setAtomMap(i);
        }

        //Examine each carbon in the molecule
        for(int i=0; i<count; i++) {
//            System.out.println("i " + i);

            MolAtom C0 = mol.getAtom(i);

            //If the C0 is not carbon, ignore it
//            System.out.println("atno" + C0.getAtno());

            if(C0.getAtno() != 6) {
                continue;
            }

            //Examine the neighboring atoms
            Set<Integer> carbonIndices = new HashSet<>();
            Set<Integer> noncarbIndices = new HashSet<>();

            for(int b=0; b< C0.getBondCount(); b++) {
                MolBond bond = C0.getBond(b);
                MolAtom C1 = bond.getOtherAtom(C0);
                if(C1.getAtno() == 6) {
                    carbonIndices.add(C1.getAtomMap());
                } else {
                    noncarbIndices.add(C1.getAtomMap());
                }
            }

            for(Integer index : carbonIndices) {
//                System.out.println("adj carbon index: " + index);
            }

            //If the C0 has more than 2 carbons attached, ignore it
            if(carbonIndices.size()>2) {
                continue;
            }

            //Create a duplicate molecule
            Molecule molclone = mol.clone();

            //Delete all the adjacent carbons in the duplicate
            Set<MolAtom> tossers = new HashSet<>();
            for(Integer ic1 : carbonIndices) {
                MolAtom C1 = molclone.getAtom(ic1);
                for(int x=0; x<C1.getBondCount(); x++) {
                    MolBond b = C1.getBond(x);
                    MolAtom C2 = b.getOtherAtom(C1);
                    tossers.add(C2);
                }
            }

            for(MolAtom C1 : tossers) {
                if(C1.getAtomMap() == i) {
                    continue;
                }
                molclone.removeAtom(C1);
            }


//            System.out.println(">");
//            System.out.println(ChemAxonUtils.toSmiles(mol));
//            System.out.println(ChemAxonUtils.toSmiles(molclone));

            //Break the cloned molecule with adj carbons removed into frags
            Molecule[] frags = molclone.convertToFrags();
//            System.out.println("count: " + frags.length);

            //Isolate the fragment containing the original C0 carbon
            Molecule Cfrag = null;
            MolAtom fragAtom0 = null;
            for(Molecule frag : frags) {
//                System.out.println("examining: " + ChemAxonUtils.toSmiles(frag));
                for(int x=0; x<frag.getAtomCount(); x++) {
                    MolAtom xatm = frag.getAtom(x);
                    if(xatm.getAtomMap() == i) {
                        Cfrag = frag;
                        fragAtom0 = xatm;
                        break;
                    }
                }
            }
//            System.out.println("found: " + ChemAxonUtils.toSmiles(Cfrag));

            //Examine Cfrag and throw it out if it contains more than one carbon
            int carbcount = 0;
            for(int x=0; x<Cfrag.getAtomCount(); x++) {
                MolAtom Cx = Cfrag.getAtom(x);
                if(Cx.getAtno() == 6) {
                    carbcount++;
                }
            }
            if(carbcount > 1 + carbonIndices.size()) {
//                System.out.println("frag has C's: " + ChemAxonUtils.toSmiles(Cfrag));
                continue;
            }

            System.out.println("frag good: " + ChemAxonUtils.toSmiles(Cfrag));



            //Convert the Cfrag into an inchi for removal of mappings and consolidation
            String inchiFrag = ChemAxonUtils.toInchi(Cfrag);

            if(inchiFrag == null) {
                System.err.println("!!!! major error if inchifrag null");
                throw new Exception();
            }

            if(carbonIndices.size() == 0) {
                zeroAdjC.add(inchiFrag);
            } else if(carbonIndices.size() == 1) {
                oneAdjC.add(inchiFrag);
            } else if(carbonIndices.size() == 2) {
                twoAdjC.add(inchiFrag);
            }

//            System.out.println("next\n");
        }

    }

}
