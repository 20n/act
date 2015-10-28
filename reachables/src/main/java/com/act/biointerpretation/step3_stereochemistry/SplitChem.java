package com.act.biointerpretation.step3_stereochemistry;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.util.ArrayList;
import java.util.List;

/**
 * see standardization: (unrelated)
 * http://lifescience.opensource.epam.com/indigo/examples/standardize.html
 * http://lifescience.opensource.epam.com/indigo/options/standardize.html#
 *http://www.inchi-trust.org/technical-faq/#8
 *
 * Created by jca20n on 10/26/15.
 */
public class SplitChem {
    String inchiBase = "";
    Boolean[] stereos;

    public SplitChem(SplitChem original) {
        this.inchiBase = original.inchiBase;
        this.stereos = original.stereos;
    }
    public SplitChem(String concreteInchi) {
        //Pull out the mIs1 and z terms
        String[] regions = concreteInchi.split("/");
        String m = null;
        String t = null;
        for(String region : regions) {
            if(region.startsWith("m")) {
                m = region;
            }
            if(region.startsWith("t")) {
                t= region;
            }
        }

        //Scan through and extract out + and -'s
        List<Boolean> stereos = new ArrayList<>();
        for(int i=0; i<t.length(); i++) {
            char achar = t.charAt(i);
            if(achar == '+') {
                stereos.add(true);
            } else if(achar == '-') {
                stereos.add(false);
            } else if(achar == '?') {
                stereos.add(null);
            }
        }
        this.stereos = new Boolean[stereos.size()];
        for(int i=0; i<stereos.size(); i++) {
            this.stereos[i] = stereos.get(i);
        }

        //if mIs1=1 then invert everything
        if(m!=null && m.charAt(1) == '1') {
            for(int i=0; i< this.stereos.length; i++) {
                this.stereos[i] = !this.stereos[i];
            }
        }

        //Abstract the t and m info
        String newT = t.replaceAll("-", "?");
        newT = newT.replaceAll("\\+", "?");
        for(String region : regions) {
            if(region.startsWith("InChI")) {
                this.inchiBase += region;
            } else if(region.startsWith("m")) {
                continue;
            } else if(region.startsWith("s")) {
                continue;
            } else if (region.startsWith("t")) {
                this.inchiBase += "/";
                this.inchiBase += newT;
            } else {
                this.inchiBase += "/";
                this.inchiBase += region;
            }
        }

        System.out.println();
    }

    public String getInchi() {
        //Pull out the tterm
        String[] regions = this.inchiBase.split("/");
        String t = null;
        for(String region : regions) {
            if(region.startsWith("t")) {
                t= region;
            }
        }

        //Replace all the stereochemistry with the correct one
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for(int i=0; i<t.length(); i++) {
            if(t.charAt(i) != '?') {
                sb.append(t.charAt(i));
                continue;
            }

            if(stereos[index]) {
                sb.append("+");
            } else {
                sb.append("-");
            }

            index++;
        }

        String newT = sb.toString();

        //Put the new t back into the inchi
        String out = "";
        for(String region : regions) {
            if(region.startsWith("InChI")) {
                out += region;
            } else if (region.startsWith("t")) {
                out += "/";
                out += newT;
            } else {
                out += "/";
                out += region;
            }
        }
        out+= "/m0/s1";

        //Use indigo to reset m
        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        IndigoObject mol = iinchi.loadMolecule(out);
        return iinchi.getInchi(mol);
    }

    public String toString() {
        String out = "";
        out+= "inchiBase: " + this.inchiBase + "\n";
        out+= "stereos:" + "\n";
        for (int i = 0; i < this.stereos.length; i++) {
            out += i + " : " + this.stereos[i]+ "\n";
        }
        return out;
    }

    public static void main(String[] args) {
        String[] tests = new String[3];
        tests[0] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1-,2-/m1/s1"; //l tartrate
        tests[1] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1-,2-/m0/s1"; //d tartrate
        tests[2] = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1-,2+";  //meso tartrate

        for(String inchi : tests) {
            SplitChem achem = new SplitChem(inchi);

            System.out.println("\nStarted with:");
            System.out.println(inchi);

            System.out.println("\nRepresented as:");
            System.out.println(achem.toString());


            System.out.println("\nReconstructed:");
            String reconstructed = achem.getInchi();
            System.out.println(reconstructed);

            System.out.println("worked? - " + inchi.equals(reconstructed));
        }
    }
}
