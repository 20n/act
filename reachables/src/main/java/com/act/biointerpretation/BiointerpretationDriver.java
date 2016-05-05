package com.act.biointerpretation;

import act.server.NoSQLAPI;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.ReactionException;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import com.act.biointerpretation.step2_desalting.Desalter;
import com.act.biointerpretation.step2_desalting.ReactionDesalter;
import com.act.biointerpretation.step3_cofactorremoval.CofactorRemover;
import com.act.biointerpretation.step4_mechanisminspection.MechanisticValidator;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class BiointerpretationDriver {
  private static final Logger LOGGER = LogManager.getLogger(BiointerpretationDriver.class);

  public static final String OPTION_CONFIGURATION_FILE = "c";
  public static final String OPTION_SINGLE_OPERATION = "o";
  public static final String OPTION_SINGLE_READ_DB = "r";
  public static final String OPTION_SINGLE_WRITE_DB = "w";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public enum BiointerpretationOperation {
    MERGE_REACTIONS,
    DESALT,
    REMOVE_COFACTORS,
    VALIDATE,
  }

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class drives one or more biointerpretation steps.  A single operation can be specified on the ",
      "command line, or a series of operations and databases can be specified in a JSON configuration file."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_CONFIGURATION_FILE)
        .argName("config file")
        .desc("JSON configuration file of steps to run in sequence")
        .hasArg()
        .longOpt("config")
    );
    add(Option.builder(OPTION_SINGLE_OPERATION)
        .argName("operation")
        .desc("Single operation to run on one read/write DB pair (requires db names), options are: " +
            StringUtils.join(BiointerpretationOperation.values(), ", "))
        .hasArg()
        .longOpt("op")
    );
    add(Option.builder(OPTION_SINGLE_READ_DB)
        .argName("db name")
        .desc("DB from which to read when performing a single operation")
        .hasArg()
        .longOpt("read")
    );
    add(Option.builder(OPTION_SINGLE_WRITE_DB)
        .argName("db name")
        .desc("DB to which to write when performing a single operation")
        .hasArg()
        .longOpt("write")
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};
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
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ReactionDesalter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    if (cl.hasOption(OPTION_CONFIGURATION_FILE)) {
      List<BiointerpretationStep> steps;
      File configFile = new File(cl.getOptionValue(OPTION_CONFIGURATION_FILE));
      if (!configFile.exists()) {
        String msg = String.format("Cannot find configuration file at %s", configFile.getAbsolutePath());
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }
      // Read the whole config file.
      try (InputStream is = new FileInputStream(configFile)) {
        steps = OBJECT_MAPPER.readValue(is, new TypeReference<List<BiointerpretationStep>>() {});
      } catch (IOException e) {
        LOGGER.error("Caught IO exception when attempting to read configuration file: %s", e.getMessage());
        throw e; // Crash after logging if the config file can't be read.
      }

      // Ask for explicit confirmation before dropping databases.
      LOGGER.info("Biointerpretation plan:");
      for (BiointerpretationStep step : steps) {
        crashIfInvalidDBName(step.getReadDBName());
        crashIfInvalidDBName(step.getWriteDBName());
        LOGGER.info("%s: %s -> %s", step.getOperation(), step.getReadDBName(), step.getWriteDBName());
      }
      LOGGER.warn("WARNING: each DB to be written will be dropped before the writing step commences");
      LOGGER.info("Proceed? [y/n]");
      String readLine;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
        readLine = reader.readLine();
      }
      readLine.trim();
      if ("y".equalsIgnoreCase(readLine) || "yes".equalsIgnoreCase(readLine)) {
        LOGGER.info("Biointerpretation plan confirmed, commencing");
        for (BiointerpretationStep step : steps) {
          performOperation(step, true);
        }
        LOGGER.info("Biointerpretation plan completed");
      } else {
        LOGGER.info("Biointerpretation plan not confirmed, exiting");
      }
    } else if (cl.hasOption(OPTION_SINGLE_OPERATION)) {
      if (!cl.hasOption(OPTION_SINGLE_READ_DB) || !cl.hasOption(OPTION_SINGLE_WRITE_DB)) {
        String msg = "Must specify read and write DB names when performing a single operation";
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }
      BiointerpretationOperation operation;
      try {
        operation = BiointerpretationOperation.valueOf(cl.getOptionValue(OPTION_SINGLE_OPERATION));
      } catch (IllegalArgumentException e) {
        LOGGER.error("Caught IllegalArgumentException when trying to parse operation '%s': %s",
            cl.getOptionValue(OPTION_SINGLE_OPERATION), e.getMessage());
        throw e; // Crash if we can't interpret the operation.
      }
      String readDB = crashIfInvalidDBName(cl.getOptionValue(OPTION_SINGLE_READ_DB));
      String writeDB = crashIfInvalidDBName(cl.getOptionValue(OPTION_SINGLE_WRITE_DB));

      performOperation(new BiointerpretationStep(operation, readDB, writeDB), false);
    } else {
      String msg = "Must specify either a config file or a single operation to perform.";
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  public static final Pattern VALID_DB_NAME_REGEX = Pattern.compile("[a-zA-Z][\\w]+");
  public static String crashIfInvalidDBName(String dbName) {
    if (!VALID_DB_NAME_REGEX.matcher(dbName).matches()) {
      String msg = String.format("Invalid database name: %s", dbName);
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
    return dbName;
  }

  public static void performOperation(BiointerpretationStep step, boolean forceDrop)
      throws IOException, LicenseProcessingException, ReactionException {
    // Drop the write DB and create a NoSQLAPI object that can be used by any step.
    NoSQLAPI.dropDB(step.writeDBName, forceDrop);
    // Note that this constructor call initializes the write DB collections and indices, so it must happen after dropDB.
    NoSQLAPI noSQLAPI = new NoSQLAPI(step.getReadDBName(), step.getWriteDBName());

    switch (step.getOperation()) {
      case MERGE_REACTIONS:
        LOGGER.info("Reaction merger starting (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        ReactionMerger merger = new ReactionMerger(noSQLAPI);
        merger.run();
        LOGGER.info("Reaction merger complete (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        break;
      case DESALT:
        LOGGER.info("Desalter starting (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        Desalter desalter = new Desalter();
        ReactionDesalter reactionDesalter = new ReactionDesalter(noSQLAPI, desalter);
        reactionDesalter.run();
        LOGGER.info("Reaction merger complete (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        break;
      case REMOVE_COFACTORS:
        LOGGER.info("Cofactor remover starting (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        CofactorRemover cofactorRemover = new CofactorRemover(noSQLAPI);
        cofactorRemover.loadCorpus();
        cofactorRemover.run();
        LOGGER.info("Cofactor remover complete (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        break;
      case VALIDATE:
        LOGGER.info("Mechanistic validator starting (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        MechanisticValidator validator = new MechanisticValidator(noSQLAPI);
        validator.loadCorpus();
        validator.initReactors();
        validator.run();
        LOGGER.info("Mechanistic validator complete (%s -> %s)", step.getReadDBName(), step.getWriteDBName());
        break;
      // No default is necessary since deserialization will ensure there is a corresponding operation in the enum.
    }
    // TODO: returning timing data and other stats for a final step-by-step report.
  }

  public static class BiointerpretationStep {
    @JsonProperty("operation")
    BiointerpretationOperation operation;
    @JsonProperty("read")
    String readDBName;
    @JsonProperty("write")
    String writeDBName;

    // Required for deserialization.
    public BiointerpretationStep() {

    }

    public BiointerpretationStep(BiointerpretationOperation operation, String readDBName, String writeDBName) {
      this.operation = operation;
      this.readDBName = readDBName;
      this.writeDBName = writeDBName;
    }

    public BiointerpretationOperation getOperation() {
      return operation;
    }

    public void setOperation(BiointerpretationOperation operation) {
      this.operation = operation;
    }

    public String getReadDBName() {
      return readDBName;
    }

    public void setReadDBName(String readDBName) {
      this.readDBName = readDBName;
    }

    public String getWriteDBName() {
      return writeDBName;
    }

    public void setWriteDBName(String writeDBName) {
      this.writeDBName = writeDBName;
    }
  }
}
