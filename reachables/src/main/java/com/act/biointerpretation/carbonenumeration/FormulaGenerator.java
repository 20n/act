package com.act.biointerpretation.carbonenumeration;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jca20n on 10/6/16.
 */
public class FormulaGenerator {

    private static final int MIN_C = 4;  //The minimum number of carbons to consider
    private static final int MAX_C = 4;  //The maximumum number of carbons to consider

    private List<Formula> terminalCarbons;

    private class Formula {
        int C;
        int H;
        int N;
        int O;
        int P;
        int S;

        public String toString() {
            String out = "";
            if(C>0) {
                out+= "C" + C;
            }
            if(H>0) {
                out+= "H" + H;
            }
            if(N>0) {
                out+= "N" + N;
            }
            if(O>0) {
                out+= "O" + O;
            }
            if(P>0) {
                out+= "P" + P;
            }
            if(S>0) {
                out+= "S" + S;
            }

            return out;
        }
    }

    public FormulaGenerator() {
        terminalCarbons = new ArrayList<>();

        //-CH3
        Formula aform = new Formula();
        aform.C = 1;
        aform.H = 3;
        terminalCarbons.add(aform);

        //-CH2OH
        aform = new Formula();
        aform.C = 1;
        aform.H = 3;
        aform.O = 1;
        terminalCarbons.add(aform);

        //-CH2NH2
        aform = new Formula();
        aform.C = 1;
        aform.H = 4;
        aform.N = 1;
        terminalCarbons.add(aform);

        //-CH2SH
        aform = new Formula();
        aform.C = 1;
        aform.H = 3;
        aform.S = 1;
        terminalCarbons.add(aform);

        //-C(=O)OH
        aform = new Formula();
        aform.C = 1;
        aform.H = 1;
        aform.O = 2;
        terminalCarbons.add(aform);

        //-C(=O)NH2
        aform = new Formula();
        aform.C = 1;
        aform.H = 2;
        aform.O = 1;
        aform.N = 1;
        terminalCarbons.add(aform);
    }

    public static void main(String[] args) {
        FormulaGenerator fg = new FormulaGenerator();

        for(int C=MIN_C; C <= MAX_C; C++) {
            fg.combinatorializeCore(C);
        }
    }

    private void combinatorializeCore(int Cin) {
        int C = Cin-2; //Just combinatorizalize the methylene positions

        for(int H=0; H<=C; H++) {
            for(int N=0; N<=(C-H); N++) {
                for(int O=0; O<=(C-H-N); O++) {
                    for(int S=0; S<=(C-H-N-O); S++) {
                        redoxidizeCore(C, H, N, O, S);
                    }
                }
            }
        }
    }

    private void redoxidizeCore(int C, int H, int N, int O, int S) {
        //Calculate the most reduced form
        int max_H = 0;
        max_H += 2*H;  //Get a two H's for all methylenes relative to value of H for H-C-H
        max_H += 3*N; //Get two protons on the nitrogen and one on the carbon for H-C-NH2
        max_H += 2*O; //Get one proton on the oxygen and one on the carbon for H-C-OH
        max_H += 2*S; //Get one proton on the sulfur and one on the carbon for H-C-SH

        for(int finalH=max_H; finalH>=0; finalH=finalH-2) {
            Formula formula = new Formula();
            formula.C = C;
            formula.H = finalH;
            formula.N = N;
            formula.O = O;
            formula.S = S;

            System.out.println(formula.toString());
        }
    }


}
