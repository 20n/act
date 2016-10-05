package com.act.biointerpretation.liver;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.moieties.MoietyExtractor;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

import static act.shared.Chemical.REFS.BRENDA;

/**
 * Created by jca20n on 12/9/15.
 */
public class LiverCrawl {
    private NoSQLAPI api;


    public static void main(String[] args) throws Exception {
        String data = FileUtils.readFile("/Users/jca20n/dbout.json");
        JSONObject json = new JSONObject(data);
        data = null;

        for(Object key : json.keySet()) {
            System.out.println(key);

        }
    }


}
