/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.twentyn.proteintodna;

import java.util.List;

/**
 *
 * @author jca20n
 */
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
