package com.act.biointerpretation.utils;

import act.api.NoSQLAPI;
import act.shared.Reaction;

/**
 * Created by jca20n on 9/29/16.
 */
public class ReadReaction {
    public static void main(String[] args) {
        long id = 7374;

        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");
        Reaction rxn = api.readReactionFromInKnowledgeGraph(id);
        System.out.println(rxn.toStringDetail());

    }
}
