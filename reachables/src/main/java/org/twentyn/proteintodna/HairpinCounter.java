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
 * * Spacing between arms of the hairpin can be anywhere from 4 to 9, and are
 * considered to be of equal weight
 * 
 * * a match of 2bp is 100x, 3bp is 1000x, etc., so the length of the match
 * of the arms is log-scale dependence
 * 
 * * G:C is 3 HBonds; A:T is 2 HBonds; G:U is 2 HBonds; these are scored linearly
 * 
 * @author jca20n
 */
public class HairpinCounter {
    public static void main(String[] args) throws Exception {
        HairpinCounter hc = new HairpinCounter();
        double d = hc.score("AACCCCCAAAAAAAAAGGGGGAAAAAAAAAAAAA");
        System.out.println("score: " + d);
    }

    public double score(String seq) throws Exception {
        seq = seq.toUpperCase();
        double out = 0.0;
        //For each spacer length between 4 and 9
        for(int spaces = 4; spaces <= 9; spaces++) {
            //scan through the sequence and test each potential hairpin
            for(int i=0; i<seq.length()-spaces-12; i++) {
                String hairpin = seq.substring(i, i+spaces+12);
                int hbonds = countHbonds(hairpin);
//                if(hbonds > 0) {
//                    System.out.println();
//                }
//                System.out.println(hbonds);
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
}
