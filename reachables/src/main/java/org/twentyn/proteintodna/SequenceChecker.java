/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package org.twentyn.proteintodna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class checks a sequence for passing Gen9 and Beersheba constraints
 */
public class SequenceChecker {
    
    private boolean verbose = false;
    
    public void setVerbose(boolean isVerbose) {
        verbose = isVerbose;
    }
    
    private void printIfVerbose(String msg) {
        if(verbose) {
            System.out.println(msg);
        }
    }
    
    public boolean check(String dnaseq) {
        String revcomp = SequenceUtils.reverseComplement(dnaseq);
        String combined = dnaseq + "x" + revcomp;
        combined = combined.toUpperCase();
        if(combined.contains("AAAAAAAA")) {
            printIfVerbose("Sequence has poly(A)");
            return false;
        }
        if(combined.contains("TTTTTTTT")) {
            printIfVerbose("Sequence has poly(T)");
            return false;
        }
        if(combined.contains("CCCCCCCC")) {
            printIfVerbose("Sequence has poly(C)");
            return false;
        }
        if(combined.contains("GGGGGGGG")) {
            printIfVerbose("Sequence has poly(G)");
            return false;
        }
        if(combined.contains("CAATTG")) {
            printIfVerbose("Sequence has MfeI");
            return false;
        }
        if(combined.contains("GAATTC")) {
            printIfVerbose("Sequence has EcoRI");
            return false;
        }
        if(combined.contains("GGATCC")) {
            printIfVerbose("Sequence has BamHI");
            return false;
        }
        if(combined.contains("AGATCT")) {
            printIfVerbose("Sequence has BglII");
            return false;
        }
        if(combined.contains("ACTAGT")) {
            printIfVerbose("Sequence has SpeI");
            return false;
        }
        if(combined.contains("TCTAGA")) {
            printIfVerbose("Sequence has XbaI");
            return false;
        }
        if(combined.contains("GGTCTC")) {
            printIfVerbose("Sequence has BsaI");
            return false;
        }
        if(combined.contains("GAGGAG")) {
            printIfVerbose("Sequence has BseRI");
            return false;
        }
        if(combined.contains("CGTCTC")) {
            printIfVerbose("Sequence has BsmBI");
            return false;
        }
        if(combined.contains("CACCTGC")) {
            printIfVerbose("Sequence has AarI");
            return false;
        }
        if(combined.contains("CTGCAG")) {
            printIfVerbose("Sequence has PstI");
            return false;
        }
        if(combined.contains("CTCGAG")) {
            printIfVerbose("Sequence has XhoI");
            return false;
        }
        if(combined.contains("GCATGC")) {
            printIfVerbose("Sequence has SphI");
            return false;
        }
        if(combined.contains("GTCGAC")) {
            printIfVerbose("Sequence has SalI");
            return false;
        }
        if(combined.contains("GCGGCCGC")) {
            printIfVerbose("Sequence has NotI");
            return false;
        }
        if(combined.contains("AAGCTT")) {
            printIfVerbose("Sequence has HindIII");
            return false;
        }
        
        return true;
    }
    
    public static void main(String[] args) {
        SequenceChecker checker = new SequenceChecker();
        boolean result = checker.check("GGGGGGGG");  //returns false due to poly(G)
        System.out.println(result);
    }
}
