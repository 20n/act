package com.act.lcms.db.analysis;

import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BestMoleculesPickerFromLCMSIonAnalysis {

  public static final String OPTION_INPUT_FILES = "i";
  public static final String OPTION_OUTPUT_FILE = "o";
  public static final String OPTION_MIN_INTENSITY_THRESHOLD = "n";
  public static final String OPTION_MIN_TIME_THRESHOLD = "t";
  public static final String OPTION_MIN_SNR_THRESHOLD = "s";

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_INPUT_FILES)
        .argName("input file")
        .desc("The input files containing molecular hit results in the IonAnalysisInterchangeModel serialized object " +
            "format for every positive replicate well from the same lcms mining run.")
        .hasArgs()
        .valueSeparator(',')
        .required()
        .longOpt("input-file")
    );
    add(Option.builder(OPTION_OUTPUT_FILE)
        .argName("output file")
        .desc("The output file to write validated inchis to")
        .hasArg().required()
        .longOpt("output-file")
    );
    add(Option.builder(OPTION_MIN_INTENSITY_THRESHOLD)
        .argName("min intensity threshold")
        .desc("The min intensity threshold")
        .hasArg()
        .longOpt("min-intensity-threshold")
    );
    add(Option.builder(OPTION_MIN_TIME_THRESHOLD)
        .argName("min time threshold")
        .desc("The min time threshold")
        .hasArg()
        .longOpt("min-time-threshold")
    );
    add(Option.builder(OPTION_MIN_SNR_THRESHOLD)
        .argName("min snr threshold")
        .desc("The min snr threshold")
        .hasArg()
        .longOpt("min-snr-threshold")
    );
  }};

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This module takes as inputs LCMS analysis results in the form of IonAnalysisInterchangeModel serialized object files " +
      "for every positive replicate vs negative controls. Based on these, it identifies inchis that are hits on all the " +
      "replicates and writes them to an output file."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      HELP_FORMATTER.printHelp(BestMoleculesPickerFromLCMSIonAnalysis.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(BestMoleculesPickerFromLCMSIonAnalysis.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    Map<String, Double> inchiToTime = new HashMap<String, Double>() {{
      put("InChI=1S/C8H8O3/c1-11-8-4-6(5-9)2-3-7(8)10/h2-5,10H,1H3",79.3659997);
      put("InChI=1S/C8H10N4O2/c1-10-4-9-6-5(10)7(13)12(3)8(14)11(6)2/h4H,1-3H3",57.24999905);
      put("InChI=1S/C7H8N4O2/c1-10-5-4(8-3-9-5)6(12)11(2)7(10)13/h3H,1-2H3,(H,8,9)",33.677001);
      put("InChI=1S/C32H39NO4/c1-31(2,30(35)36)25-17-15-24(16-18-25)29(34)14-9-21-33-22-19-28(20-23-33)32(37,26-10-5-3-6-11-26)27-12-7-4-8-13-27/h3-8,10-13,15-18,28-29,34,37H,9,14,19-23H2,1-2H3,(H,35,36)",177.8599977);
      put("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)",37.96299934);
      put("InChI=1S/C29H33ClN2O2/c1-31(2)27(33)29(24-9-5-3-6-10-24,25-11-7-4-8-12-25)19-22-32-20-17-28(34,18-21-32)23-13-15-26(30)16-14-23/h3-16,34H,17-22H2,1-2H3",203.1890059);
      put("InChI=1S/C9H8O4/c1-6(10)13-8-5-3-2-4-7(8)9(11)12/h2-5H,1H3,(H,11,12)",102.5530028);
      put("InChI=1S/C8H15N7O2S3/c9-6(15-20(12,16)17)1-2-18-3-5-4-19-8(13-5)14-7(10)11/h4H,1-3H2,(H2,9,15)(H2,12,16,17)(H4,10,11,13,14)",34.74900126);
      put("InChI=1S/C21H25ClN2O3/c22-19-8-6-18(7-9-19)21(17-4-2-1-3-5-17)24-12-10-23(11-13-24)14-15-27-16-20(25)26/h1-9,21H,10-16H2,(H,25,26)",178.930006);
      put("InChI=1S/C13H18ClNO/c1-9(15-13(2,3)4)12(16)10-6-5-7-11(14)8-10/h5-9,15H,1-4H3",114.3390012);
    }};

    Map<String, String> nameToInchi = new HashMap<String, String>() {{
      put("tenofovir disoproxil","InChI=1S/C19H30N5O10P/c1-12(2)33-18(25)28-9-31-35(27,32-10-29-19(26)34-13(3)4)11-30-14(5)6-24-8-23-15-16(20)21-7-22-17(15)24/h7-8,12-14H,6,9-11H2,1-5H3,(H2,20,21,22)/t14-/m1/s1");
      put("tenofavir","InChI=1S/C9H14N5O4P/c1-6(18-5-19(15,16)17)2-14-4-13-7-8(10)11-3-12-9(7)14/h3-4,6H,2,5H2,1H3,(H2,10,11,12)(H2,15,16,17)/t6-/m1/s1");
      put("emtricitabine","InChI=1S/C8H10FN3O3S/c9-4-1-12(8(14)11-7(4)10)5-3-16-6(2-13)15-5/h1,5-6,13H,2-3H2,(H2,10,11,14)/t5-,6+/m0/s1");
      put("emtricitabine sulfoxide","InChI=1S/C8H10FN3O4S/c9-4-1-12(8(14)11-7(4)10)5-3-17(15)6(2-13)16-5/h1,5-6,13H,2-3H2,(H2,10,11,14)/t5-,6+,17?/m0/s1");
      put("emtricitabine-2'-o-glucuronide","InChI=1S/C14H18FN3O9S/c15-4-1-18(14(24)17-11(4)16)5-3-28-6(26-5)2-25-13-9(21)7(19)8(20)10(27-13)12(22)23/h1,5-10,13,19-21H,2-3H2,(H,22,23)(H2,16,17,24)/t5-,6+,7-,8-,9+,10-13+/m0/s1");
      put("vanillin","InChI=1S/C8H8O3/c1-11-8-4-6(5-9)2-3-7(8)10/h2-5,10H,1H3");
      put("acetaminophen","InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)");
      put("aspirin","InChI=1S/C9H8O4/c1-6(10)13-8-5-3-2-4-7(8)9(11)12/h2-5H,1H3,(H,11,12)");
      put("ibuprofen","InChI=1S/C13H18O2/c1-9(2)8-11-4-6-12(7-5-11)10(3)13(14)15/h4-7,9-10H,8H2,1-3H3,(H,14,15)");
      put("bupropion","InChI=1S/C13H18ClNO/c1-9(15-13(2,3)4)12(16)10-6-5-7-11(14)8-10/h5-9,15H,1-4H3");
      put("theophylline","InChI=1S/C7H8N4O2/c1-10-5-4(8-3-9-5)6(12)11(2)7(10)13/h3H,1-2H3,(H,8,9)");
      put("sucralose","InChI=1S/C12H19Cl3O8/c13-1-4-7(17)10(20)12(3-14,22-4)23-11-9(19)8(18)6(15)5(2-16)21-11/h4-11,16-20H,1-3H2/t4-,5-,6+,7-,8+,9-,10+,11-,12+/m1/s1");
      put("famotidine","InChI=1S/C8H15N7O2S3/c9-6(15-20(12,16)17)1-2-18-3-5-4-19-8(13-5)14-7(10)11/h4H,1-3H2,(H2,9,15)(H2,12,16,17)(H4,10,11,13,14)");
      put("cetirizine","InChI=1S/C21H25ClN2O3/c22-19-8-6-18(7-9-19)21(17-4-2-1-3-5-17)24-12-10-23(11-13-24)14-15-27-16-20(25)26/h1-9,21H,10-16H2,(H,25,26)");
      put("loperamide","InChI=1S/C29H33ClN2O2/c1-31(2)27(33)29(24-9-5-3-6-10-24,25-11-7-4-8-12-25)19-22-32-20-17-28(34,18-21-32)23-13-15-26(30)16-14-23/h3-16,34H,17-22H2,1-2H3");
      put("fexofenadine","InChI=1S/C32H39NO4/c1-31(2,30(35)36)25-17-15-24(16-18-25)29(34)14-9-21-33-22-19-28(20-23-33)32(37,26-10-5-3-6-11-26)27-12-7-4-8-13-27/h3-8,10-13,15-18,28-29,34,37H,9,14,19-23H2,1-2H3,(H,35,36)");
      put("caffeine","InChI=1S/C8H10N4O2/c1-10-4-9-6-5(10)7(13)12(3)8(14)11(6)2/h4H,1-3H3");
      put("capsaicin","InChI=1S/C18H27NO3/c1-14(2)8-6-4-5-7-9-18(21)19-13-15-10-11-16(20)17(12-15)22-3/h6,8,10-12,14,20H,4-5,7,9,13H2,1-3H3,(H,19,21)/b8-6+");
      put("theobromine","InChI=1S/C7H8N4O2/c1-10-3-8-5-4(10)6(12)9-7(13)11(5)2/h3H,1-2H3,(H,9,12,13)");
    }};

    Map<String, String> inchiToName = new HashMap<>();

    for (Map.Entry<String, String> entry : nameToInchi.entrySet()) {
      inchiToName.put(entry.getValue(), entry.getKey());
    }

    Double minSnrThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_SNR_THRESHOLD));
    Double minIntensityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_INTENSITY_THRESHOLD));
    Double minTimeThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_TIME_THRESHOLD));

    List<String> positiveReplicateResults = new ArrayList<>(Arrays.asList(cl.getOptionValues(OPTION_INPUT_FILES)));

    Set<Pair<String, Double>> inchis = IonAnalysisInterchangeModel.getAllMoleculeHitsFromMultiplePositiveReplicateFiles(
        positiveReplicateResults, minSnrThreshold, minIntensityThreshold, minTimeThreshold);

    Set<String> finalSet = new HashSet<>();
    Set<String> otherSet = new HashSet<>();
    for (Pair<String, Double> inchiAndTime : inchis) {
      String inchi = inchiAndTime.getLeft();
      Double time = inchiAndTime.getRight();
      if (inchiToTime.containsKey(inchi)) {
        if (time > inchiToTime.get(inchi) - 1 && time < inchiToTime.get(inchi) + 1) {
          finalSet.add(inchi);
        }
      } else {
        otherSet.add(inchi);
      }
    }

    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(cl.getOptionValue(OPTION_OUTPUT_FILE)))) {
      for (String inchi : finalSet) {
        predictionWriter.append(inchiToName.get(inchi));
        predictionWriter.newLine();
      }

      predictionWriter.newLine();
      predictionWriter.append("Other:");
      predictionWriter.newLine();

      for (String inchi : otherSet) {
        System.out.println(inchi);
        if (inchiToName.containsKey(inchi)) {
          predictionWriter.append(inchiToName.get(inchi));
        } else {
          predictionWriter.append(inchi);
        }
        predictionWriter.newLine();
      }
    }
  }
}
