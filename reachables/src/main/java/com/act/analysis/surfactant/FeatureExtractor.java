package com.act.analysis.surfactant;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.utils.TSVParser;
import com.act.utils.TSVWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureExtractor {


  public static Molecule cleanMol(Molecule molecule) {
    // We had to clean the molecule after importing since based on our testing, the RO only matched the molecule
    // once we cleaned it. Else, the RO did not match the chemical.
    Cleaner.clean(molecule, 2);

    // We had to aromatize the molecule so that aliphatic related ROs do not match with aromatic compounds.
    molecule.aromatize(MoleculeGraph.AROM_BASIC);
    return molecule;
  }

  public static void main(String[] args) throws Exception {

    TSVParser parser = new TSVParser();
    parser.parse(new File("/mnt/shared-data/Vijay/ret_time_prediction/combined_set.txt"));

    List<String> header = new ArrayList<>();

    for (SurfactantAnalysis.FEATURES features : SurfactantAnalysis.FEATURES.values()) {
      header.add(features.name());
    }

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File("/mnt/shared-data/Vijay/ret_time_prediction/features.tsv"));

    for (Map<String, String> row : parser.getResults()) {
      Molecule molecule = cleanMol(MolImporter.importMol(row.get("Molecule"), "smiles"));
      String inchi = MolExporter.exportToFormat(molecule, "inchi:AuxNone,Woff");

      Map<SurfactantAnalysis.FEATURES, Double> results = SurfactantAnalysis.performAnalysis(inchi, false);

      Map<String, String> writeRow = new HashMap<>();

      for (Map.Entry<SurfactantAnalysis.FEATURES, Double> entry : results.entrySet()) {
        writeRow.put(entry.getKey().name(), entry.getValue().toString());
      }

      writer.append(writeRow);
      writer.flush();
    }

    writer.close();
  }
}
