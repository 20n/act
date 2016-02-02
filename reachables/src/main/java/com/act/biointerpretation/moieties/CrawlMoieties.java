package com.act.biointerpretation.moieties;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.operators.OperatorHasher;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.io.File;
import java.util.*;

/**
 * Created by jca20n on 12/9/15.
 */
public class CrawlMoieties {
    private NoSQLAPI api;
    private MoietyExtractor extractor;
    public List<String> moietiesObserved;
    public Map<Long,Integer> ChemIDToMoietyIndex;

    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        CrawlMoieties abstractor = new CrawlMoieties();
        abstractor.initiate();
        abstractor.run();
        abstractor.printout();
    }

    public void printout() throws Exception {
        StringBuilder moieties = new StringBuilder();
        StringBuilder chemIds = new StringBuilder();

        for(Long id : ChemIDToMoietyIndex.keySet()) {
            int index = ChemIDToMoietyIndex.get(id);
            String inchi = moietiesObserved.get(index);
            Molecule amol = MolImporter.importMol(inchi);
            String smiles = ChemAxonUtils.toSmiles(amol);
            moieties.append(inchi).append("\t").append(smiles).append("\n");
            chemIds.append(id).append("\t").append(inchi).append("\n");
        }
        FileUtils.writeFile(moieties.toString(), "output/moieties/exact_moieties.txt");



    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        extractor = new MoietyExtractor();
        moietiesObserved = new ArrayList<>();
        ChemIDToMoietyIndex = new HashMap<>();

        File dir = new File("output/moieties");
        if(!dir.exists()) {
            dir.mkdir();
        }
    }

    public void run() {
        Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Chemical achem = iterator.next();
            try {
                //Load the next molecule
                long id = achem.getUuid();
                String inchi = achem.getInChI();
                Molecule mol = MolImporter.importMol(inchi);

                //Extract the moieties
                Set<Molecule> moieties = extractor.extract(mol);

                //Put the resulting moieties into collectors
                for(Molecule moiety : moieties) {
                    //Don't include it if there is ambiguity to the stereochemistry of any atom
                    if(isConcrete(moiety)) {
                        continue;
                    }

                    //Put the inchi of the moiety into the list
                    String moiInchi = ChemAxonUtils.toInchi(moiety);
                    if(!moietiesObserved.contains(moiInchi)) {
                        moietiesObserved.add(moiInchi);
                    }

                    //If the moiety accounts for all heteroatoms in the molecule, save that too
                    if(moieties.size() != 1) {
                        continue;
                    }

                    if(moiety.getAtomCount() == mol.getAtomCount()) {
                        int index = moietiesObserved.indexOf(moiInchi);
                        ChemIDToMoietyIndex.put(id, index);
                    }
                }



            } catch(Exception err) {
                System.out.println("dud" + achem.getUuid());
            }
        }
    }

    private boolean isConcrete(Molecule amol) {
        for(int i=0; i<amol.getAtomCount(); i++) {
            if(amol.getChirality(i) == MoleculeGraph.PARITY_EITHER) {
                return false;
            }
        }
        return true;
    }

    public Set<Chemical> getExactMoietyChems() {
        Set<Chemical> out = new HashSet<>();
        for(long id : this.ChemIDToMoietyIndex.keySet()) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(id);
            out.add(achem);
        }
        return out;
    }
}
