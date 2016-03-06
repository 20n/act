package com.act.biointerpretation.synthesis;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;

import java.util.Iterator;

/**
 * Created by jca20n on 2/20/16.
 */
public class Sandbox {
    public static void main(String[] args) {
        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");
        Chemical paba = api.readChemicalFromInKnowledgeGraph("InChI=1S/C7H7NO2/c8-6-3-1-5(2-4-6)7(9)10/h1-4H,8H2,(H,9,10)");
        Chemical pap = api.readChemicalFromInKnowledgeGraph("InChI=1S/C6H7NO/c7-5-1-3-6(8)4-2-5/h1-4,8H,7H2");

        long pabaId = paba.getUuid();
        long papId = pap.getUuid();
//        System.out.println(paba.getUuid());
//        System.out.println(pap.getUuid());

        Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Reaction rxn = iterator.next();
            Long[] prods = rxn.getProducts();
            for(long along : prods) {
                if(along == papId) {
                    System.out.println(rxn.getUUID());
                }
            }
        }
    }
}
