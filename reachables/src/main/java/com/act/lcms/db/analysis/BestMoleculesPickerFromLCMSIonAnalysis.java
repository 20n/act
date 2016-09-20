package com.act.lcms.db.analysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import com.act.utils.TSVParser;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.io.IOException;
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
  public static final String OPTION_GET_IONS_SUPERSET = "f";
  public static final String OPTION_JSON_FORMAT = "j";
  public static final String OPTION_MIN_OF_REPLICATES = "m";
  public static final String OPTION_THRESHOLD_ANALYSIS = "p";
  public static final String OPTION_FILTER_BY_IONS = "k";
  public static final String HEADER_INCHI = "Inchi";
  public static final String HEADER_MASS = "Monoisotopic Mass";
  public static final String HEADER_MZ = "MZ";
  public static final String HEADER_ION = "Ion";
  public static final String OPTION_STATISTICAL_ANALYSIS = "x";

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
    add(Option.builder(OPTION_GET_IONS_SUPERSET)
        .argName("ions superset")
        .desc("A run option on all the ionic variant files on a single replicate run")
        .longOpt("ions-superset")
    );
    add(Option.builder(OPTION_JSON_FORMAT)
        .argName("json format")
        .desc("Output the result in the IonAnalysisInterchangeModel json format. If not, output a list of inchis, one per line.")
        .longOpt("json-format")
    );
    add(Option.builder(OPTION_MIN_OF_REPLICATES)
        .argName("min of replicates")
        .desc("Get the min intensities, SNR and times of multiple replicates")
        .longOpt("ions-superset")
    );
    add(Option.builder(OPTION_THRESHOLD_ANALYSIS)
        .argName("threshold analysis")
        .desc("Do threshold analysis")
        .longOpt("threshold-analysis")
    );
    add(Option.builder(OPTION_FILTER_BY_IONS)
        .argName("filter by ions")
        .desc("Filter files by ion")
        .hasArgs()
        .valueSeparator(',')
        .longOpt("filter-by-ions")
    );
    add(Option.builder(OPTION_STATISTICAL_ANALYSIS)
        .argName("statistical analysis")
        .desc("statistical analysis")
        .longOpt("statistical-analysis")
    );
  }};

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This module takes as inputs LCMS analysis results in the form of IonAnalysisInterchangeModel serialized object files ",
          "for every positive replicate vs negative controls. Based on these, it identifies inchis that are hits on all the ",
          "replicates and writes them to an output file."
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<String> OUTPUT_TSV_HEADER_FIELDS = new ArrayList<String>() {{
    add(HEADER_INCHI);
    add(HEADER_ION);
    add(HEADER_MASS);
    add(HEADER_MZ);
    add("Time");
    add("Average Intensity");
    add("Min Intensity");
    add("Max Intensity");
    add("STD Intensity");
  }};

  /**
   * This function is used to print the model either as a json or as a TSV file containing relevant statistics.
   * @param fileName The name of file
   * @param jsonFormat Whether it needs to be outputted in a json format or a a list of inchis, one per line.
   * @param model The model that is being written
   * @throws IOException
   */
  public static void printInchisAndIonsToFile(IonAnalysisInterchangeModel model, String fileName, Boolean jsonFormat) throws IOException {
    if (jsonFormat) {
      model.writeToJsonFile(new File(fileName));
    } else {
      List<String> header = new ArrayList<>();
      header.addAll(OUTPUT_TSV_HEADER_FIELDS);

      TSVWriter<String, String> writer = new TSVWriter<>(header);
      writer.open(new File(fileName));

      for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : model.getResults()) {
        for (IonAnalysisInterchangeModel.HitOrMiss molecule : resultForMZ.getMolecules()) {
          Map<String, String> row = new HashMap<>();
          row.put(HEADER_INCHI, molecule.getInchi());
          row.put(HEADER_ION, molecule.getIon());
          row.put(HEADER_MASS, MassCalculator.calculateMass(molecule.getInchi()).toString());
          row.put(HEADER_MZ, resultForMZ.getMz().toString());
          row.put("Time", molecule.getTime().toString());
          row.put("Average Intensity", molecule.getAverageIntensity().toString());
          row.put("Min Intensity", molecule.getMinIntensity().toString());
          row.put("Max Intensity", molecule.getMaxIntensity().toString());
          row.put("STD Intensity", molecule.getStdIntensity().toString());

          writer.append(row);
          writer.flush();
        }
      }

      writer.close();
    }
  }

  public static void main(String[] args) throws Exception {

    IonAnalysisInterchangeModel alignedPeaks = new IonAnalysisInterchangeModel();
    alignedPeaks.loadResultsFromFile(new File("/mnt/shared-data/Vijay/perlstein_azure_run_water/water_combined"));

//    // Analysis for GM03123
//    for (int j = 0; j < 3; j++) {
//      int startingIndex = j * 3;
//
//      Map<IonAnalysisInterchangeModel.ResultForMZ, Triple<Double, Double, Double>> GM03123infoTorTriples = new HashMap<>();
//
//      IonAnalysisInterchangeModel GM03123PeaksRepA = new IonAnalysisInterchangeModel();
//
//      GM03123PeaksRepA.loadResultsFromFile(new File(String.format("/mnt/shared-data/Vijay/perlstein_azure_run/amino_acid_results_water/perlstein_water.results_%d.json", startingIndex)));
//
//      IonAnalysisInterchangeModel GM03123PeaksRepB = new IonAnalysisInterchangeModel();
//      GM03123PeaksRepB.loadResultsFromFile(new File(String.format("/mnt/shared-data/Vijay/perlstein_azure_run/amino_acid_results_water/perlstein_water.results_%d.json", startingIndex + 1)));
//
//      IonAnalysisInterchangeModel GM03123PeaksRepC = new IonAnalysisInterchangeModel();
//      GM03123PeaksRepC.loadResultsFromFile(new File(String.format("/mnt/shared-data/Vijay/perlstein_azure_run/amino_acid_results_water/perlstein_water.results_%d.json", startingIndex + 2)));
//
//      for (int i = 0; i < GM03123PeaksRepA.getResults().size(); i++) {
//        IonAnalysisInterchangeModel.ResultForMZ alignedPeak = alignedPeaks.getResults().get(i);
//        IonAnalysisInterchangeModel.ResultForMZ GM03123PeakRepA = GM03123PeaksRepA.getResults().get(i);
//        IonAnalysisInterchangeModel.ResultForMZ GM03123PeakRepB = GM03123PeaksRepB.getResults().get(i);
//        IonAnalysisInterchangeModel.ResultForMZ GM03123PeakRepC = GM03123PeaksRepC.getResults().get(i);
//
//        if (alignedPeak.getMolecules().get(0).getIntensity() > 0.0) {
//          GM03123infoTorTriples.put(alignedPeak,
//              Triple.of(GM03123PeakRepA.getMolecules().get(0).getIntensity(), GM03123PeakRepB.getMolecules().get(0).getIntensity(), GM03123PeakRepC.getMolecules().get(0).getIntensity()));
//        }
//      }
//
//      List<Double> leftRatios = new ArrayList<>();
//      List<Double> middleRatios = new ArrayList<>();
//      List<Double> rightRatios = new ArrayList<>();
//
//      for (Map.Entry<IonAnalysisInterchangeModel.ResultForMZ, Triple<Double, Double, Double>> entry : GM03123infoTorTriples.entrySet()) {
//
//        if (entry.getKey().getMolecules().get(0).getTime() > 15.0 && entry.getKey().getMolecules().get(0).getIntensity() > 1000.0) {
////          System.out.println(entry.getKey().getMolecules().get(0).getInchi());
////          System.out.println(entry.getKey().getMolecules().get(0).getIon());
//
//          Double total = entry.getValue().getLeft() + entry.getValue().getMiddle() + entry.getValue().getRight();
//
////          System.out.println(String.format("Rep A: %.2f", entry.getValue().getLeft()/total));
////          System.out.println(String.format("Rep B: %.2f", entry.getValue().getMiddle()/total));
////          System.out.println(String.format("Rep C: %.2f", entry.getValue().getRight()/total));
////          System.out.println("-------");
//
//          leftRatios.add(entry.getValue().getLeft() / total);
//          middleRatios.add(entry.getValue().getMiddle() / total);
//          rightRatios.add(entry.getValue().getRight() / total);
//        }
//      }
//
//      System.out.println(String.format("Count is %d", leftRatios.size()));
//
//      Double averageA = leftRatios.stream().reduce(0.0, (accum, newVal) -> accum + newVal) / leftRatios.size();
//      Double stdA = HitOrMissReplicateFilterAndTransformer.sd(leftRatios, averageA);
//
//      Double averageB = middleRatios.stream().reduce(0.0, (accum, newVal) -> accum + newVal) / middleRatios.size();
//      Double stdB = HitOrMissReplicateFilterAndTransformer.sd(middleRatios, averageB);
//
//      Double averageC = rightRatios.stream().reduce(0.0, (accum, newVal) -> accum + newVal) / rightRatios.size();
//      Double stdC = HitOrMissReplicateFilterAndTransformer.sd(rightRatios, averageC);
//
//      System.out.println(String.format("Replicate A has mean: %.2f and std: %.3f", averageA, stdA));
//      System.out.println(String.format("Replicate B has mean: %.2f and std: %.3f", averageB, stdB));
//      System.out.println(String.format("Replicate C has mean: %.2f and std: %.3f", averageC, stdC));
//    }

    Map<IonAnalysisInterchangeModel.ResultForMZ, Triple<Double, Double, Double>> GM03123infoTorTriples = new HashMap<>();

    IonAnalysisInterchangeModel GM03123PeaksRepA = new IonAnalysisInterchangeModel();
    GM03123PeaksRepA.loadResultsFromFile(new File(String.format("/mnt/shared-data/Vijay/perlstein_azure_run_water/GM18453_combined_water")));

    IonAnalysisInterchangeModel GM03123PeaksRepB = new IonAnalysisInterchangeModel();
    GM03123PeaksRepB.loadResultsFromFile(new File(String.format("/mnt/shared-data/Vijay/perlstein_azure_run_water/GM03123_combined_water")));

    IonAnalysisInterchangeModel GM03123PeaksRepC = new IonAnalysisInterchangeModel();
    GM03123PeaksRepC.loadResultsFromFile(new File(String.format("/mnt/shared-data/Vijay/perlstein_azure_run_water/GM09503_combined_water")));


    List<String> header = new ArrayList<>();
    header.add("GM18453_Average_Intensity");
    header.add("GM18453_Time");
    header.add("GM18453_Mass_Charge");
    header.add("GM03123_Average_Intensity");
    header.add("GM03123_Time");
    header.add("GM03123_Mass_Charge");
    header.add("GM09503_Average_Intensity");
    header.add("GM09503_Time");
    header.add("GM09503_Mass_Charge");

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File("out.tsv"));

    for (int i = 0; i < GM03123PeaksRepA.getResults().size(); i++) {
      IonAnalysisInterchangeModel.ResultForMZ alignedPeak = alignedPeaks.getResults().get(i);
      IonAnalysisInterchangeModel.ResultForMZ GM03123PeakRepA = GM03123PeaksRepA.getResults().get(i);
      IonAnalysisInterchangeModel.ResultForMZ GM03123PeakRepB = GM03123PeaksRepB.getResults().get(i);
      IonAnalysisInterchangeModel.ResultForMZ GM03123PeakRepC = GM03123PeaksRepC.getResults().get(i);

      if (alignedPeak.getMolecules().get(0).getIntensity() > 0.0) {

        Map<String, String> row = new HashMap<>();
        row.put("GM18453_Average_Intensity", GM03123PeakRepA.getMolecules().get(0).getAverageIntensity().toString());
        row.put("GM03123_Average_Intensity", GM03123PeakRepB.getMolecules().get(0).getAverageIntensity().toString());
        row.put("GM09503_Average_Intensity", GM03123PeakRepC.getMolecules().get(0).getAverageIntensity().toString());

        row.put("GM18453_Time", GM03123PeakRepA.getMolecules().get(0).getTime().toString());
        row.put("GM03123_Time", GM03123PeakRepB.getMolecules().get(0).getTime().toString());
        row.put("GM09503_Time", GM03123PeakRepC.getMolecules().get(0).getTime().toString());

        row.put("GM18453_Mass_Charge", GM03123PeakRepA.getMz().toString());
        row.put("GM03123_Mass_Charge", GM03123PeakRepB.getMz().toString());
        row.put("GM09503_Mass_Charge", GM03123PeakRepC.getMz().toString());

        writer.append(row);

        GM03123infoTorTriples.put(alignedPeak,
            Triple.of(GM03123PeakRepA.getMolecules().get(0).getAverageIntensity(), GM03123PeakRepB.getMolecules().get(0).getAverageIntensity(), GM03123PeakRepC.getMolecules().get(0).getAverageIntensity()));
      }
    }

    writer.close();

//    List<Double> leftRatios = new ArrayList<>();
//    List<Double> middleRatios = new ArrayList<>();
//    List<Double> rightRatios = new ArrayList<>();
//
//    for (Map.Entry<IonAnalysisInterchangeModel.ResultForMZ, Triple<Double, Double, Double>> entry : GM03123infoTorTriples.entrySet()) {
//
//      //if (entry.getKey().getMolecules().get(0).getTime() > 15.0 && entry.getKey().getMolecules().get(0).getIntensity() > 1000.0) {
////          System.out.println(entry.getKey().getMolecules().get(0).getInchi());
////          System.out.println(entry.getKey().getMolecules().get(0).getIon());
//
//      Double total = entry.getValue().getLeft() + entry.getValue().getMiddle() + entry.getValue().getRight();
//      total = entry.getValue().getRight();
//
//          System.out.println(String.format("Rep A: %.2f", entry.getValue().getLeft()/total));
//          System.out.println(String.format("Rep B: %.2f", entry.getValue().getMiddle()/total));
//          System.out.println(String.format("Rep C: %.2f", entry.getValue().getRight()/total));
//          System.out.println("-------");
//
//      leftRatios.add(entry.getValue().getLeft() / total);
//      middleRatios.add(entry.getValue().getMiddle() / total);
//      rightRatios.add(entry.getValue().getRight() / total);
//      //}
//    }
//
//    System.out.println(String.format("Count is %d", leftRatios.size()));
//
//    Double averageA = leftRatios.stream().reduce(0.0, (accum, newVal) -> accum + newVal) / leftRatios.size();
//    Double stdA = HitOrMissReplicateFilterAndTransformer.sd(leftRatios, averageA);
//
//    Double averageB = middleRatios.stream().reduce(0.0, (accum, newVal) -> accum + newVal) / middleRatios.size();
//    Double stdB = HitOrMissReplicateFilterAndTransformer.sd(middleRatios, averageB);
//
//    Double averageC = rightRatios.stream().reduce(0.0, (accum, newVal) -> accum + newVal) / rightRatios.size();
//    Double stdC = HitOrMissReplicateFilterAndTransformer.sd(rightRatios, averageC);
//
//    System.out.println(String.format("GM18453 has mean: %.2f and std: %.3f", averageA, stdA));
//    System.out.println(String.format("GM03123 has mean: %.2f and std: %.3f", averageB, stdB));
//    System.out.println(String.format("GM09503 has mean: %.2f and std: %.3f", averageC, stdC));
//


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
//      HELP_FORMATTER.printHelp(BestMoleculesPickerFromLCMSIonAnalysis.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
//      System.exit(1);
//    }
//
//    if (cl.hasOption("help")) {
//      HELP_FORMATTER.printHelp(BestMoleculesPickerFromLCMSIonAnalysis.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
//      System.exit(1);
//    }
//
//    List<String> positiveReplicateResults = new ArrayList<>(Arrays.asList(cl.getOptionValues(OPTION_INPUT_FILES)));
//
//    if (cl.hasOption(OPTION_MIN_OF_REPLICATES)) {
//      HitOrMissReplicateFilterAndTransformer transformer = new HitOrMissReplicateFilterAndTransformer();
//
//      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
//          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults), transformer);
//
//      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
//      return;
//    }
//
//    Double minSnrThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_SNR_THRESHOLD));
//    Double minIntensityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_INTENSITY_THRESHOLD));
//    Double minTimeThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_TIME_THRESHOLD));
//
//    if (cl.hasOption(OPTION_GET_IONS_SUPERSET)) {
//      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.getSupersetOfIonicVariants(
//          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
//          minSnrThreshold,
//          minIntensityThreshold,
//          minTimeThreshold);
//
//      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
//      return;
//    }
//
//    if (cl.hasOption(OPTION_THRESHOLD_ANALYSIS)) {
//
//      // We need to set this variable as a final since it is used in a lambda function below.
//      final Set<String> ions = new HashSet<>();
//      if (cl.hasOption(OPTION_FILTER_BY_IONS)) {
//        ions.addAll(Arrays.asList(cl.getOptionValues(OPTION_FILTER_BY_IONS)));
//      }
//
//      HitOrMissSingleSampleFilterAndTransformer hitOrMissSingleSampleTransformer =
//          new HitOrMissSingleSampleFilterAndTransformer(minIntensityThreshold, minSnrThreshold, minTimeThreshold, ions);
//
//      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateResultFiles(
//          IonAnalysisInterchangeModel.loadMultipleIonAnalysisInterchangeModelsFromFiles(positiveReplicateResults),
//          hitOrMissSingleSampleTransformer);
//
//      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
//    }
  }
}
