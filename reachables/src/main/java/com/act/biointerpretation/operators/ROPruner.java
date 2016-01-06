package com.act.biointerpretation.operators;

/**
 * Created by jca20n on 1/6/16.
 */
public class ROPruner {
    private TestSetCrossROs tests;

    public static void main(String[] args) throws Exception {
        TestSetCrossROs tests = TestSetCrossROs.deserialize("output/TestSetCrossROs.ser");

        ROPruner pruner = new ROPruner(tests);
        pruner.prune();
        pruner.tests.serialize("output/TestSetCrossROs_ROPruner.ser");
    }

    public ROPruner(TestSetCrossROs tests) {
        this.tests = tests;
    }

    public void prune() {
        for(RORecord rec1 : tests.ros) {
            for(RORecord rec2 : tests.ros) {
                if(rec1==rec2) {
                    continue;
                }

                //If the rxnIds of another RO are a superset of all rec1's rxnIds, then it should be trimmed
                if(rec2.projectedRxnIds.containsAll(rec1.projectedRxnIds)) {
                    rec1.trimResult = true;
                    break;
                }
            }
        }
    }

}
