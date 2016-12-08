package com.act.biointerpretation.metadata;

import java.util.Comparator;
import java.util.Map;

/**
 * Created by jca20n on 12/7/16.
 */
public class ProteinMetadata implements Comparator {
    String peptide;

    //These are strict requirements that these be ok
    Map<Host, Boolean> cloned;
    Double kcatkm;
    Double specificActivity;

    //Thgse things should be downranked but not dealbreakers
    Localization localization;
    Boolean heteroSubunits;
    Boolean modifications;


    public boolean isValid(Host host, Localization localization) {
        if(kcatkm < 1.0) {   //Not in the top 80% of proteins, and anecdotally the bare minimum activity limit
            return false;
        }
        if(specificActivity < 0.3) {  //Not in the top 80% of proteins
            return false;
        }
//        if(cloned == false) {
//            return false;
//        }

        return true;
    }

    @Override
    public int compare(Object o1, Object o2) {
        ProteinMetadata p1 = (ProteinMetadata) o1;
        ProteinMetadata p2 = (ProteinMetadata) o2;

        //Return 0 if everything is the same
        if(p1.kcatkm - p2.kcatkm < 0.0001) {
            if(p1.specificActivity -p2.specificActivity < 0.0001) {
                if(p1.localization.equals(p2.localization)) {
                    if(p1.heteroSubunits == p2.heteroSubunits) {
                        return 0;
                    }
                }
            }
        }

        if(heteroSubunits == false) {  //If the protein has partners, it is downranked
            return 0;
        }

        if(modifications == false) {  //If the protein has partners, it is downranked
            return 0;
        }

        //Return pos if p1 > p2

        return 0;
    }
}
