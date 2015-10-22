package com.act.biointerpretation.step2_desalting;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;

import java.util.*;

/**
 * This creates Synapse from Dr. Know.  Synapse is the database in which the chemicals
 * have been inspected for containing multiple species or ionized forms, and corrected.
 *
 * Created by jca20n on 10/22/15.
 */
public class ReactionDesalter {
    private NoSQLAPI api;
    private Desalter desalter;

    public static void main(String[] args) {
        ReactionDesalter runner = new ReactionDesalter();
        runner.run();
    }

    public ReactionDesalter() {
        NoSQLAPI.dropDB("synapse");
        this.api = new NoSQLAPI("drknow", "synapse");
        this.desalter = new Desalter();
    }

    public void run() {
        System.out.println("Starting ReactionDesalter");

        //Populate the hashmap of duplicates keyed by a hash of the reactants and products
        long start = new Date().getTime();

        long end = new Date().getTime();

    }

}
