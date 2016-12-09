/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.twentyn.proteintodna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author jca20n
 */
public class CodonIndexer {
    private static final double limit = 3.0;
    Map<String,Map<String,Integer>> peptideToCodons = new HashMap<>();
    Map<Character, List<String>> aminoAcidToBestCodons = new HashMap<>();
    
    SequenceChecker checker = new SequenceChecker();
    Translator translator = new Translator();
    
    private static CodonIndexer singleton;
    
    private CodonIndexer() {
        
    }
    
    public static CodonIndexer initiate() throws Exception {
        if(singleton!=null) {
            return singleton;
        }
        
        singleton = new CodonIndexer();
        
        //Gather up all the orfs with greater copy than the limit
        List<String> orfs = new ArrayList<>();
        String data = FileUtils.readFile2("/Volumes/shared-data/Vijay/CodonOptimization/coli_genes.txt");
        String[] lines = data.split("\\r|\\r?\\n");
        for(String line : lines) {
            try {
                String[] tabs = line.split("\t");
                double copy = Double.parseDouble(tabs[7]);
                if(copy > limit) {
                    orfs.add(tabs[6].toUpperCase());
                }
            } catch(Exception err) {
                continue;
            }
        }
        
        //Index each orf
        for(String orf : orfs) {
            //Iterate through orf starting with each codon
            for(int i=0; i<orf.length(); i=i+3) {
                //Do 1 codon
                try {
                    String cds = orf.substring(i, i+3);
                    String peptide = singleton.translator.translate(cds);
                    singleton.index(peptide, cds);
                } catch(Exception err) {}
                
                //Do 2 codons
                try {
                    String cds = orf.substring(i, i+6);
                    String peptide = singleton.translator.translate(cds);
                    singleton.index(peptide, cds);
                } catch(Exception err) {}
                
                //Do 3 codons
                try {
                    String cds = orf.substring(i, i+9);
                    String peptide = singleton.translator.translate(cds);
                    singleton.index(peptide, cds);
                } catch(Exception err) {}
                
                //Do 4 codons
                try {
                    String cds = orf.substring(i, i+12);
                    String peptide = singleton.translator.translate(cds);
                    singleton.index(peptide, cds);
                } catch(Exception err) {}
                
                //Do 5 codons
                try {
                    String cds = orf.substring(i, i+15);
                    String peptide = singleton.translator.translate(cds);
                    singleton.index(peptide, cds);
                } catch(Exception err) {}
                
                //Do 6 codons
                try {
                    String cds = orf.substring(i, i+18);
                    String peptide = singleton.translator.translate(cds);
                    singleton.index(peptide, cds);
                } catch(Exception err) {}
                
            }
        }
        
        String aas = "ACDEFGHIKLMNPQRSTVWY";
//        System.out.println("This many aas: " + aas.length());
        
        for(int i=0; i<aas.length(); i++) {
            char aa = aas.charAt(i);
            List<String> codons = new ArrayList<>();
            
            //Find the best codon count
            Map<String,Integer> codonToCount = singleton.peptideToCodons.get("" + aa);
            int best = 0;
            for(String codon : codonToCount.keySet()) {
                Integer count = codonToCount.get(codon);
                if(count > best) {
                    best = count;
                }
            }
            
            //Include only those codons with at least 25% the count of best codon
            for(String codon : codonToCount.keySet()) {
                Integer count = codonToCount.get(codon);
                if(count > 0.25* best) {
                    codons.add(codon);
                }
            }
            
            //If only one codon survived, include the next best
            if(codons.size() == 1) {
                String nextBest = singleton.getNextBestSeq("" + aa);
                if(nextBest !=null) {
                    codons.add(nextBest);
                }
            }
            
            //Sort the codons in the list
            if(codons.size() > 1) {
                outer: while(true) {
                    for(int x=0; x<codons.size()-1; x++) {
                        String codon1 = codons.get(x);
                        String codon2 = codons.get(x+1);
                        int count1 = codonToCount.get(codon1);
                        int count2 = codonToCount.get(codon2);
                        if(count2>count1) {
                            codons.remove(x+1);
                            codons.add(x, codon2);
                            continue outer;
                        }
                    }
                    break;
                }
            }
            
            singleton.aminoAcidToBestCodons.put(aa, codons);
        }
        
        //Put in  TAA or TGA for a stop
        List<String> stops = new ArrayList<>();
        stops.add("TAA");
        stops.add("TGA");
        singleton.aminoAcidToBestCodons.put('*', stops);
        
        return singleton;
    }

    private void index(String peptide, String cds) {
        Map<String, Integer> amap = peptideToCodons.get(peptide);
        if (amap == null) {
            amap = new HashMap<>();
        }
        Integer count = amap.get(cds);
        if(count == null) {
            count = 0;
        }
        count++;
        
        if(cds.length() >= 6) {
            if(checker.check(cds)==false) {
                return;
            }
        }
        
        amap.put(cds, count);
        peptideToCodons.put(peptide,amap);
    }
    
    public String getBestSeq(String peptide) {
        String best = null;
        int bestscore = -9999;
        try {
            Map<String, Integer> amap = peptideToCodons.get(peptide);
            
            for(String cds : amap.keySet()) {
                Integer score = amap.get(cds);
                if(score > bestscore) {
                    best = cds;
                    bestscore = score;
                }
            }
        } catch(Exception err) {
            return null;
        }
        return best;
    }
    
    public String getNextBestSeq(String peptide) {
        String best = null;
        String nextbest = null;
        int bestscore = -9999;
        try {
            Map<String, Integer> amap = peptideToCodons.get(peptide);
            
            for(String cds : amap.keySet()) {
                Integer score = amap.get(cds);
                if(score > bestscore) {
                    nextbest = best;
                    best = cds;
                    bestscore = score;
                }
            }
        } catch(Exception err) {
            return null;
        }
        return nextbest;
    }
    
    public static void main(String[] args) throws Exception {
        CodonIndexer indexer = CodonIndexer.initiate();
        
        //Test a dipeptide with lots of diversity
        String seq = indexer.getBestSeq("AA");  //best is GCGGCG with score 66
        System.out.println("Best for AA: " + seq);
        
        //Print out best codons per aa
        for(Character aa : indexer.aminoAcidToBestCodons.keySet()) {
            System.out.print(aa + ": ");
            List<String> codons = indexer.aminoAcidToBestCodons.get(aa);
            for(String codon : codons) {
                System.out.print(codon + ", ");
            }
            System.out.println();
        }
        
        System.out.println("done");
        
    }

    public Set<String> getAllSeq(String peptide) {
        Set<String> out = new HashSet<String>();
        
        //Add all valid codon options
        for(int x=1; x<=6; x++) {
            String pep = peptide.substring(0,x);
            String bestcodon = getBestSeq(pep);
            out.add(bestcodon);
            String secondbest = getNextBestSeq(pep);
            out.add(secondbest);
        }
        
        out.remove(null);
        return out;
    }

}
