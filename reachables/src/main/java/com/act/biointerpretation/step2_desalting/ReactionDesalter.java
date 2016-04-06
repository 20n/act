package com.act.biointerpretation.step2_desalting;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
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

/**
 * ReactionDesalter itself does the processing of the database using an instance of Desalter.
 * This class creates Synapse from Dr. Know.  Synapse is the database in which the chemicals
 * have been inspected for containing multiple species or ionized forms, and corrected.
 *
 * Created by jca20n on 10/22/15.
 */
public class ReactionDesalter {
  private static final Integer BULK_NUMBER_OF_REACTIONS = 10000;
  private static final String FAKE = "FAKE";
  private static final String WRITE_DB = "synapse";
  private static final String READ_DB = "drknow";
  private static final String DESALTER_READ_DB = "lucille";
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);
  private NoSQLAPI api;
  private Map<Long, Long> oldChemicalIdToNewChemicalId;
  private Map<String, Long> inchiToNewId;

  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class reads reactions from a DB, transforms them by desalting these reactions, and then writes these reactions" +
          "to a write DB."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("output prefix")
        .desc("A prefix for the output data/pdf files")
        .hasArg()
        .longOpt("output-prefix")
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

  public static void main(String[] args) {
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

    if (cl.hasOption(OPTION_OUTPUT_PREFIX)) {
      String outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX);
      examineReactionChemicals(outAnalysis);
    } else {
      ReactionDesalter runner = new ReactionDesalter();
      runner.run();
    }
  }

  public ReactionDesalter() {
    // Delete all records in the WRITE_DB
    NoSQLAPI.dropDB(WRITE_DB);
    api = new NoSQLAPI(READ_DB, WRITE_DB);
    oldChemicalIdToNewChemicalId = new HashMap<>();
    inchiToNewId = new HashMap<>();
  }

  /**
   * This function reads the products and reactions from the db, desalts them and writes it back.
   */
  public void run() {
    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();
    while (iterator.hasNext()) {
      Reaction rxn = iterator.next();
      Long[] newSubstrates = desaltChemicals(rxn.getSubstrates());
      rxn.setSubstrates(newSubstrates);
      Long[] newProducts = desaltChemicals(rxn.getProducts());
      rxn.setProducts(newProducts);

      //Write the modified Reaction to the db
      api.writeToOutKnowlegeGraph(rxn);
    }

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  /**
   * This function desalts chemicals and returns the resulting ids of the modified chemicals. If the chemicals
   * cannot be desalted, we just pass the chemical unaltered.
   * @param chemIds The input list of chemical ids.
   * @return A list of output ids of desalted chemicals
   */
  private Long[] desaltChemicals(Long[] chemIds) {
    Set<Long> newIds = new HashSet<>();

    for (int i = 0; i < chemIds.length; i++) {
      long originalId = chemIds[i];

      // If the chemical's ID maps to a single pre-seen entry, use its existing oldid
      if (oldChemicalIdToNewChemicalId.containsKey(originalId)) {
        long prerun = oldChemicalIdToNewChemicalId.get(originalId);
        newIds.add(prerun);
        continue;
      }

      // Otherwise need to clean the chemical
      Set<String> cleanedInchis = null;
      Chemical chemical = api.readChemicalFromInKnowledgeGraph(originalId);
      String inchi = chemical.getInChI();

      // If it's FAKE, just go with it
      if (inchi.contains(FAKE)) {
        long newId = api.writeToOutKnowlegeGraph(chemical); //Write to the db
        inchiToNewId.put(inchi, newId);
        newIds.add(newId);
        oldChemicalIdToNewChemicalId.put(originalId, newId);
        continue;
      }

      try {
        cleanedInchis = Desalter.desaltMolecule(inchi);
      } catch (Exception e) {
        // TODO: probably should handle this error differently, currently just letting pass unaltered
        LOGGER.error("Exception in desalting the inchi: %s", e.getMessage());
        long newId = api.writeToOutKnowlegeGraph(chemical); //Write to the db
        inchiToNewId.put(inchi, newId);
        newIds.add(newId);
        oldChemicalIdToNewChemicalId.put(originalId, newId);
        continue;
      }

      // For each cleaned chemical, put in DB or update ID
      for (String cleanInchi : cleanedInchis) {
        // If the cleaned inchi is already in DB, use existing ID, and hash the id
        if(inchiToNewId.containsKey(cleanInchi)) {
          long preRun = inchiToNewId.get(cleanInchi);
          newIds.add(preRun);
          oldChemicalIdToNewChemicalId.put(originalId, preRun);
        } else {
          // Otherwise update the chemical, put into DB, and hash the id and inchi
          chemical.setInchi(cleanInchi);
          long newId = api.writeToOutKnowlegeGraph(chemical); //Write to the db
          inchiToNewId.put(cleanInchi, newId);
          newIds.add(newId);
          oldChemicalIdToNewChemicalId.put(originalId, newId);
        }
      }
    }

    // Sort and return the array.
    List<Long> orderedListOfReactionIds = Arrays.asList((Long[]) newIds.toArray());
    Collections.sort(orderedListOfReactionIds);

    return orderedListOfReactionIds.toArray(new Long[orderedListOfReactionIds.size()]);
  }

  /**
   * This method is used for testing Desalter. It pulls BULK_NUMBER_OF_REACTIONS salty inchis from the database that are
   * in reactions, then cleans them and bins them into output files depending on whether they fail, clean to the same inchi,
   * or get modified.
   * @param outputPrefix The output file prefix where the analysis output will reside in
   */
  public static void examineReactionChemicals(String outputPrefix) {
    // Grab a large sample of chemicals that are in reactions. We do not read anything to the WRITE_DB below.
    List<String> saltyChemicals = getSaltyReactions(new NoSQLAPI(DESALTER_READ_DB, WRITE_DB), BULK_NUMBER_OF_REACTIONS);
    LOGGER.debug(String.format("Total number of reactions being processed: %d", saltyChemicals.size()));
    generateAnalysisOfDesaltingSaltyChemicals(saltyChemicals, outputPrefix);
  }

  /**
   * This function extracts a set number of reactions containing salts from the DB
   * @param api The api to extract data from.
   * @param numberOfChemicals The total number of reactions being examined.
   * @return A list of reaction strings
   */
  private static List<String> getSaltyReactions(NoSQLAPI api, Integer numberOfChemicals) {
    Set<String> saltyChemicals = new HashSet<>();
    Iterator<Reaction> allReactions = api.readRxnsFromInKnowledgeGraph();

    Set<Long> previouslyEncounteredChemicalIDs = new HashSet<>();
    List<String> outputSaltyChemicals = new ArrayList<>();

    while (allReactions.hasNext()) {
      Reaction reaction = allReactions.next();
      Set<Long> reactionParticipants = new HashSet<>();

      for (Long substrateOrProductId : Stream.concat(Arrays.asList(reaction.getSubstrates()).stream(),
          Arrays.asList(reaction.getProducts()).stream()).collect(Collectors.toList())) {
        reactionParticipants.add(substrateOrProductId);
      }

      for (Long reactionId : reactionParticipants) {

        if (saltyChemicals.size() >= numberOfChemicals) {
          outputSaltyChemicals.addAll(saltyChemicals);
          return outputSaltyChemicals;
        }

        if (previouslyEncounteredChemicalIDs.contains(reactionId)) {
          continue;
        }

        previouslyEncounteredChemicalIDs.add(reactionId);
        Chemical achem = api.readChemicalFromInKnowledgeGraph(reactionId);
        String inchi = achem.getInChI();

        if (inchi.contains(FAKE)) {
          continue;
        }

        try {
          Desalter.InchiToSmiles(inchi);
        } catch (Exception err) {
          LOGGER.error("Exception caught while trying to convert inchi to smile: %s\n", err.getMessage());
          continue;
        }

        saltyChemicals.add(inchi);
      }
    }

    outputSaltyChemicals.addAll(saltyChemicals);
    Collections.sort(outputSaltyChemicals);
    return outputSaltyChemicals;
  }

  /**
   * This function bins each reaction into modified, unchanged, errors and complex files based on
   * processing them through the desalter module.
   * @param salties A list of reactions
   * @param outputPrefix The output prefix for the generated files
   */
  private static void generateAnalysisOfDesaltingSaltyChemicals(List<String> salties, String outputPrefix) {
    try {
      BufferedWriter substrateModifiedFileWriter =
          new BufferedWriter(new FileWriter(new File(outputPrefix + "_modified.txt")));
      BufferedWriter substrateUnchangedFileWriter =
          new BufferedWriter(new FileWriter(new File(outputPrefix + "_unchanged.txt")));
      BufferedWriter substrateErrorsFileWriter =
          new BufferedWriter(new FileWriter(new File(outputPrefix + "_errors.txt")));
      BufferedWriter substrateComplexFileWriter =
          new BufferedWriter(new FileWriter(new File(outputPrefix + "_complex.txt")));

      for (int i = 0; i < salties.size(); i++) {
        String salty = salties.get(i);
        String saltySmile = null;
        try {
          saltySmile = Desalter.InchiToSmiles(salty);
        } catch (Exception err) {
          LOGGER.error(String.format("Exception caught while desalting inchi: %s with error message: %s\n", salty,
              err.getMessage()));
          substrateErrorsFileWriter.write("InchiToSmiles1\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }

        Set<String> results = null;
        try {
          results = Desalter.desaltMolecule(salty);
        } catch (Exception err) {
          LOGGER.error(String.format("Exception caught while desalting inchi: %s with error message: %s\n", salty,
              err.getMessage()));
          substrateErrorsFileWriter.append("cleaned\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }

        //Not sure results can be size zero or null, but check anyway
        if (results == null) {
          LOGGER.error(String.format("Clean results are null for chemical: %s\n", salty));
          substrateErrorsFileWriter.append("clean results are null:\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }
        if (results.isEmpty()) {
          LOGGER.error(String.format("Clean results are empty for chemical: %s\n", salty));
          substrateErrorsFileWriter.append("clean results are empty:\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }

        //If cleaning resulted in a single organic product
        if (results.size() == 1) {
          String cleaned = results.iterator().next();
          String cleanSmile = null;
          try {
            cleanSmile = Desalter.InchiToSmiles(cleaned);
          } catch (Exception err) {
            LOGGER.error(String.format("Exception caught while desalting inchi: %s with error message: %s\n", cleaned,
                err.getMessage()));
            substrateErrorsFileWriter.append("InchiToSmiles2\t" + salty);
            substrateErrorsFileWriter.newLine();
          }

          if (!salty.equals(cleaned)) {
            String[] stringElements = {salty, cleaned, saltySmile, cleanSmile};
            substrateModifiedFileWriter.append(StringUtils.join(Arrays.asList(stringElements), "\t"));
            substrateModifiedFileWriter.newLine();
          } else {
            String[] stringElements = {salty, saltySmile};
            substrateUnchangedFileWriter.append(StringUtils.join(Arrays.asList(stringElements), "\t"));
            substrateUnchangedFileWriter.newLine();
          }
        }
        //Otherwise there were multiple organic products
        else {
          substrateComplexFileWriter.append(">>\t" + salty + "\t" + saltySmile);
          substrateComplexFileWriter.newLine();
          for (String inchi : results) {
            substrateComplexFileWriter.append("\t" + inchi + "\t" + Desalter.InchiToSmiles(inchi));
            substrateComplexFileWriter.newLine();
          }
        }
      }
    } catch (IOException exception) {
      LOGGER.error(String.format("IOException: %s", exception.getMessage()));
    }
  }
}
