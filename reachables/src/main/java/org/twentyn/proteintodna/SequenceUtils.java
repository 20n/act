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
 * Utility methods for common molecular biology operations
 *
 * @author jca20n
 */
public class SequenceUtils {

    public static String complement(String rbsrc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rbsrc.length(); i++) {
            char achar = rbsrc.charAt(i);
            if (achar == 'A') {
                sb.append("T");
            } else if (achar == 'T') {
                sb.append("A");
            } else if (achar == 'C') {
                sb.append("G");
            } else if (achar == 'G') {
                sb.append("C");
            } else if (achar == 'a') {
                sb.append("t");
            } else if (achar == 't') {
                sb.append("a");
            } else if (achar == 'c') {
                sb.append("g");
            } else if (achar == 'g') {
                sb.append("c");
            }
        }
        return sb.toString();
    }

    public static String reverseComplement(String rbsrc) {
        StringBuilder sb = new StringBuilder();
        for (int i = rbsrc.length() - 1; i >= 0; i--) {
            char achar = rbsrc.charAt(i);
            if (achar == 'A') {
                sb.append("T");
            } else if (achar == 'T') {
                sb.append("A");
            } else if (achar == 'C') {
                sb.append("G");
            } else if (achar == 'G') {
                sb.append("C");
            } else if (achar == 'a') {
                sb.append("t");
            } else if (achar == 't') {
                sb.append("a");
            } else if (achar == 'c') {
                sb.append("g");
            } else if (achar == 'g') {
                sb.append("c");
            } else if (achar == 'B') {
                sb.append("V");
            } else if (achar == 'D') {
                sb.append("H");
            } else if (achar == 'H') {
                sb.append("D");
            } else if (achar == 'K') {
                sb.append("M");
            } else if (achar == 'N') {
                sb.append("N");
            } else if (achar == 'R') {
                sb.append("Y");
            } else if (achar == 'S') {
                sb.append("S");
            } else if (achar == 'V') {
                sb.append("B");
            } else if (achar == 'W') {
                sb.append("W");
            } else if (achar == 'Y') {
                sb.append("R");
            } else if (achar == 'b') {
                sb.append("v");
            } else if (achar == 'd') {
                sb.append("h");
            } else if (achar == 'h') {
                sb.append("d");
            } else if (achar == 'k') {
                sb.append("m");
            } else if (achar == 'n') {
                sb.append("n");
            } else if (achar == 'r') {
                sb.append("y");
            } else if (achar == 's') {
                sb.append("s");
            } else if (achar == 'v') {
                sb.append("b");
            } else if (achar == 'w') {
                sb.append("w");
            } else if (achar == 'y') {
                sb.append("r");
            }
        }
        
        //BDHKMNRSVWY
        //VHDMKNYSBWR
        return sb.toString();
    }

    public static double calcGC(String inseq) {
        String seq = inseq.toUpperCase();
        //Tally up the A's and G's
        int cs = 0;
        int gs = 0;

        for (int i = 0; i < seq.length(); i++) {
            char achar = seq.charAt(i);
            if (achar == 'C') {
                cs++;
            } else if (achar == 'G') {
                gs++;
            }
        }
        
        double length = 1.0*seq.length();
        double out = (gs + cs)/length;
        return out;
    }

}
