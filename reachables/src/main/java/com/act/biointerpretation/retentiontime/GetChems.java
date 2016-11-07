package com.act.biointerpretation.retentiontime;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.utils.TSVParser;
import com.act.utils.TSVWriter;
import com.mongodb.DBObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GetChems {

  public static void main(String[] args) throws Exception {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");
    Map<String, Chemical> drugBankChems = new HashMap<>();
    Map<String, Chemical> sigmaChems = new HashMap<>();

    List<String> header = new ArrayList<>();
    header.add("Name");
    header.add("Inchi");

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File("/mnt/shared-data/Vijay/ret_time_prediction/marvin_l2_drugbank_sigma.txt"));

    for (Chemical chemical : mongoDB.getDrugbankChemicals()) {
      drugBankChems.put(chemical.getInChI(), chemical);
    }

    for (Chemical chemical : mongoDB.getSigmaChemicals()) {
      sigmaChems.put(chemical.getInChI(), chemical);
    }

    BufferedReader reader = new BufferedReader(new FileReader("/mnt/shared-data/Gil/resources/reachables_list"));

    String line = null;
    while ((line = reader.readLine()) != null) {
      if (drugBankChems.keySet().contains(line)) {
        Map<String, String> row = new HashMap<>();
        row.put("Name", drugBankChems.get(line).getCanon());
        row.put("Inchi", line);
        writer.append(row);
      } else if (sigmaChems.keySet().contains(line)) {
        Map<String, String> row = new HashMap<>();
        row.put("Name", sigmaChems.get(line).getCanon());
        row.put("Inchi", line);
        writer.append(row);
      }
    }

    writer.close();
    reader.close();
  }
}
