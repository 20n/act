package com.act.biointerpretation;

import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDB;
import act.shared.Reaction;

public class Cleanup {
    private MongoDB db;

    Cleanup() {
        this.db = new MongoDB("localhost", 27017, "actv01");
    }

    private void testIterateOverRxns() {
        DBIterator iterator = this.db.getIteratorOverReactions(true);
	    Reaction r;
        while ((r = this.db.getNextReaction(iterator)) != null) {
            System.out.format("rxn: %d: %s\n", r.getUUID(), r.getReactionName());
        }
    }

    public static void main(String[] args) {
        new Cleanup().testIterateOverRxns();
    }
}
