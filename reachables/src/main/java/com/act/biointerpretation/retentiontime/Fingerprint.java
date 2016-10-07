package com.act.biointerpretation.retentiontime;

import chemaxon.descriptors.CFParameters;
import chemaxon.descriptors.ChemicalFingerprint;
import chemaxon.descriptors.GenerateMD;
import chemaxon.descriptors.MDParameters;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.io.formats.mdl.MolImport;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Fingerprint {

  public static final String OPTION_INPUT_INCHIS = "i";
  public static final String OPTION_OUTPUT_FINGERPRINT = "o";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {
    {
      add(Option.builder(OPTION_INPUT_INCHIS)
          .argName("input inchis")
          .desc("input inchis")
          .hasArg().required()
          .longOpt("input inchis")
      );
      add(Option.builder(OPTION_OUTPUT_FINGERPRINT)
          .argName("output fingerprint")
          .desc("output fingerprint")
          .hasArg().required()
          .longOpt("output fingerprint")
      );
    }
  };

  public static void generate(String inputFile, String outputFile) throws Exception {
    GenerateMD generator = new GenerateMD(1);
    MDParameters cfpConfig = new CFParameters( new File("/mnt/shared-data/Vijay/ret_time_prediction/config/cfp.xml"));
    generator.setInput(inputFile);
    generator.setDescriptor(0, outputFile, "CF", cfpConfig, "");
    generator.setBinaryOutput(true);
    generator.init();
    generator.run();
    generator.close();
  }

  public static void compare(String input) throws Exception {
    BufferedReader reader = new BufferedReader(new FileReader(input));

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
//    Options opts = new Options();
//    for (Option.Builder b : OPTION_BUILDERS) {
//      opts.addOption(b.build());
//    }
//
//    CommandLine cl = null;
//    try {
//      CommandLineParser parser = new DefaultParser();
//      cl = parser.parse(opts, args);
//    } catch (ParseException e) {
//      System.err.format("Argument parsing failed: %s\n", e.getMessage());
//      System.exit(1);
//    }

    CFParameters params = new CFParameters(new File("/mnt/shared-data/Vijay/ret_time_prediction/config/cfp.xml"));
    ChemicalFingerprint apapFingerprint = new ChemicalFingerprint(params);
    Molecule apap = MoleculeImporter.importMolecule("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)");
    apapFingerprint.generate(apap);

    ChemicalFingerprint otherFingerprint = new ChemicalFingerprint(params);
    Molecule otherChem = MoleculeImporter.importMolecule("InChI=1S/C8H11NO/c1-2-9-7-3-5-8(10)6-4-7/h3-6,9-10H,2H2,1H3");
    otherFingerprint.generate(otherChem);

    System.out.println(apapFingerprint.getCommonBitCount(otherFingerprint));


    ChemicalFingerprint apapFingerprint2 = new ChemicalFingerprint(params);
    Molecule apap2 = MolImporter.importMol("CC(=O)Nc1ccc(cc1)O", "smiles");
    apapFingerprint2.generate(apap2);

    ChemicalFingerprint otherFingerprint2 = new ChemicalFingerprint(params);
    Molecule otherChem2 = MolImporter.importMol("CCNc1ccc(cc1)O", "smiles");
    otherFingerprint2.generate(otherChem2);

    System.out.println(apapFingerprint2.getCommonBitCount(otherFingerprint2));





//    generate(cl.getOptionValue(OPTION_INPUT_INCHIS), cl.getOptionValue(OPTION_OUTPUT_FINGERPRINT));
//    compare(cl.getOptionValue(OPTION_OUTPUT_FINGERPRINT));
  }
}
