package org.twentyn.proteintodna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * * Spacing between arms of the hairpin can be anywhere from 4 to 9, and are
 * considered to be of equal weight
 * 
 * * a match of 2bp is 100x, 3bp is 1000x, etc., so the length of the match
 * of the arms is log-scale dependence
 * 
 * * G:C is 3 HBonds; A:T is 2 HBonds; G:U is 2 HBonds; these are scored linearly
 */
public class HairpinCounter {
    public static void main(String[] args) throws Exception {
        HairpinCounter hc = new HairpinCounter();
        double d = hc.score("AACCCCCAAAAAAAAAGGGGGAAAAAAAAAAAAA");
        System.out.println("score: " + d);
    }

    public double score(String seq) throws Exception {
        seq = seq.toUpperCase();
        char[] seqRevC = SequenceUtils.reverseComplement(seq).toCharArray();
        char[] s = seq.toCharArray();
        int len = seq.length();
        double out = 0.0;
        //For each spacer length between 4 and 9
        for(int spaces = 4; spaces <= 9; spaces++) {
            //scan through the sequence and test each potential hairpin
            for(int i=0; i<len-spaces-12; i++) {
                // String hairpin = seq.substring(i, i+spaces+12);
                int hbonds = countHbonds(s, i, i+spaces+12, seqRevC, len);
                // int hbondsInefficient = countHbonds(hairpin);
                // if (hbondsInefficient != hbonds)
                //   throw new Exception("not equal: " + hbonds + " <> " + hbondsInefficient);
                out+= Math.pow(2, hbonds);
            }
            
        }
        
        return out;
    }
    
    private int countHbonds(String hairpin) {
        String prefix = hairpin.substring(0,6);
        String suffix = hairpin.substring(hairpin.length()-6);
        String prerev = SequenceUtils.reverseComplement(prefix);
        
        //See how many out of the six being examined match
        int matchlength = 0;
        for(int i=0; i<6; i++) {
            if(prerev.charAt(i)==suffix.charAt(i)) {
                matchlength = i;
            } else {
                break;
            }
        }
        
        if(matchlength <3) {
            return 0;
        }
        
        //For the ones that match, score them for A, T, C, G
        int hbonds = 0;
        for(int i=0; i<matchlength; i++) {
            char achar = prerev.charAt(i);
            if(achar == 'C' || achar == 'G') {
                hbonds+=3;
            }
            if(achar == 'A' || achar == 'T') {
                hbonds+=2;
            }
        }
        
        return hbonds;
    }

    private int countHbonds(char[] seq, int startInc, int endExcl, char[] revC, int len) {

        // This code is an optimized version of the code that used to take in a String `hairpin`
        //    where the `hairpin` was a substring of an origin `seq`
        // Instead now the caller just passing in the origin `seq` and `startInc` and `endExcl`
        //    indices into `seq`. That way we do not recreate small substrings over and over.
        // What that means then is that we have to do some indexing to locate the right `prefix`
        //    and `suffix` that the original code needed. They are now translated as follows:
        //
        // *** prefix = hairpin.substring(0,6)
        //      prefix now goes from `startInc` 6 chars ahead, i.e., seq [startInc, startInc + 6]
        // *** suffix = hairpin.substring(hairpin.length()-6)
        //      suffix goes from end index `endExcl` and 6 chars before, i.e., seq [endExcl - 6, endExcl]
        // *** revComplement(prefix) = SequenceUtils.reverseComplement(prefix)
        //      rev complement is a little funky. we pass in the reverse complement of `seq` as `revC`
        //      but now to locate the reverse complement of `hairpin` we have to subtract its indices from
        //      `len-1`, and then since we will be reading it in reverse (and always 6 chars wide),
        //      the index of `i` will be at `len - 1 - (startInc + 5 - i)`! Apologies, but that is what it is!
        //      revComplement(prefix)[i] == revC[len-1 - (startInc+5-i)]

        int suffixStart = endExcl - 6;
        //See how many out of the six being examined match
        int matchlength = 0;
        for(int i=0; i<6; i++) {
            // now we need to check `suffix.charAt(i) == revComplement(prefix).charAt(i) == suffix.charAt(i)`
            // suffix.chatAt(i) is index `suffixStart + i`
            // revComplement(prefix).charAt(i) is more complicated, but as explained above.
            char revChar = revC[len - 1 - startInc - 5 + i];
            if (seq[suffixStart + i] == revChar) {
                matchlength = i;
            } else {
                break;
            }
        }

        if(matchlength <3) {
            return 0;
        }

        int hbonds = 0;
        //For the ones that match, score them for A, T, C, G
        for(int i=0; i<matchlength; i++) {
            // compute metrics on revComplement(prefix).charAt(i); get that char using indexing as above
            char achar = revC[len - 1 - startInc - 5 + i];
            if(achar == 'C' || achar == 'G') {
                hbonds+=3;
            }
            if(achar == 'A' || achar == 'T') {
                hbonds+=2;
            }
        }

        return hbonds;
    }

}
