package org.twentyn.proteintodna;

import java.util.List;

public class Mrna {
    public RBSOption rbs;
    public String peptide;
    public int[] codons;
    
    public String toSeq() throws Exception {
        CodonIndexer indexer = CodonIndexer.initiate();
        
        StringBuilder out = new StringBuilder();
        for(int i=0; i<peptide.length(); i++) {
            char aa = peptide.charAt(i);
            List<String> codonList = indexer.aminoAcidToBestCodons.get(aa);
            out.append(codonList.get(codons[i]));
        }
        out.append("TAA");
            
        return rbs.rbs.toLowerCase() + out.toString().toUpperCase();
    }
}
