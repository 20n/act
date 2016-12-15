package com.act.biointerpretation.metadata;

import java.util.Comparator;
import java.util.Map;

/**
 * Created by jca20n on 12/7/16.
 */
public class ProteinMetadata {
    //These are strict requirements that these be ok
    Map<Host, Integer> cloned;
    Double kcatkm;
    Double specificActivity;

    //Thgse things should be downranked but not dealbreakers, they are fixable
    Map<Host, Localization> localization;
    Boolean heteroSubunits;
    Boolean modifications;


    public boolean isValid(Host host) {
        //Don't bother if it's a really bad enzyme
        if(kcatkm != null && kcatkm < 1.0) {   //Not in the top 80% of proteins, and anecdotally the bare minimum activity limit
            return false;
        }

        //Don't bother if it's a really bad enzyme
        if(specificActivity != null && specificActivity < 0.3) {  //Not in the top 80% of proteins
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
