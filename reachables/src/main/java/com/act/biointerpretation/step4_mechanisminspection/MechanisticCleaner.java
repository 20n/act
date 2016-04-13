package com.act.biointerpretation.step4_mechanisminspection;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

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
    private RxnLog rxnlog;

    public static void main(String[] args) {
        ChemAxonUtils.license();

        MechanisticCleaner cleaner = new MechanisticCleaner();
        cleaner.initiate();
        cleaner.flowAllReactions();
    }

    public void initiate() {
        this.api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        rxnIdToScore = new HashMap<>();
        validator = new MechanisticValidator(api);
        validator.initiate();
        rxnlog = new RxnLog();

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

        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        while(iterator.hasNext()) {

            //Process one reaction
            try {
                Reaction rxn = iterator.next();
                int rxnid = rxn.getUUID();
                if(rxnIdToScore.containsKey(rxnid)) {
                    continue;
                }


                MechanisticValidator.Report report = validator.validate(rxn, 4);


                //Throw GUIs upon particular events
                if(report.score == -1) {
                    if(rxnlog.get(rxnlog.hash(report.subInchis, report.prodInchis)) == null) {
                        System.out.println("Displaying: " + rxn.getUUID());
                        ReactionDashboard dashboard = new ReactionDashboard(report, rxn.getUUID(), rxnlog);
                        dashboard.setVisible(true);
                        return;
                    }
                }

                rxnIdToScore.put(rxnid, report.score);
                saveLog(rxnid, report.score);

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
