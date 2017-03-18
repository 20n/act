/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.lcms.db.analysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.db.io.report.IonAnalysisInterchangeModel;
import com.act.utils.TSVWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BestMoleculesPickerFromLCMSIonAnalysis {

  private static final Logger LOGGER = LogManager.getFormatterLogger(BestMoleculesPickerFromLCMSIonAnalysis.class);
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
          writer.append(row);
          writer.flush();
        }
      }

      writer.close();
    }
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

    List<String> positiveReplicateResults = new ArrayList<>(Arrays.asList(cl.getOptionValues(OPTION_INPUT_FILES)));

    if (cl.hasOption(OPTION_MIN_OF_REPLICATES)) {
      HitOrMissReplicateFilterAndTransformer transformer = new HitOrMissReplicateFilterAndTransformer();

      List<IonAnalysisInterchangeModel> models = positiveReplicateResults.stream().map(file -> {
        try {
          return IonAnalysisInterchangeModel.loadIonAnalysisInterchangeModelFromFile(file);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }).collect(Collectors.toList());

      IonAnalysisInterchangeModel transformedModel = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromMultipleReplicateModels(
          models, transformer);

      printInchisAndIonsToFile(transformedModel, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
      return;
    }

    Double minSnrThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_SNR_THRESHOLD));
    Double minIntensityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_INTENSITY_THRESHOLD));
    Double minTimeThreshold = Double.parseDouble(cl.getOptionValue(OPTION_MIN_TIME_THRESHOLD));

    if (cl.hasOption(OPTION_GET_IONS_SUPERSET)) {
      List<IonAnalysisInterchangeModel> models = positiveReplicateResults.stream().map(file -> {
        try {
          return IonAnalysisInterchangeModel.loadIonAnalysisInterchangeModelFromFile(file);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }).collect(Collectors.toList());

      IonAnalysisInterchangeModel transformedModel = IonAnalysisInterchangeModel.getSupersetOfIonicVariants(
          models,
          minSnrThreshold,
          minIntensityThreshold,
          minTimeThreshold);

      printInchisAndIonsToFile(transformedModel, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
      return;
    }

    if (cl.hasOption(OPTION_THRESHOLD_ANALYSIS)) {

      if (positiveReplicateResults.size() > 1) {
        LOGGER.error("Since this is a threshold analysis, the number of positive replicates should be 1.");
        System.exit(1);
      }

      // We need to set this variable as a final since it is used in a lambda function below.
      final Set<String> ions = new HashSet<>();
      if (cl.hasOption(OPTION_FILTER_BY_IONS)) {
        ions.addAll(Arrays.asList(cl.getOptionValues(OPTION_FILTER_BY_IONS)));
      }

      HitOrMissSingleSampleFilterAndTransformer hitOrMissSingleSampleTransformer =
          new HitOrMissSingleSampleFilterAndTransformer(minIntensityThreshold, minSnrThreshold, minTimeThreshold, ions);

      IonAnalysisInterchangeModel model = IonAnalysisInterchangeModel.filterAndOperateOnMoleculesFromModel(
          IonAnalysisInterchangeModel.loadIonAnalysisInterchangeModelFromFile(positiveReplicateResults.get(0)),
          hitOrMissSingleSampleTransformer);

      printInchisAndIonsToFile(model, cl.getOptionValue(OPTION_OUTPUT_FILE), cl.hasOption(OPTION_JSON_FORMAT));
    }
  }
}
