package com.act.biointerpretation;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.io.FileWriter;
import java.util.Iterator;

/**
 * Created by chris on 8/27/15.
 */
public class InchiCleaner {
    private static NoSQLAPI api = new NoSQLAPI();

    public static void main(String[] args) throws Exception {
        FileWriter writer = new FileWriter("/home/chris/C/vmwaredata/badinchis.txt");

        Iterator<Chemical> chems = api.getChems();


        while(chems.hasNext()) {
            Chemical achem = chems.next();

            String inchi = achem.getInChI();
            String name = achem.getFirstName();

            Indigo indigo = new Indigo();
            IndigoInchi iinchi = new IndigoInchi(indigo);

            try {
                IndigoObject mol = iinchi.loadMolecule(inchi);
            } catch(Exception err) {
                if(inchi.contains("FAKE")) {
                    continue;
                }
                if(inchi.contains("&gt")) {
                    continue;
                }
                if(inchi.contains("Failed to compute")) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(inchi);
                sb.append("\t");
                sb.append(name);
                sb.append("\n");

                System.out.println(inchi);
                writer.write(sb.toString());
            }
        }

        writer.close();
    }

    public static Chemical clean(Chemical achem) {
        return null;
    }
}
