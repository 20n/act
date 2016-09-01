package com.act.lcms.db.analysis;

import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
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
  public static final String OPTION_GET_CHEMICAL_STATISTICS = "c";

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
    add(Option.builder(OPTION_GET_CHEMICAL_STATISTICS)
        .argName("get chemical statistics")
        .desc("Get chemicals from input file")
        .hasArg()
        .longOpt("chemical-statistics")
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

  public static Set<String> readChemicalsFromFile(File in) throws IOException {
    FileReader fileReader = new FileReader(in);
    Set<String> inchis = new HashSet<>();

    try (BufferedReader reader = new BufferedReader(fileReader)) {
      String inchi = null;
      while((inchi = reader.readLine()) != null) {
        inchis.add(inchi);
      }
    }

    return inchis;
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

    //List<String> positiveReplicateResults = new ArrayList<>(Arrays.asList(cl.getOptionValues(OPTION_INPUT_FILES)));

    if (cl.hasOption(OPTION_GET_CHEMICAL_STATISTICS)) {

        Set<String> inchis = readChemicalsFromFile(new File("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/inchis"));

      IonAnalysisInterchangeModel minPositiveModel = new IonAnalysisInterchangeModel();
      minPositiveModel.loadResultsFromFile(new File("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/ss_chris_min"));

      List<String> negFiles = new ArrayList<>();
      negFiles.add("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/lr_d1_ur_mn");
      negFiles.add("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/lr_d2_ur_mn");
      negFiles.add("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/lr_d2_ur_ev");
      negFiles.add("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/lr_d1_ur_ev");
      negFiles.add("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/ss_d1_ur_ev.json");
      negFiles.add("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/ss_d1_ur_mn.json");
      negFiles.add("/mnt/shared-data/Vijay/jaffna/issue_371_analysis/ss_d2_ur_mn.json");

      List<IonAnalysisInterchangeModel> negModels = new ArrayList<>();
      for (String negFile : negFiles) {
        IonAnalysisInterchangeModel negModel = new IonAnalysisInterchangeModel();
        negModel.loadResultsFromFile(new File(negFile));
        negModels.add(negModel);
      }

      try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(new File("out.inchi")))) {
        for (String inchi : inchis) {
          for (int i = 0; i < minPositiveModel.getResults().size(); i++) {
            for (int j = 0; j < minPositiveModel.getResults().get(i).getMolecules().size(); j++) {
              IonAnalysisInterchangeModel.HitOrMiss hitOrMiss = minPositiveModel.getResults().get(i).getMolecules().get(j);

              if (hitOrMiss.getInchi().equals(inchi) &&
                  hitOrMiss.getIon().equals("M+H") &&
                  hitOrMiss.getIntensity() > 0.0 &&
                  hitOrMiss.getSnr() > 0.0) {

                Double maxIntensity = Double.MIN_VALUE;
                Double maxSNR = Double.MIN_VALUE;

                for (int k = 0; k < negModels.size(); k++) {
                  IonAnalysisInterchangeModel.HitOrMiss hitOrMissNeg = negModels.get(k).getResults().get(i).getMolecules().get(j);
                  if ((hitOrMissNeg.getTime() > hitOrMiss.getTime() - 5.0) && (hitOrMissNeg.getTime() < hitOrMiss.getTime() + 5.0)) {
                    maxIntensity = Math.max(maxIntensity, hitOrMissNeg.getIntensity());
                    maxSNR = Math.max(maxSNR, hitOrMissNeg.getSnr());
                  }
                }

                if (hitOrMiss.getIntensity()/maxIntensity > 5.0 && hitOrMiss.getSnr()/maxSNR > 10.0) {
                  predictionWriter.write(inchi);
                  predictionWriter.newLine();
                  predictionWriter.flush();
                }
              }
            }
          }
        }
      }

//      TSVParser parser = new TSVParser();
//      parser.parse(new File("/Users/vijaytramakrishnan/Desktop/porfovour/test.tsv"));
//      List<Map<String, String>> inputRows = parser.getResults();
//
//      Map<String, String> inchiToName = new HashMap<>();
//      Set<String> chemsOfInterest = new HashSet<>();
//      for(Map<String, String> cell : inputRows) {
//        inchiToName.put(cell.get("inchi"), cell.get("name"));
//        chemsOfInterest.add(cell.get("inchi"));
//      }
//
//
//      L2PredictionCorpus corpus47 = new L2PredictionCorpus();
//      corpus47 = corpus47.readPredictionsFromJsonFile(new File("/Volumes/shared-data/Vijay/jaffna/projections/predictions.47"));
//
//      L2PredictionCorpus corpus228 = new L2PredictionCorpus();
//      corpus228 = corpus228.readPredictionsFromJsonFile(new File("/Volumes/shared-data/Vijay/jaffna/projections/predictions.228"));
//
//
//      Set<String> inchis = readChemicalsFromFile(new File(cl.getOptionValue(OPTION_GET_CHEMICAL_STATISTICS)));
//
//      List<String> header = new ArrayList<>();
//      header.add("Name");
//      header.add("Intensity");
//      header.add("SNR");
//      header.add("Time");
//      header.add("Sample");
//      header.add("Inchi");
//
//      NumberFormat formatter = new DecimalFormat("0.#E0");
//
//      TSVWriter<String, String> writer = new TSVWriter<>(header);
//      writer.open(new File(cl.getOptionValue(OPTION_OUTPUT_FILE)));
//
//      for (String file : positiveReplicateResults) {
//        IonAnalysisInterchangeModel model = new IonAnalysisInterchangeModel();
//        model.loadResultsFromFile(new File(file));
//
//        for (IonAnalysisInterchangeModel.ResultForMZ resultForMZ : model.getResults()) {
//          for (IonAnalysisInterchangeModel.HitOrMiss hitOrMiss : resultForMZ.getMolecules()) {
//            if (inchis.contains(hitOrMiss.getInchi()) && hitOrMiss.getIon().equals("M+H")) {
//
//              Map<String, String> row = new HashMap<>();
//
//              if (chemsOfInterest.contains(hitOrMiss.getInchi())) {
//                row.put("Name", inchiToName.get(hitOrMiss.getInchi()));
//                row.put("Inchi", "");
//              } else {
//
//                if (corpus47.getUniqueProductInchis().contains(hitOrMiss.getInchi())) {
//                  Set<String> substrates =
//                      corpus47.applyFilter(l2Prediction -> l2Prediction.getProductInchis().contains(hitOrMiss.getInchi())).getUniqueSubstrateInchis();
//
//                  for (String substrate : substrates) {
//                    row.put("Name", inchiToName.get(substrate) + "_RO47");
//                    row.put("Inchi", hitOrMiss.getInchi());
//                    break;
//                  }
//                }
//
//                if (corpus228.getUniqueProductInchis().contains(hitOrMiss.getInchi())) {
//                  Set<String> substrates =
//                      corpus228.applyFilter(l2Prediction -> l2Prediction.getProductInchis().contains(hitOrMiss.getInchi())).getUniqueSubstrateInchis();
//
//                  for (String substrate : substrates) {
//                    row.put("Name", inchiToName.get(substrate) + "_RO228");
//                    row.put("Inchi", hitOrMiss.getInchi());
//                    break;
//                  }
//                }
//              }
//
//              row.put("Intensity", formatter.format(hitOrMiss.getIntensity()));
//              row.put("SNR", formatter.format(hitOrMiss.getSnr()));
//              row.put("Time", new DecimalFormat("0.00").format(hitOrMiss.getTime()));
//              row.put("Sample", file.split("/")[6]);
//
//              writer.append(row);
//              writer.flush();
//            }
//          }
//        }
//      }
//
//      writer.close();

      return;
    }

//    Double minSnrThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_SNR_THRESHOLD));
//    Double minIntensityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_INTENSITY_THRESHOLD));
//    Double minTimeThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_TIME_THRESHOLD));
//
//    Set<String> inchis = cl.hasOption(OPTION_GET_IONS_SUPERSET) ?
//        IonAnalysisInterchangeModel.getSupersetOfIonicVariants(positiveReplicateResults, minSnrThreshold,
//            minIntensityThreshold, minTimeThreshold) :
//        IonAnalysisInterchangeModel.getAllMoleculeHitsFromMultiplePositiveReplicateFiles(
//        positiveReplicateResults, minSnrThreshold, minIntensityThreshold, minTimeThreshold);
//
//    try (BufferedWriter predictionWriter = new BufferedWriter(new FileWriter(cl.getOptionValue(OPTION_OUTPUT_FILE)))) {
//      for (String inchi : inchis) {
//        predictionWriter.append(inchi);
//        predictionWriter.newLine();
//      }
//    }
  }
}
