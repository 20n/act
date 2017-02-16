package org.twentyn.proteintodna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Construct {
    public List<Mrna> mRNAs;
    public String promoter;
    public String terminator;
    
    public String toSeq() {
        StringBuilder out = new StringBuilder();
        out.append(promoter);
        for(Mrna mrna : mRNAs) {
            try {
                out.append(mrna.toSeq());
            } catch (Exception e) {

            }
        }
        out.append(terminator);
        return out.toString();
    }
}
