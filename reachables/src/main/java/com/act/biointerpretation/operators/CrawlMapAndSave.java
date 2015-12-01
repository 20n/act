package com.act.biointerpretation.operators;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.common.util.Pair;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * This one crawls through all the reactions that pass the filter of
 * 1)  Giving rise to an hcERO
 * 2)  That hcERO passes the CompareROs criteria of multiple-observations
 *
 * This re-maps each one, picks the map that matches the hcERO, and serializes
 * the RxnMolecule to disk for further analysis
 *
 * Created by jca20n on 11/9/15.
 */
public class CrawlMapAndSave {

    private NoSQLAPI api;
    private SimpleReactionFactory simplifier;

    private Set<Integer> rxnIds;
    private Map<Pair<String,String>, Integer> counts;


    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        CompareROs comp = new CompareROs();
        comp.initiate();
        Map<Pair<String,String>, Integer> counts = comp.compare();
        Set<Integer> rxnIds = comp.getGoodRxnIds(counts);
        System.out.println("Total rxnIds: " + rxnIds.size());

        CrawlMapAndSave abstractor = new CrawlMapAndSave(rxnIds, counts);
        abstractor.initiate();
        abstractor.flowAllReactions();

        System.out.println("done!");
        System.exit(0);
    }

    public CrawlMapAndSave(Set<Integer> rxnIds, Map<Pair<String, String>, Integer> counts) {
        this.rxnIds = rxnIds;
        this.counts = counts;
        File dir = new File("output/simple_reactions/");
        if(!dir.exists()) {
            dir.mkdir();
        }
        dir = new File("output/mapped_reactions/");
        if(!dir.exists()) {
            dir.mkdir();
        }
        dir = new File("output/mapped_reactions/automap");
        if(!dir.exists()) {
            dir.mkdir();
        }
        dir = new File("output/mapped_reactions/skeleton");
        if(!dir.exists()) {
            dir.mkdir();
        }
    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        simplifier = SimpleReactionFactory.generate(api);
        List<String> names = simplifier.getCofactorNames();
    }

    private void flowAllReactions() throws Exception {
        int count = 0;
        for(int rxnId : this.rxnIds) {
            count++;
            System.out.println("count: " + count);
            double dround = Math.floor(rxnId / 1000);
            int iround = (int) dround;

            //See if this reaction has already been run
            File file = new File("output/simple_reactions/" + "/range" + iround + "/" + rxnId + ".ser");
            if(file.exists()) {
                System.out.println("exists");
                continue;
            }

            long i = rxnId;
            Reaction rxn = null;
            try {
                rxn = api.readReactionFromInKnowledgeGraph(i);
            } catch(Exception err) {
                System.out.println("error pulling rxn " + i);
                continue;
            }

            if(rxn==null) {
                System.out.println("null rxn " + i);
                continue;
            }

            try {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(new RunManager(rxn));

                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    System.out.println("Timed out");
                }

                executor.shutdownNow();

            } catch (Exception err) {
            }
        }
    }


    private class RunManager implements Callable<String> {
        Reaction reaction;
        public RunManager(Reaction reaction) {
            this.reaction = reaction;
        }
        @Override
        public String call() throws Exception {
            processOne(this.reaction);
            return "Ready!";
        }
    }

    private void processOne(Reaction rxn) throws Exception {
        SimpleReaction srxn = simplifier.simplify(rxn);
        RxnMolecule reaction = srxn.getRxnMolecule();
        int rxnID = rxn.getUUID();
        System.out.println("id:" + rxnID);

        //Calculate the CHANGING RO
        try {
            RxnMolecule mapped = new ChangeMapper().map(reaction);
            System.out.print(" .");
            if(index(mapped, srxn, rxnID, false)) {
                System.out.print("c");
            }
        } catch(Exception err) {
            System.out.print(" x");
        }

        //Calculate the skeleton RO
        try {
            RxnMolecule mapped = new SkeletonMapper().map(reaction);
            if(index(mapped, srxn, rxnID, true)) {
                System.out.print("c");
            }
            System.out.println(" .");
        } catch(Exception err) {
            System.out.println(" x");
        }
    }

    private boolean index(RxnMolecule mapped, SimpleReaction srxn, int rxnID, boolean isSkeleton) {
        double dround = Math.floor(rxnID / 1000);
        int iround = (int) dround;

        //Serialize the SimpleReaction
        File dir = new File("output/simple_reactions/" + "/range" + iround + "/");
        if(!dir.exists()) {
            dir.mkdir();
        }
        File file = new File("output/simple_reactions/" + "/range" + iround + "/" + rxnID + ".ser");

        //Calculate hcERO and check it
        RxnMolecule hcERO = new OperatorExtractor().calc_hcERO(mapped);
        String s = ChemAxonUtils.toInchi(hcERO.getReactant(0));
        String p = ChemAxonUtils.toInchi(hcERO.getProduct(0));

        Pair<String,String> pair = new Pair(s,p);
        if(!this.counts.containsKey(pair)) {
            return false;
        }

        try {
            FileOutputStream fos = new FileOutputStream(file.getAbsolutePath());
            ObjectOutputStream out = new ObjectOutputStream(fos);
            out.writeObject(srxn);
            out.close();
            fos.close();

            //Serialize the mapped reaction so don't have to do again
            String mapType = "automap";
            if(isSkeleton) {
                mapType = "skeleton";
            }

            dir = new File("output/mapped_reactions/" + mapType + "/range" + iround + "/");
            if(!dir.exists()) {
                dir.mkdir();
            }
            fos = new FileOutputStream(dir.getAbsolutePath() + "/" + rxnID + ".ser");
            out = new ObjectOutputStream(fos);
            out.writeObject(mapped);
            out.close();
            fos.close();
        } catch(Exception err) {}
        return true;
    }
}
