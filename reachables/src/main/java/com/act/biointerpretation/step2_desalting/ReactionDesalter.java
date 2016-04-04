package com.act.biointerpretation.step2_desalting;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
  private NoSQLAPI API;
  private Map<Long,Long> OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID;
  private Map<String, Long> INCHI_TO_NEW_ID;
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);
  private static final Integer BULK_NUMBER_OF_REACTIONS = 10000;
  private static final String FAKE = "FAKE";
  private static final String WRITE_DB = "synapse";
  private static final String READ_DB = "drknow";
  private static final String DESALTER_READ_DB = "lucille";

  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_OUTPUT_PREFIX)
        .argName("output prefix")
        .desc("A prefix for the output data/pdf files")
        .hasArg()
        .longOpt("output-prefix")
    );
  }};

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class reads reactions from a DB, transforms them by desalting these reactions, and then writes these reactions" +
          "to a write DB."
  }, "");
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
    API = new NoSQLAPI(READ_DB, WRITE_DB);
    OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID = new HashMap<>();
    INCHI_TO_NEW_ID = new HashMap<>();
  }

  /**
   * This function reads the products and reactions from the db, desalts them and writes it back.
   */
  public void run() {
    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = API.readRxnsFromInKnowledgeGraph();
    while(iterator.hasNext()) {
      Reaction rxn = iterator.next();
      Long[] newSubstrates = desaltChemicals(rxn.getSubstrates());
      rxn.setSubstrates(newSubstrates);
      Long[] newProducts = desaltChemicals(rxn.getProducts());
      rxn.setProducts(newProducts);

      //Write the modified Reaction to the db
      API.writeToOutKnowlegeGraph(rxn);
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

    for(int i = 0; i < chemIds.length; i++) {
      long originalId = chemIds[i];

      // If the chemical's ID maps to a single pre-seen entry, use its existing oldid
      if(OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID.containsKey(originalId)) {
        long prerun = OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID.get(originalId);
        newIds.add(prerun);
        continue;
      }

      // Otherwise need to clean the chemical
      Set<String> cleanedInchis = null;
      Chemical chemical = API.readChemicalFromInKnowledgeGraph(originalId);
      String inchi = chemical.getInChI();

      // If it's FAKE, just go with it
      if(inchi.contains(FAKE)) {
        long newId = API.writeToOutKnowlegeGraph(chemical); //Write to the db
        INCHI_TO_NEW_ID.put(inchi, newId);
        newIds.add(newId);
        OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID.put(originalId, newId);
        continue;
      }

      try {
        cleanedInchis = Desalter.desaltMolecule(inchi);
      } catch (Exception e) {
        // TODO: probably should handle this error differently, currently just letting pass unaltered
        long newId = API.writeToOutKnowlegeGraph(chemical); //Write to the db
        INCHI_TO_NEW_ID.put(inchi, newId);
        newIds.add(newId);
        OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID.put(originalId, newId);
        continue;
      }

      // For each cleaned chemical, put in DB or update ID
      for(String cleanInchi : cleanedInchis) {
        // If the cleaned inchi is already in DB, use existing ID, and hash the id
        if(INCHI_TO_NEW_ID.containsKey(cleanInchi)) {
          long preRun = INCHI_TO_NEW_ID.get(cleanInchi);
          newIds.add(preRun);
          OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID.put(originalId, preRun);
        }

        // Otherwise update the chemical, put into DB, and hash the id and inchi
        else {
          chemical.setInchi(cleanInchi);
          long newId = API.writeToOutKnowlegeGraph(chemical); //Write to the db
          INCHI_TO_NEW_ID.put(cleanInchi, newId);
          newIds.add(newId);
          OLD_CHEMICAL_ID_TO_NEW_CHEMICAL_ID.put(originalId, newId);
        }
      }
    }

    // Return the newIds as an array
    Long[] out = new Long[newIds.size()];

    int counter = 0;
    for (Long id : newIds) {
      out[counter] = id;
      counter++;
    }

    return out;
  }

  /**
   * This method is used for testing Desalter. It pulls BULK_NUMBER_OF_REACTIONS salty inchis from the database that are
   * in reactions, then cleans them and bins them into output files depending on whether they fail, clean to the same inchi,
   * or get modified.
   * @param outputPrefix The output file prefix where the analysis output will reside in
   */
  public static void examineReactionChemicals(String outputPrefix) {
    // Grab a large sample of chemicals that are in reactions. We do not read anything to the WRITE_DB below.
    List<String> saltyChemical = getSaltyReactions(new NoSQLAPI(DESALTER_READ_DB, WRITE_DB), BULK_NUMBER_OF_REACTIONS);
    LOGGER.debug(String.format("Total number of reactions being processed: %d", saltyChemical.size()));
    generateAnalysisOfDesaltingSaltyReactions(saltyChemical, outputPrefix);
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

    while(allReactions.hasNext()) {
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
        } else {
          previouslyEncounteredChemicalIDs.add(reactionId);
        }

        Chemical achem = api.readChemicalFromInKnowledgeGraph(reactionId);
        String inchi = achem.getInChI();

        if (inchi.contains(FAKE)) {
          continue;
        }

        try {
          Desalter.InchiToSmiles(inchi);
        } catch (Exception err) {
          LOGGER.error("Exception message: %s", err.getMessage());
          continue;
        }

        saltyChemicals.add(inchi);
      }
    }

    outputSaltyChemicals.addAll(saltyChemicals);
    return outputSaltyChemicals;
  }

  /**
   * This function bins each reaction into modified, unchanced, errors and complex files based on
   * processing them through the desalter module.
   * @param salties A list of reactions
   * @param outputPrefix The output prefix for the generated files
   */
  private static void generateAnalysisOfDesaltingSaltyReactions(List<String> salties, String outputPrefix) {
    try {
      FileWriter subtrateModifiedFileWriter = new FileWriter(new File(outputPrefix + "_modified.txt"));
      FileWriter substrateUnchangedFileWriter = new FileWriter(new File(outputPrefix + "_unchaged.txt"));
      FileWriter substrateErrorsFileWriter = new FileWriter(new File(outputPrefix + "_errors.txt"));
      FileWriter substrateComplexFileWriter = new FileWriter(new File(outputPrefix + "_complex.txt"));

      for (int i = 0; i < salties.size(); i++) {
        String salty = salties.get(i);
        String saltySmile = null;
        try {
          saltySmile = Desalter.InchiToSmiles(salty);
        } catch (Exception err) {
          substrateErrorsFileWriter.append("InchiToSmiles1\t" + salty + "\r\n");
          continue;
        }

        Set<String> results = null;
        try {
          results = Desalter.desaltMolecule(salty);
        } catch (Exception err) {
          substrateErrorsFileWriter.append("cleaned\t" + salty + "\r\n");
          err.printStackTrace();
          continue;
        }

        //Not sure results can be size zero or null, but check anyway
        if (results == null) {
          substrateErrorsFileWriter.append("clean results are null:\t" + salty + "\r\n");
          continue;
        }
        if (results.isEmpty()) {
          substrateErrorsFileWriter.append("clean results are empty:\t" + salty + "\r\n");
          continue;
        }

        //If cleaning resulted in a single organic product
        if (results.size() == 1) {
          String cleaned = results.iterator().next();
          String cleanSmile = null;
          try {
            cleanSmile = Desalter.InchiToSmiles(cleaned);
          } catch (Exception err) {
            substrateErrorsFileWriter.append("InchiToSmiles2\t" + salty + "\r\n");
          }

          if (!salty.equals(cleaned)) {
            subtrateModifiedFileWriter.append(salty + "\t" + cleaned + "\t" + saltySmile + "\t" + cleanSmile + "\r\n");
          } else {
            substrateUnchangedFileWriter.append(salty + "\t" + saltySmile + "\r\n");
          }
        }
        //Otherwise there were multiple organic products
        else {
          substrateComplexFileWriter.append(">>\t" + salty + "\t" + saltySmile + "\r\n");
          for (String inchi : results) {
            substrateComplexFileWriter.append("\t" + inchi + "\t" + Desalter.InchiToSmiles(inchi) + "\r\n");
          }
        }
      }
    } catch (IOException exception) {
      LOGGER.error(String.format("IOException: %s", exception.getMessage()));
    }
  }
}
