package com.act.biointerpretation.operators;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.common.util.Pair;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Created by jca20n on 11/9/15.
 */
public class CrawlAndHmERO {

    private NoSQLAPI api;
    private SimpleReactionFactory simplifier;
    private OperatorHasher hasher;

    private Set<Integer> rxnIds;
    private Map<Pair<String,String>, Integer> counts;


    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        CompareROs comp = new CompareROs();
        comp.initiate();
        Map<Pair<String,String>, Integer> counts = comp.compare();
        Set<Integer> rxnIds = comp.getGoodRxnIds(counts);

        CrawlAndHmERO abstractor = new CrawlAndHmERO(rxnIds, counts);
        abstractor.initiate();
        abstractor.flowAllReactions();
    }

    public CrawlAndHmERO(Set<Integer> rxnIds, Map<Pair<String,String>, Integer> counts) {
        this.rxnIds = rxnIds;
        this.counts = counts;
    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        simplifier = SimpleReactionFactory.generate(api);
        List<String> names = simplifier.getCofactorNames();

        try {
            hasher = OperatorHasher.deserialize("output/hash_hmERO.ser");
        } catch(Exception err) {
            hasher = new OperatorHasher(names);
        }
    }

    private void flowAllReactions() throws Exception {
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        for(int pos : this.rxnIds) {
            long i = (long) pos;
            //Serialize the hashers
            if(i % 1000 == 0) {
                System.out.println("count:" + i);
                hasher.serialize("output/hash_hmERO.ser");
            }

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

        //Final save
        hasher.serialize("output/hash_hmERO.ser");
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
            RxnMolecule ro = new OperatorExtractor().calc_hmERO(mapped);
            System.out.print(" .");
            if(index(mapped, mapped, srxn, rxnID)) {
                System.out.println("c");
            }
        } catch(Exception err) {
            System.out.print(" x");
        }

        //Calculate the skeleton RO
        try {
            RxnMolecule mapped = new SkeletonMapper().map(reaction);
            RxnMolecule ro = new OperatorExtractor().calc_hmERO(mapped);
            if(index(mapped, ro, srxn, rxnID)) {
                System.out.println("c");
            }
            System.out.println(" .");
        } catch(Exception err) {
            System.out.println(" x");
        }
    }

    private boolean index(RxnMolecule rxn, RxnMolecule ro, SimpleReaction srxn, int rxnID) {
        //Index the ro
        Set<String> subCo = srxn.subCofactors;
        Set<String> prodCo = srxn.prodCofactors;
        String subro = ChemAxonUtils.toInchi(ro.getReactant(0));
        String prodro = ChemAxonUtils.toInchi(ro.getProduct(0));


        //Calculate hcERO and check it
        RxnMolecule hcERO = new OperatorExtractor().calc_hcERO(rxn);
        String s = ChemAxonUtils.toInchi(hcERO.getReactant(0));
        String p = ChemAxonUtils.toInchi(hcERO.getProduct(0));

        Pair<String,String> pair = new Pair(s,p);
        if(!this.counts.containsKey(pair)) {
            return false;
        }

        //Index the mock data
        hasher.index(subro, prodro, subCo, prodCo, rxnID);
        return true;
    }
}
