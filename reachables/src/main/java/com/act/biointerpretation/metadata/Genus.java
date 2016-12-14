package com.act.biointerpretation.metadata;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jca20n on 12/13/16.
 */
public class Genus {

    String Domain;
    String Kingdom;
    String Phylum;
    String CClass;
    String Order;
    String Family;
    String Cgenus;

    public static Map<String, Genus> parseGenuses() throws Exception {

        //Import genus classifications for 'cloned'
        Map<String, Genus> nameToGenus = new HashMap<>();
        File termfile = new File("/Users/jca20n/Dropbox (20n)/20n Team Folder/act_data/ProteinMetadata/2016_12_07-cloned_genus_wikipedia_classification.txt");
        String data = FileUtils.readFileToString(termfile);
        String[] regions = data.split(">");

        //Parse each organism's Genus info
        for(String region : regions) {
            if(region.trim().isEmpty()) {
                continue;
            }
            String[] lines = region.split("\\r|\\r?\\n");

            //Parse first line
            String firstline = lines[0];
            String organism = firstline.substring(1);

            //Initialize data to parse
            String Domain = null;
            String Kingdom = null;
            String Phylum = null;
            String CClass = null;
            String Order = null;
            String Family = null;
            String Cgenus = null;

            //Parse remaining line
            for(int i=1; i<lines.length; i++) {
                String line = lines[i];
                if(line.isEmpty()) {
                    continue;
                }
                String[] tabs = line.split("[:\t]");
                if(tabs[0].equals("Domain")) {
                    Domain = tabs[2];
                } else if(tabs[0].equals("Kingdom")) {
                    Kingdom = tabs[2];
                } else if(tabs[0].equals("Phylum")) {
                    Phylum = tabs[2];
                } else if(tabs[0].equals("Class")) {
                    CClass = tabs[2];
                } else if(tabs[0].equals("Order")) {
                    Order = tabs[2];
                } else if(tabs[0].equals("Family")) {
                    Family = tabs[2];
                } else if(tabs[0].equals("Genus")) {
                    Cgenus = tabs[2];
                }
            }

            if(Domain==null) {
                if(Kingdom.equals("Animalia")) {
                    Domain = "Eukaryota";
                } else if(Kingdom.equals("Bacteria")) {
                    Domain = "Bacteria";
                } else if(Kingdom.equals("Fungi")) {
                    Domain = "Eukaryota";
                } else if(Kingdom.equals("Plantae")) {
                    Domain = "Eukaryota";
                }
            }

            //Construct and store the Genus
            Genus genus = new Genus( Domain , Kingdom, Phylum, CClass, Order, Family, Cgenus);
            nameToGenus.put(organism, genus);
        }

        return nameToGenus;
    }

    public Genus(String domain, String kingdom, String phylum, String CClass, String order, String family, String cgenus) {
        Domain = domain;
        Kingdom = kingdom;
        Phylum = phylum;
        this.CClass = CClass;
        Order = order;
        Family = family;
        Cgenus = cgenus;
    }

    public static int similarity(Genus g1, Genus g2) {
        int out = 0;

        if(compare(g1.Domain, g2.Domain)) {
            out+=1;
            if(compare(g1.Kingdom, g2.Kingdom)) {
                out+=1;
                if(compare(g1.Phylum, g2.Phylum)) {
                    out+=1;
                    if(compare(g1.CClass, g2.CClass)) {
                        out+=1;
                        if(compare(g1.Order, g2.Order)) {
                            out+=1;
                            if(compare(g1.Family, g2.Family)) {
                                out+=1;
                                if(compare(g1.Cgenus, g2.Cgenus)) {
                                    out+=1;
                                }
                            }
                        }
                    }
                }
            }
        }

        return out;
    }

    private static boolean compare(String d1, String d2) {
        if(d1==null || d2==null) {
            return false;
        }
        if(d1.equals(d2)) {
            return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        Map<String, Genus> nameToGenus = Genus.parseGenuses();
        for(String name : nameToGenus.keySet()) {
            Genus genus = nameToGenus.get(name);
            if(genus==null) {
                System.err.println("Null genus");
                continue;
            }

            if(genus.Kingdom == null) {
                System.err.println("Null Kingdom " + name);
            }

            if(genus.Domain == null) {
                System.err.println("Null Domain " + name);
            }

            if(genus.Phylum == null) {
                System.err.println("Null Phylum " + name);
            }
        }
        System.out.println("done");
    }
}
