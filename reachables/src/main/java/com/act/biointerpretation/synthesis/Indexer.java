package com.act.biointerpretation.synthesis;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import com.act.biointerpretation.step3_mechanisminspection.MechanisticValidator;

import java.io.*;
import java.util.*;

/**
 * Created by jca20n on 2/15/16.
 */
public class Indexer implements Serializable {
    private static final long serialVersionUID = -6632151416443842271L;

    Map<Long, Set<Set<Long>>> hash;
    Map<String, Long> chemicals;
    long position = 0;

    private  long chemcount = 0;
    private  int rxncount = 0;
    private transient MechanisticValidator validator;
    private transient NoSQLAPI api;

    public Indexer(String db) {
        hash = new HashMap<>();
        chemicals = new HashMap<>();
        api = new NoSQLAPI(db, db);  //read only for this method
        validator = new MechanisticValidator(api);
        validator.initiate();
    }

    public void populate() {
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

        //Find the end of the ids
        long highest = 0;
        while(iterator.hasNext()) {
            iterator.next();
            try {
                Reaction rxn = iterator.next();
                long id = rxn.getUUID();
                if (id > highest) {
                    highest = id;
                }
            } catch(Exception err) {
                break;
            }
        }

        //make pap set
        Set<Long> papRxns = new HashSet();
        papRxns.add(6182l);
        papRxns.add(127944l);
        papRxns.add(273709l);
        papRxns.add(305455l);
        papRxns.add(352949l);
        papRxns.add(485648l);
        papRxns.add(507379l);
        papRxns.add(582391l);
        papRxns.add(639643l);
        papRxns.add(640490l);
        papRxns.add(658048l);
        papRxns.add(662071l);
        papRxns.add(684664l);
        papRxns.add(739610l);
        papRxns.add(804366l);
        papRxns.add(813773l);
        papRxns.add(823209l);
        papRxns.add(835988l);


        System.out.println("Highest is " + highest);

        //Scan through each reaction
        for(long i=position; i<highest; i++) {
            if(!papRxns.contains(i)) {
                continue;
            }
            position = i;
            Reaction rxn = null;
            try {
                rxn = api.readReactionFromInKnowledgeGraph(i);
            } catch(Exception er2) {
                System.out.println("! 1 - " + i);
                continue;
            }
            if(rxn==null) {
                System.out.println("! 2 - " + i);
                continue;
            }
            rxncount++;

            System.out.println("working: " + rxn.getUUID());
            MechanisticValidator.Report report = new MechanisticValidator.Report();
            try {
                //Remove cofactors and FAKE things
                validator.preProcess(rxn, report);

                //Populate the substrate set for this reaction
                Set<Long> subSet = new HashSet<>();
                for(String subInchi : report.subInchis) {
                    Long chemIndex = null;
                    if(chemicals.containsKey(subInchi)) {
                        chemIndex = chemicals.get(subInchi);
                    } else {
                        chemIndex = chemcount;
                        chemicals.put(subInchi, chemIndex);
                        chemcount++;
                    }
                    subSet.add(chemIndex);
                }

                //Associate the substrate set with each product
                for(String prodInchi : report.prodInchis) {

                    Long chemIndex = null;
                    if(chemicals.containsKey(prodInchi)) {
                        chemIndex = chemicals.get(prodInchi);
                    } else {
                        chemIndex = chemcount;
                        chemicals.put(prodInchi, chemIndex);
                        chemcount++;
                    }
                    Set<Set<Long>> existing = hash.get(chemIndex);
                    if (existing == null) {
                        existing = new HashSet<>();
                    }
                    existing.add(subSet);
                    hash.put(chemIndex, existing);
                }
            } catch(Exception err) {
                report.log.add("Failure to preProcess " + rxn.getUUID());
            }
            if(rxncount % 100 == 0) {
                try {
                    this.save("output/synthesis/indexer.ser");
                } catch(Exception err) {}
            }
        }

    }

    public void save(String path) throws Exception {
        FileOutputStream fos = new FileOutputStream(path);
        ObjectOutputStream out = new ObjectOutputStream(fos);
        out.writeObject(this);
        out.close();
        fos.close();
    }

    public static Indexer fromFile(String path) throws Exception {
        FileInputStream fis = new FileInputStream(path);
        ObjectInputStream ois = new ObjectInputStream(fis);
        Indexer out = (Indexer) ois.readObject();
        ois.close();
        fis.close();
        return out;
    }

    public static void main(String[] args) throws Exception {
        File dir = new File("output/synthesis");
        if(!dir.exists()) {
            dir.mkdir();
        }

        //Load existing data, or start over
        Indexer indexer = null;
        try {
            indexer = Indexer.fromFile("output/synthesis/indexer.ser");
            indexer.api = new NoSQLAPI("synapse", "synapse");
            indexer.validator = new MechanisticValidator(indexer.api);
        } catch(Exception err) {}
        if(indexer == null) {
            indexer = new Indexer("synapse");
        }

        indexer.populate();

        System.out.println(indexer.hash.size());

        indexer.save("output/synthesis/indexer.ser");
        System.out.println("done");
    }
}
