package com.act.biointerpretation.metadata;

import java.util.List;
import java.util.Map;

public class ProteinMetadata {
    //These are strict requirements that these be ok
    Long reactionId;
    List<Long> sequences;
    Map<Host, Integer> cloned;
    Double kcatkm;
    Double specificActivity;

    //These things should be downranked but not dealbreakers, they are fixable
    Map<Host, Localization> localization;
    Boolean heteroSubunits;
    Boolean modifications;

    public void setReactionId(Long reactionId) {
        this.reactionId = reactionId;
    }

    public boolean isValid(Host host) {
        if (sequences.isEmpty()) {
            return false;
        }
        //Don't bother if it's a really bad enzyme
        if(kcatkm != null && kcatkm < 1.0) {
            //Not in the top 80% of proteins, and anecdotally the bare minimum activity limit
            return false;
        }

        //Don't bother if it's a really bad enzyme
        if(specificActivity != null && specificActivity < 0.3) {
            //Not in the top 80% of proteins
            return false;
        }

        //Don't bother if there is a negaative observation about the protein working in the host
        Integer clonedval = cloned.get(host);
        if(clonedval != null && clonedval < 0) {
            return false;
        }

        return true;
    }
}
