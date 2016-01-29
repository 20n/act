package com.act.biointerpretation.sandbox;

import com.act.biointerpretation.step2_desalting.Desalter;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

/**
 * Created by jca20n on 1/28/16.
 */
public class ConvertMolport {
    public static void main(String[] args) throws IOException {
        ChemAxonUtils.license();

        FileWriter bw = new FileWriter(new File("output/ConvertMolport_inchis.txt"), true);

//        Desalter desalter = new Desalter();

        String data = FileUtils.readFile("data/ConvertMolport/iis_smiles.txt");
        String[] lines = data.split("\\r|\\r?\\n");
        data = null;

        int count = 0;

        System.out.println("Number of entries: " + lines.length);

        for(String line : lines) {
            String[] tabs = line.split("\t");
            String smiles = tabs[0].trim();
            String molportId = tabs[1].trim();

            bw.write(molportId);
            bw.write("\t");

            String inchi = ChemAxonUtils.SmilesToInchi(smiles);

//            try {
//                Set<String> inchiSet = desalter.clean(inchi);
//                for(String str : inchiSet) {
//                    bw.write(str);
//                    bw.write("\t");
//                }
//            } catch(Exception err) {
//            }

            bw.write(inchi);
            bw.write("\n");
            System.out.println(count);
//            desalter.clearLog();
            count++;
        }
        bw.close();
    }
}
