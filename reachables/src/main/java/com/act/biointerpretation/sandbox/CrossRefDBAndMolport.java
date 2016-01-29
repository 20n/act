package com.act.biointerpretation.sandbox;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.act.biointerpretation.utils.FileUtils;

import java.io.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by jca20n on 1/28/16.
 */
public class CrossRefDBAndMolport {
    public static void main(String[] args) throws Exception {
        Map<String, String> molportInchis = initiate();

        NoSQLAPI api = new NoSQLAPI("synapse", "synapse");
        FileWriter bw = new FileWriter(new File("output/CrossRefDBAndMolport_inchis.txt"), true);

        Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();
        while(iterator.hasNext()) {
            Chemical achem = iterator.next();
            String inchi = achem.getInChI();
            if(molportInchis.containsKey(inchi)) {
                bw.write(achem.getFirstName());
                bw.write("\t");
                bw.write(molportInchis.get(inchi));
                bw.write("\t");
                bw.write(inchi);
                bw.write("\n");
                System.out.println(inchi);
            }
        }
        bw.close();
    }

    private static Map<String, String> initiate() throws Exception {
        File file = new File("output/ConvertMolport_inchis.txt");
        Map<String, String> molportInchis = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tabs = line.split("\t");
                molportInchis.put(tabs[1], tabs[0]);
            }
        }
        return molportInchis;

    }
}
