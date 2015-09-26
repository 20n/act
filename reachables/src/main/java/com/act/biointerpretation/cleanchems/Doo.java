package com.act.biointerpretation.cleanchems;

import act.shared.Chemical;
import act.shared.Reaction;

import java.util.HashSet;
import java.util.Set;

/**
 * A Doo is a management object that tracks the progress
 * Created by jca20n on 9/25/15.
 */
public class Doo {
    private Doo parentDoo;
    private long chemical;
    private long reaction;
    private String label;

    //Make this non-static at some point
    static Set<Doo> hopper = new HashSet<>();

    public Doo(Doo parent, String label) {
        this(parent, label, parent.chemical, parent.reaction);
    }

    public Doo(Doo parent, String label, Chemical achem, Reaction rxn) {
        this(parent, label, achem.getUuid(), rxn.getUUID());
    }

    private Doo(Doo parent, String label, long chemid, long rxnid) {
        this.chemical = chemid;
        this.reaction = rxnid;
        this.label = label;
        this.parentDoo = parent;
        hopper.add(this);
    }

    public void close() {
        hopper.remove(this);
    }
}
