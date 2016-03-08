package com.act.biointerpretation.step4_mechanisminspection;

import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by jca20n on 1/7/16.
 */
public class RxnLog {
    private Map<String, String> hashToValue = new HashMap<>();
    File log = new File("data/MechanisticCleaner/RxnLog_data.txt");

    public String get(String hash) {
        return hashToValue.get(hash);
    }

    public RxnLog() {

        try {
            String data = FileUtils.readFile(log.getAbsolutePath());
            data = data.replaceAll("\"", "");
            String[] lines = data.split("\\r|\\r?\\n");
            for (String line : lines) {
                String[] tabs = line.split("\t");
                String hash = tabs[0];
                String value = tabs[1];
                hashToValue.put(hash, value);
            }
        } catch(Exception err) {
            System.out.println("no preexisiting dudrxnlog");
            FileUtils.writeFile("", log.getAbsolutePath());
        }
    }

    /**
     * @param substrates
     * @param products
     * @param tag
     */
    public void log(Set<String> substrates, Set<String> products, String tag) {
        String hash = hash(substrates, products);
        hashToValue.put(hash, tag);
        saveLog(hash,tag);
    }

    public String hash(Set<String> substrates, Set<String> products) {
        String subs = SetToInchi(substrates);
        String prods = SetToInchi(products);
        String hash = subs + ">>" + prods;
        return hash;
    }

    public String SetToInchi(Set<String> inchis) {
        String out = "";
        for(String inchi : inchis) {
            String smiles = ChemAxonUtils.InchiToSmiles(inchi);
            out += smiles;
            out += ".";
        }
        out = out.substring(0, out.length()-1);
        return ChemAxonUtils.SmilesToInchi(out);
    }

    private void saveLog(String hash, String tag) {
        try {
            FileWriter bw = new FileWriter(log.getAbsoluteFile(), true);
            bw.write(hash);
            bw.write("\t");
            bw.write(tag);
            bw.write("\n");
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
