package com.act.biointerpretation.retentiontime;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.descriptors.CFParameters;
import chemaxon.descriptors.ChemicalFingerprint;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.utils.TSVWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FingerprintGenerator {

  public static Molecule cleanMol(Molecule molecule) {
    // We had to clean the molecule after importing since based on our testing, the RO only matched the molecule
    // once we cleaned it. Else, the RO did not match the chemical.
    Cleaner.clean(molecule, 2);

    // We had to aromatize the molecule so that aliphatic related ROs do not match with aromatic compounds.
    molecule.aromatize(MoleculeGraph.AROM_BASIC);
    return molecule;
  }

  public static void main(String[] args) throws Exception {

    CFParameters params = new CFParameters(new File("/mnt/shared-data/Vijay/ret_time_prediction/config/cfp.xml"));
    BufferedReader reader = new BufferedReader(new FileReader("/mnt/shared-data/Gil/resources/reachables_list"));

    List<String> headers = new ArrayList<>();
    headers.add("SMILE");
    headers.add("Fingerprint");

    TSVWriter<String, String> writer = new TSVWriter<>(headers);
    writer.open(new File("/mnt/shared-data/Vijay/ret_time_prediction/l2_fingerprints"));

    String inchi = null;
    while ((inchi = reader.readLine()) != null) {

      try {
        Molecule moleculeInchi = cleanMol(MolImporter.importMol(inchi, "inchi"));
        String smilesChemical = (String)MolExporter.exportToObject(moleculeInchi, "smiles:a");
        Molecule moleculeSmiles = cleanMol(MolImporter.importMol(smilesChemical, "smiles"));

        ChemicalFingerprint fingerprint = new ChemicalFingerprint(params);
        fingerprint.generate(moleculeSmiles);

        Map<String, String> row = new HashMap<>();
        row.put("SMILE", smilesChemical);
        row.put("Fingerprint", fingerprint.toBinaryString());
        writer.append(row);
        writer.flush();
      } catch (Exception e) {
        System.out.println(e.getLocalizedMessage());
      }
    }

    writer.close();
  }
}
