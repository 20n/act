package com.act.biointerpretation.operators;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by jca20n on 11/9/15.
 */
public class CrawlAndAbstract {

    private NoSQLAPI api;
    private SimpleReactionFactory simplifier;
    private OperatorHasher brendaHasher;
    private OperatorHasher metacycHasher;

    private int start = 398820;
    private int limit = 9999999;

    //stalls:  69983, 134776, 186312, 216170, 294130, 311583, 321949, 329219, 344388
    //termination: 303892, 387536

    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        CrawlAndAbstract abstractor = new CrawlAndAbstract();
        abstractor.initiate();
        abstractor.flowAllReactions();
    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        simplifier = SimpleReactionFactory.generate(api);
        List<String> names = simplifier.getCofactorNames();

        try {
            brendaHasher = OperatorHasher.deserialize("output/brenda_hash.ser");
            metacycHasher = OperatorHasher.deserialize("output/metacyc_hash.ser");
        } catch(Exception err) {
            brendaHasher = new OperatorHasher(names);
            metacycHasher = new OperatorHasher(names);
        }
    }

    private void flowAllReactions() throws Exception {
        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        int count = 0;
        outer: while(iterator.hasNext()) {
            count++;
            try {
                Reaction rxn = iterator.next();

                if(count < start) {
                    continue;
                }

                System.out.println("count:" + count);
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future = executor.submit(new RunManager(rxn));

                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                }

                executor.shutdownNow();

                //Serialize the hashers
                brendaHasher.serialize("output/brenda_hash.ser");
                metacycHasher.serialize("output/metacyc_hash.ser");
            } catch (Exception err) {
            }

            if(count > limit) {
                break;
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

//        if(blockList.contains(rxnID)) {
//            System.out.println("blocked");
//            return;
//        }

        //Calculate the CHANGING RO
        try {
            RxnMolecule ro = new ROExtractor().calcCRO(reaction);
            index(rxn, ro, srxn, rxnID);
            System.out.print(" .");
        } catch(Exception err) {
            System.out.print(" x");
        }

        //Calculate the skeleton RO
        try {
            RxnMolecule ro = new SkeletonMapper().calcCRO(reaction);
            index(rxn, ro, srxn, rxnID);
            System.out.println(" .");
        } catch(Exception err) {
            System.out.println(" x");
        }
    }

    private void index(Reaction rxn, RxnMolecule ro, SimpleReaction srxn, int rxnID) {
        //Index the ro
        Set<String> subCo = srxn.subCofactors;
        Set<String> prodCo = srxn.prodCofactors;
        String subro = ChemAxonUtils.toInchi(ro.getReactant(0));
        String prodro = ChemAxonUtils.toInchi(ro.getProduct(0));

        //Index the mock data
        Reaction.RxnDataSource source = rxn.getDataSource();
        if(source.equals(Reaction.RxnDataSource.BRENDA)) {
            brendaHasher.index(subro, prodro, subCo, prodCo, rxnID);
        } else if(source.equals(Reaction.RxnDataSource.METACYC)) {
            metacycHasher.index(subro, prodro, subCo, prodCo, rxnID);
        }
    }
}
