package com.act.biointerpretation.operators;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.common.util.Pair;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.cofactors.SimpleReactionFactory;
import com.act.biointerpretation.utils.ChemAxonUtils;

import java.util.*;

/**
 * Created by jca20n on 11/9/15.
 */
public class CrawlAndAbstract {

    private NoSQLAPI api;
    private SimpleReactionFactory simplifier;
    private OperatorHasher hasher;
    private int limit = 9999000;
    Set<Integer> blockList;

    public static void main(String[] args) {
        ChemAxonUtils.license();

        CrawlAndAbstract abstractor = new CrawlAndAbstract();
        abstractor.initiate();
        abstractor.flowAllReactions();
    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        simplifier = SimpleReactionFactory.generate(api);
        hasher = new OperatorHasher(simplifier.getCofactorNames());

        //These are specific datapoints that put the algorithm in a loop, use to debug
        blockList = new HashSet<>();
        blockList.add(1625);
        blockList.add(1684);
        blockList.add(3294);
        blockList.add(3878);
        blockList.add(5247);
        blockList.add(5321);
        blockList.add(7841);
        blockList.add(8621);
        blockList.add(14888);
        blockList.add(16466);
        blockList.add(17746);
        blockList.add(18180);

        //700000 up
        blockList.add(700278);
        blockList.add(732210);
    }

    private void flowAllReactions() {
//        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
//        int count = 0;
//        outer: while(iterator.hasNext()) {
        for(long count = 732210; count < 9999000; count++) {
            try {
//                Reaction rxn = iterator.next();
                Reaction rxn = api.readReactionFromInKnowledgeGraph(count);
                processOne(rxn);
            } catch (Exception err) {
            }

            count++;
            if(count > limit) {
                break;
            }
        }

        //Count everything up and sort
        List<Map.Entry<Pair<String,String>, Integer>> ranked = hasher.rank();

        //Print the ranked results
        for(Map.Entry<Pair<String,String>, Integer> entry : ranked) {
            int num = entry.getValue();
            Pair<String,String> pair = entry.getKey();
            System.out.println(pair.left() + "," + pair.right() + " : " + num);
        }
    }



    private void processOne(Reaction rxn) throws Exception {
        System.out.println("id:" + rxn.getUUID());
        SimpleReaction srxn = simplifier.simplify(rxn);
        RxnMolecule reaction = srxn.getRxnMolecule();
        int rxnID = rxn.getUUID();

        if(blockList.contains(rxnID)) {
            return;
        }

        //Calculate the CHANGING RO
        try {
            RxnMolecule ro = new ROExtractor().calcCRO(reaction);
            index(ro, srxn, rxnID);
            System.out.print(" .");
        } catch(Exception err) {
            System.out.print(" x");
        }

        //Calculate the skeleton RO
        try {
            RxnMolecule ro = new SkeletonMapper().calcCRO(reaction);
            index(ro, srxn, rxnID);
            System.out.println(" .");
        } catch(Exception err) {
            System.out.println(" x");
        }
    }

    private void index(RxnMolecule ro, SimpleReaction srxn, int rxnID) {
        //Index the ro
        Set<String> subCo = srxn.subCofactors;
        Set<String> prodCo = srxn.prodCofactors;
        String subro = ChemAxonUtils.toInchi(ro.getReactant(0));
        String prodro = ChemAxonUtils.toInchi(ro.getProduct(0));;

        //Index the mock data
        hasher.index(subro, prodro, subCo, prodCo, rxnID);
    }
}
