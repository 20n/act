package com.act.biointerpretation.operators;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by jca20n on 11/21/15.
 */
public class ReactionInterpretation {
    public String ro;
    public String mapping;
    public Set<String> subCofactors;
    public Set<String> prodCofactors;
    public int rxnId;

    public static ReactionInterpretation parse(String data) {
        //Break into top part and lower part
        String[] regions = data.split("cofactors:\n");

        //On top half, pull out single fields
        Map<String,String> fields = new HashMap<>();
        String[] lines = regions[0].split("\\r|\\r?\\n");
        for(String aline : lines) {
            String[] splitted = aline.split("\t");
            fields.put(splitted[0], splitted[1]);
        }

        //Create the object and put in fields
        ReactionInterpretation out = new ReactionInterpretation();
        out.ro = fields.get("ro");
        out.mapping = fields.get("mapping");
        out.rxnId = Integer.parseInt(fields.get("rxnId"));

        //Parse the cofactors
        out.subCofactors = new HashSet<>();
        out.prodCofactors = new HashSet<>();
        String[] cofactors = regions[1].split("~");
        lines = cofactors[1].split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            if(lines[i].equals("sub") || lines[i].isEmpty()) {
                continue;
            }
            out.subCofactors.add(lines[i]);
        }
        lines = cofactors[2].split("\\r|\\r?\\n");
        for(int i=1; i<lines.length; i++) {
            if(lines[i].equals("sub") || lines[i].isEmpty()) {
                continue;
            }
            out.prodCofactors.add(lines[i]);
        }
        return out;
    }

    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append("rxnId").append("\t").append(rxnId).append("\n");
        out.append("ro").append("\t").append(ro).append("\n");
        out.append("mapping").append("\t").append(mapping).append("\n");

        out.append("\ncofactors:\n~sub\n\n");
        for(String cofactor : subCofactors) {
            out.append(cofactor).append("\n");
        }
        out.append("\n~prod\n");
        for(String cofactor : prodCofactors) {
            out.append(cofactor).append("\n");
        }
        return out.toString();
    }

    public static void main(String[] args) {
        ReactionInterpretation ri = new ReactionInterpretation();
        ri.ro = "[C:1][O:2]>>[C:1][N:3]";
        ri.mapping = "[C:1](=[O:4])[O:2]>>[C:1](=[O:4])[N:3]";
        ri.rxnId = 5;
        ri.subCofactors = new HashSet<>();
        ri.subCofactors.add("water");
        ri.subCofactors.add("proton");
        ri.prodCofactors = new HashSet<>();
        ri.prodCofactors.add("ammonia");
        ri.prodCofactors.add("proton");

        String data = ri.toString();
        System.out.println(data);
        ReactionInterpretation reparsed = parse(data);
        System.out.println("done");
    }
}
