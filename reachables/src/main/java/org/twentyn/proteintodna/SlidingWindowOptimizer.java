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
 * This is in concept similar to GeneOptimizer, but a fresh re-write.  It considers
 * the first 42 bp as "special" requiring checks for hairpins.  It chooses RBS in a
 * manner similar to RBSChooser2 starting with a list of well-expressing RBS's and 
 * initial 18 bp.  It differs in that it considers alternate fill-in codons chosen
 * to eliminate secondary structure.  The remaining codons in the sequence are done
 * by a more conventional CAI process.  Throughout, forbidden sequences are eliminated.
 * 
 * @author jca20n
 */
public class SlidingWindowOptimizer {
    
    private Translator translator;
    private CodonIndexer indexer;
    private SequenceChecker checker;
    private RBSChooser3 rbsChooser;
    private HairpinCounter haircounter;
    
    private SlidingWindowOptimizer() {}
    
    public static SlidingWindowOptimizer initiate() throws Exception {
        SlidingWindowOptimizer swo = new SlidingWindowOptimizer();
        swo.translator = new Translator();
        swo.rbsChooser = RBSChooser3.initiate();
        swo.indexer = CodonIndexer.initiate();
        swo.checker = new SequenceChecker();
        swo.haircounter = new HairpinCounter();
        return swo;
    }
    
    public Mrna mRNAconstruct(String peptide, Set<RBSOption> ignores) throws Exception {
        //Initialize the mRNA with the peptide and the "best" codon for each aa
        Mrna out = new Mrna();
        out.peptide = peptide;
        out.codons = new int[peptide.length()];
        for(int x=0; x<out.codons.length; x++) {
            out.codons[x] = 0;
        }
        
        //Choose the least edit distance RBS
        out.rbs = rbsChooser.choose(peptide, ignores);
        
        //Optimize while eliminating secondary structure and removing forbidden sites
        optimizeORF(out);

        return out;
    }

    /**
     * This is the codon selection algorithm
     * It scans from N to C terminus of the peptide in 6 amino acid windows
     * 
     * * It permutes all ~2^6 codon options (64 typically, no more than 4096)
     * options for that peptide
     * * It eliminates all peptides that when combined with the previous 8 bp
     * causes a forbidden sequence
     * * It scores the remaining for hairpin count and elminates all but the
     * lowest secondary structure options
     * * It returns the first (and thus highest total CAI) solution
     * * It then slides over 3 amino acids, and thus only the first 9 bp from
     * the previous optimization are retained in the next iteration
     * 
     * @param mrna
     * @throws Exception 
     */
    private void optimizeORF(Mrna mrna) throws Exception {
        //Make the mRNA divisible by 3 by adding stop codons
        int stopcount = mrna.peptide.length() % 3 + 6;
        
        //Create temporary codon arrays to hold new CDS
        String protein = mrna.peptide;
        int[] codonIndices = new int[mrna.codons.length + stopcount];
        String[] codonValues = new String[mrna.codons.length + stopcount];
        
        //Fill in the extra stop codons
        for(int i=0; i<stopcount; i++) {
            protein+="*"; //Add stops to the peptide
            codonIndices[mrna.codons.length + i] = 0;
            codonValues[mrna.codons.length + i] = "TAA";
        }

        // TODO: Saurabh says: More optimizations: The loop below takes about 100ms on average
        //       for each peptide sequence. We have heavily optimized the `for(Integer[] option : encodingOptions)`
        //       loop. But there are still other opportunities to shave away time. 
        //       One thought: Memoize the computation for unique `aas6`s. There are 20^6 = 64M possibilities,
        //                    and this inner loop runs 10,896 times when a 16 paths (5 orf's each) and because
        //                    we run combinations, each protein is optimized multiple times! So an additional
        //                    memoization should be done at the higher level to optimize each orf only once
        //                    and again and again in pathways.

        //Scan through peptide 3 amino acids at a time
        for(int i=0; i<protein.length()-6; i=i+3) {
            //Pull out the next 6 amino acids as array
            char[] aas6 = new char[6];
            for(int x=0; x<6; x++) {
                aas6[x] = protein.charAt(i+x);
            }
            
            //Pull out the count of codon options for each amino acid
            int[] diversity6 = new int[6];
            for(int x=0; x<6; x++) {
                List<String> codons = indexer.aminoAcidToBestCodons.get(aas6[x]);
                diversity6[x] = codons.size();                      
            }
            
            //Permute all codons for next 6 amino acids as encodingOptions
            List<Integer[]> encodingOptions = new ArrayList<>(); 
            
            for(int p0=0; p0<diversity6[0]; p0++) {
                for(int p1=0; p1<diversity6[1]; p1++) {
                    for(int p2=0; p2<diversity6[2]; p2++) {
                        for(int p3=0; p3<diversity6[3]; p3++) {
                            for(int p4=0; p4<diversity6[4]; p4++) {
                                for(int p5=0; p5<diversity6[5]; p5++) {
                                    Integer[] option = new Integer[6];
                                    option[0] = p0;
                                    option[1] = p1;
                                    option[2] = p2;
                                    option[3] = p3;
                                    option[4] = p4;
                                    option[5] = p5;
                                    encodingOptions.add(option);
                                }
                            }
                        }
                    }
                }
            }
            
            //Compute the preamble sequence (the previous 3 amino acids)
            String preamble = "";
            
            //If this is the first iteration, the preamble is the rbs
            if(i==0) {
                preamble = mrna.rbs.rbs.toUpperCase();
            
            //Otherwise it is the previous 3 codons
            } else {
                for(int p=i-3; p<i; p++) {
                    preamble += codonValues[p];
                }
            }
            
            //Choose the best option, checking for forbidden sequence and count hairpins
            Integer[] bestOption = null;
            double bestscore = 99999999;
            
            for(Integer[] option : encodingOptions) {
                //Construct the sequence for the option
                String sequence = preamble;
                
                for(int x=0; x<6; x++) {
                    char aa = aas6[x];
                    List<String> codons = indexer.aminoAcidToBestCodons.get(aa);
                    String codon = codons.get(option[x]);
                    sequence += codon;
                }
                
                //Apply sequenceChecker to the sequence, abort if it is forbidden
                boolean checked = checker.check(sequence);

                if(checked) {
                  //See if it is better with hairpins than the previous, if so update
                  double score = haircounter.score(sequence);

                  if(score < bestscore) {
                      bestscore = score;
                      bestOption = option;
                  }
                }
            }

            //Update the CDS with the bestOption
            for(int x=0; x<6; x++) {
                codonIndices[i+x] = bestOption[x];
                char aa = protein.charAt(i+x);
                codonValues[i+x] = indexer.aminoAcidToBestCodons.get(aa).get(bestOption[x]);
            }
            
        }

        //Update the mRNA with the optimized codons
        for(int i=0; i<mrna.codons.length; i++) {
            mrna.codons[i] = codonIndices[i];
        }
    }


    public static void main(String[] args) throws Exception {
        String peptide = "MRKGEELFTGVVPILVELDGDVNGHKFSVSGEGEGDATYGKLTLKFICTTGKLPVPWPTLVTTFGYGVQCFARYPDHMKQHDFFKSAMPEGYVQERTIFFKDDGNYKTRAEVKFEGDTLVNRIELKGIDFKEDGNILGHKLEYNYNSHNVYIMADKQKNGIKVNFKIRHNIEDGSVQLADHYQQNTPIGDGPVLLPDNHYLSTQSALSKDPNEKRDHMVLLEFVTAAGITHGMDELYK";
        
        //Create an optimized mRNA for the peptide
        SlidingWindowOptimizer swo = SlidingWindowOptimizer.initiate();
        Set<RBSOption> ignores = new HashSet<>();
        
            long start = System.currentTimeMillis();
        Mrna mrna = swo.mRNAconstruct(peptide, ignores);
            long end = System.currentTimeMillis();
            long duration = end - start;
            double seconds = duration/1000.0;
            System.out.println("Took this long in seconds: " + seconds);
        
        String seq = mrna.toSeq();
        System.out.println(seq);
        
        //Check the translation
        String cds = seq.substring(mrna.rbs.rbs.length(), seq.length()-3);
        String newpep = swo.translator.translate(cds);
        if(newpep.equals(peptide)) {
            System.out.println("Translation ok");
        } else {
            throw new Exception();
        }
    }
}
