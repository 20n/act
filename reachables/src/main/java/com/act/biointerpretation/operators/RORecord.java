package com.act.biointerpretation.operators;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by jca20n on 1/4/16.
 */
public class RORecord implements Serializable {

        private static final long serialVersionUID = -331894164438402271L;

    //Fields loaded from curated data
    Boolean isTrim;  //Whether or not the curation indicates that the RO should be trimmed, or null
    Set<Integer> expectedRxnIds;  //The IDs observed in the original hcERO generation for this RO

    //Fields populated during projection of ROs
    boolean trimResult; //After projection and analysis, whether this RO should be trimmed out
    Set<Integer> projectedRxnIds = new HashSet<>(); //The RxnIDs from test set that are projected correctly by this RO

    String hcERO;
}
