package org.twentyn.proteintodna;

public class RBSOption {

    String name;
    String rbs;
    String cds;
    String first6aas;

    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append(name);
        out.append("\n").append(rbs);
        out.append("\n").append(cds);
        out.append("\n").append(first6aas);
        return out.toString();
    }
}
