package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.operators.OperatorExtractor;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;
import com.act.biointerpretation.cofactors.MolViewer;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.operators.SkeletonMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by jca20n on 11/2/15.
 */
public class MechanisticCleaner {
    NoSQLAPI api;
    private MechanisticValidator validator;
    private Map<Integer,Integer> rxnIdToScore;
    private DudRxnLog dudlog;

    public static void main(String[] args) {
        ChemAxonUtils.license();

        MechanisticCleaner cleaner = new MechanisticCleaner();
        cleaner.initiate();
        cleaner.flowAllReactions();
    }

    public void initiate() {
        rxnIdToScore = new HashMap<>();
        validator = new MechanisticValidator();
        validator.initiate();
        dudlog = new DudRxnLog();

        File log = new File("data/MechanisticCleaner/visited_reactions.txt");
        try {
            String data = FileUtils.readFile(log.getAbsolutePath());
            data = data.replaceAll("\"", "");
            String[] lines = data.split("\\r|\\r?\\n");
            for(String line : lines) {
                String[] tabs = line.split("\t");
                int rxnid = Integer.parseInt(tabs[0]);
                int score = Integer.parseInt(tabs[1]);

                rxnIdToScore.put(rxnid, score);
            }
        } catch(Exception err) {
            System.out.println("no preexisiting log");
            FileUtils.writeFile("", log.getAbsolutePath());
        }

    }

    public void flowAllReactions() {
        Map<String, Set<Long>> observedROs = new HashMap<>(); //For counting up instances of new ROs

        this.api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        while(iterator.hasNext()) {

            //Process one reaction
            try {
                Reaction rxn = iterator.next();
                int rxnid = rxn.getUUID();
                if(rxnIdToScore.containsKey(rxnid)) {
                    continue;
                }

                Set<String> subInchis = getInchis(rxn.getSubstrates());
                Set<String> prodInchis = getInchis(rxn.getProducts());

                int result = validator.isValid(subInchis, prodInchis);


                //Throw GUIs upon particular events
                if(result == 0) {
                    Set<String> subs = getInchis(rxn.getSubstrates());
                    Set<String> prods = getInchis(rxn.getProducts());
                    if(dudlog.get(dudlog.hash(subs, prods)) == null) {
                        ReactionDashboard dashboard = new ReactionDashboard(subs, prods, rxn.getUUID(), dudlog);
                        dashboard.setVisible(true);
                        return;
                    }
                }

                rxnIdToScore.put(rxnid, result);
                saveLog(rxnid, result);

            } catch (Exception err) {

            }
        }
    }

    private void saveLog(int rxnid, int score) {
        try {
            File log = new File("data/MechanisticCleaner/visited_reactions.txt");
            FileWriter bw = new FileWriter(log.getAbsoluteFile(), true);
            bw.write(Integer.toString(rxnid));
            bw.write("\t");
            bw.write(Integer.toString(score));
            bw.write("\n");
            bw.close();

            System.out.println(rxnid + "   " + score);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Set<String> getInchis(Long[] substrates) {
        Set<String> inchis = new HashSet<>();
        for(Long along : substrates) {
            Chemical achem = api.readChemicalFromInKnowledgeGraph(along);
            inchis.add(achem.getInChI());
        }
        return inchis;
    }
}
