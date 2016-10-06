package com.act.biointerpretation.retentiontime;

import chemaxon.descriptors.CFParameters;
import chemaxon.descriptors.GenerateMD;
import chemaxon.descriptors.MDParameters;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Fingerprint {

  public static void generate(String inputFile, String outputFile) throws Exception {
    GenerateMD generator = new GenerateMD(1);
    MDParameters cfpConfig = new CFParameters( new File("/Volumes/shared-data/Vijay/ret_time_prediction/config/cfp.xml"));
    generator.setInput(inputFile);
    generator.setDescriptor(0, outputFile, "CF", cfpConfig, "");
    generator.setBinaryOutput(true);
    generator.init();
    generator.run();
    generator.close();
  }

  public static void compare() throws Exception {
    BufferedReader reader = new BufferedReader(new FileReader("/Users/vijaytramakrishnan/act/reachables/molecules.cfp"));

    String line = null;
    List<List<String>> binaryValues = new ArrayList<>();
    while ((line = reader.readLine()) != null) {
      binaryValues.add(Arrays.asList(line.split("|")));
    }

    // Compare
    int differenceInBits = 0;
    int total = 0;
    for (int i = 0; i < binaryValues.get(0).size(); i++) {
      String firstBinary = binaryValues.get(0).get(i);
      String secondBinary = binaryValues.get(1).get(i);
      for (int j = 0; j < firstBinary.length(); j++) {
        total++;
        if (firstBinary.charAt(j) != secondBinary.charAt(j)) {
          differenceInBits++;
        }
      }
    }

    System.out.println(differenceInBits);
    System.out.println(total);
  }

  public static void main(String[] args) throws Exception {
    generate("/Volumes/shared-data/Vijay/ret_time_prediction/test_inchis", "/Volumes/shared-data/Vijay/ret_time_prediction/molecules.cfp");
  }
}
