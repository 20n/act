package com.act.biointerpretation.retentiontime;

import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.MajorMicrospeciesPlugin;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.struc.Molecule;
import com.act.utils.TSVWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetChems {

  public static void main(String[] args) throws Exception {
    MongoDB mongoDB = new MongoDB("localhost", 27017, "marvin");
    Map<String, Chemical> drugBankChems = new HashMap<>();
    Map<String, Chemical> sigmaChems = new HashMap<>();

    List<String> header = new ArrayList<>();
    header.add("Name");
    header.add("Inchi");
    header.add("LogP");
    header.add("Mass");

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File("/mnt/shared-data/Vijay/ret_time_prediction/drugbank_sigma.txt"));

    logPPlugin plugin = new logPPlugin();
    MajorMicrospeciesPlugin microspeciesPlugin = new MajorMicrospeciesPlugin();

    for (Chemical chemical : mongoDB.getDrugbankChemicals()) {
      drugBankChems.put(chemical.getInChI(), chemical);
    }

    for (Chemical chemical : mongoDB.getSigmaChemicals()) {
      sigmaChems.put(chemical.getInChI(), chemical);
    }

    for (String line : drugBankChems.keySet()) {
      if (sigmaChems.keySet().contains(line)) {
        Map<String, String> row = new HashMap<>();
        row.put("Name", sigmaChems.get(line).getFirstName());
        row.put("Inchi", line);

        Molecule molecule = MolImporter.importMol(line, "inchi");

        Cleaner.clean(molecule, 3);
        plugin.standardize(molecule);
        microspeciesPlugin.setpH(2.7);
        microspeciesPlugin.setMolecule(molecule);
        microspeciesPlugin.run();

        Molecule phMol = microspeciesPlugin.getMajorMicrospecies();
        plugin.setlogPMethod(LogPMethod.CONSENSUS);
        plugin.setUserTypes("logPTrue,logPMicro,logPNonionic");
        plugin.setMolecule(phMol);
        plugin.run();

        Double mass = molecule.getMass();
        Double logP = plugin.getlogPTrue();
        row.put("Mass", mass.toString());
        row.put("LogP", logP.toString());

        writer.append(row);
      }
    }
//
//    BufferedReader reader = new BufferedReader(new FileReader("/mnt/shared-data/Gil/resources/reachables_list"));
//
//
//
//
//    String line = null;
//    while ((line = reader.readLine()) != null) {
//      if (sigmaChems.keySet().contains(line)) {
//        Map<String, String> row = new HashMap<>();
//        row.put("Name", sigmaChems.get(line).getFirstName());
//        row.put("Inchi", line);
//
//        Molecule molecule = MolImporter.importMol(line, "inchi");
//
//        Cleaner.clean(molecule, 3);
//        plugin.standardize(molecule);
//        microspeciesPlugin.setpH(2.7);
//        microspeciesPlugin.setMolecule(molecule);
//        microspeciesPlugin.run();
//
//        Molecule phMol = microspeciesPlugin.getMajorMicrospecies();
//        plugin.setlogPMethod(LogPMethod.CONSENSUS);
//        plugin.setUserTypes("logPTrue,logPMicro,logPNonionic");
//        plugin.setMolecule(phMol);
//        plugin.run();
//
//        Double mass = molecule.getMass();
//        Double logP = plugin.getlogPTrue();
//        row.put("Mass", mass.toString());
//        row.put("LogP", logP.toString());
//
//        writer.append(row);
//      }
//    }

    writer.close();
    //reader.close();
  }
}
