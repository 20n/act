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

import com.act.biointerpretation.reactionmerging.ReactionMerger;
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
import org.json.JSONObject;

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
  private static final String READ_DB = "actv01";
  private static final String DESALTER_READ_DB = "lucille";
  private static final Logger LOGGER = LogManager.getLogger(ReactionDesalter.class);
  private static final Boolean IS_SUBSTRATE = true;
  private NoSQLAPI api;
  private Map<Long, List<Long>> oldChemicalIdToNewChemicalId;
  private Map<String, Long> inchiToNewId;
  private Desalter desalter;

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

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ReactionDesalter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    if (cl.hasOption(OPTION_OUTPUT_PREFIX)) {
      String outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX);
      ReactionDesalter runner = new ReactionDesalter(new NoSQLAPI(READ_DB, WRITE_DB), new Desalter());
      runner.examineReactionChemicals(outAnalysis);
    } else {
      // Delete all records in the WRITE_DB
      NoSQLAPI.dropDB(WRITE_DB);
      ReactionDesalter runner = new ReactionDesalter(new NoSQLAPI(READ_DB, WRITE_DB), new Desalter());
      runner.run();
    }
  }

  public ReactionDesalter(NoSQLAPI inputApi, Desalter inputDesalter) {
    api = inputApi;
    oldChemicalIdToNewChemicalId = new HashMap<>();
    inchiToNewId = new HashMap<>();
    desalter = inputDesalter;
  }

  /**
   * This function reads the products and reactions from the db, desalts them and writes it back.
   */
  public void run() {
    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();
    ReactionMerger rxnMerger = new ReactionMerger(api);

    //Scan through all Reactions and process each one.
    Iterator<Reaction> reactionIterator = api.readRxnsFromInKnowledgeGraph();

    while (reactionIterator.hasNext()) {
      Reaction rxn = reactionIterator.next();
      int oldUUID = rxn.getUUID();
      Set<JSONObject> oldProteinData = new HashSet<>(rxn.getProteinData());
      Set<Long> oldIds = new HashSet<>(Arrays.asList(rxn.getSubstrates()));
      oldIds.addAll(Arrays.asList(rxn.getProducts()));

      // Desalt the reaction
      Long[] newSubstrates = desaltChemicals(rxn.getSubstrates());
      rxn.setSubstrates(newSubstrates);
      Long[] newProducts = desaltChemicals(rxn.getProducts());
      rxn.setProducts(newProducts);

      updateSubstratesProductsCoefficients(rxn, newSubstrates, IS_SUBSTRATE, oldIds);
      updateSubstratesProductsCoefficients(rxn, newProducts, !IS_SUBSTRATE, oldIds);

      int newId = api.writeToOutKnowlegeGraph(rxn);

      rxn.removeAllProteinData();

      for (JSONObject protein : oldProteinData) {
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        protein.put("source_reaction_id", oldUUID);
        JSONObject newProteinData = rxnMerger.migrateProteinData(protein, Long.valueOf(newId), rxn);
        rxn.addProteinData(newProteinData);
      }

      // Update the reaction in the DB with the newly migrated protein data.
      api.getWriteDB().updateActReaction(rxn, newId);
    }

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  private void updateSubstratesProductsCoefficients(Reaction rxn, Long[] newSubstratesOrProducts, Boolean isSubstrate,
                                                    Set<Long> oldIdsOfReaction) {
    Map<Long, Integer> idToCoefficient = new HashMap<>();
    Set<Long> setOfIdsForMembershipChecking = new HashSet<>(Arrays.asList(newSubstratesOrProducts));

    for (Long oldId : oldIdsOfReaction) {
      List<Long> newIds = oldChemicalIdToNewChemicalId.get(oldId);
      for (Long newId : newIds) {
        if (setOfIdsForMembershipChecking.contains(newId)) {
          if (isSubstrate) {
            idToCoefficient.put(newId, rxn.getSubstrateCoefficient(oldId));
          } else {
            idToCoefficient.put(newId, rxn.getProductCoefficient(oldId));
          }
        }
      }
    }

    if (isSubstrate) {
      rxn.setAllSubstrateCoefficients(idToCoefficient);
    } else {
      rxn.setAllProductCoefficients(idToCoefficient);
    }
  }

  /**
   * This function desalts chemicals and returns the resulting ids of the modified chemicals. If the chemicals
   * cannot be desalted, we just pass the chemical unaltered.
   *
   * @param chemIds The input list of chemical ids.
   * @return A list of output ids of desalted chemicals
   */
  private Long[] desaltChemicals(Long[] chemIds) {
    Set<Long> newIds = new HashSet<>();

    for (int i = 0; i < chemIds.length; i++) {
      long originalId = chemIds[i];

      // If the chemical's ID maps to a single pre-seen entry, use its existing old id
      if (oldChemicalIdToNewChemicalId.containsKey(originalId)) {
        List<Long> preRun = oldChemicalIdToNewChemicalId.get(originalId);
        newIds.addAll(preRun);
        continue;
      }

      // Otherwise need to clean the chemical
      Set<String> cleanedInchis = null;
      Chemical chemical = api.readChemicalFromInKnowledgeGraph(originalId);
      String inchi = chemical.getInChI();

      // If it's FAKE, just go with it
      if (inchi.contains(FAKE)) {
        long newId = api.writeToOutKnowlegeGraph(chemical); //Write to the db
        List<Long> singletonId = Collections.singletonList(newId);
        inchiToNewId.put(inchi, newId);
        newIds.add(newId);
        oldChemicalIdToNewChemicalId.put(originalId, singletonId);
        continue;
      }

      try {
        cleanedInchis = desalter.desaltMolecule(inchi);
      } catch (Exception e) {
        // TODO: probably should handle this error differently, currently just letting pass unaltered
        LOGGER.error(String.format("Exception in desalting the inchi: %s", e.getMessage()));
        long newId = api.writeToOutKnowlegeGraph(chemical); //Write to the db
        List<Long> singletonId = Collections.singletonList(newId);
        inchiToNewId.put(inchi, newId);
        newIds.add(newId);
        oldChemicalIdToNewChemicalId.put(originalId, singletonId);
        continue;
      }

      // For each cleaned chemical, put in DB or update ID
      for (String cleanInchi : cleanedInchis) {
        // If the cleaned inchi is already in DB, use existing ID, and hash the id
        long id;

        if (inchiToNewId.containsKey(cleanInchi)) {
          id = inchiToNewId.get(cleanInchi);
        } else {
          // Otherwise update the chemical, put into DB, and hash the id and inchi
          chemical.setInchi(cleanInchi);
          id = api.writeToOutKnowlegeGraph(chemical); // Write to the db
          inchiToNewId.put(cleanInchi, id);
        }

        newIds.add(id);
        List<Long> results = oldChemicalIdToNewChemicalId.get(originalId);
        if (results == null) {
          results = new ArrayList<>();
          oldChemicalIdToNewChemicalId.put(originalId, results);
        }
        results.add(id);
      }
    }

    // Sort and return the array.
    Long[] newIdsArray = new Long[newIds.size()];
    List<Long> orderedListOfReactionIds = Arrays.asList(newIds.toArray(newIdsArray));
    Collections.sort(orderedListOfReactionIds);

    return orderedListOfReactionIds.toArray(new Long[orderedListOfReactionIds.size()]);
  }

  /**
   * This method is used for testing Desalter. It pulls BULK_NUMBER_OF_REACTIONS salty inchis from the database that are
   * in reactions, then cleans them and bins them into output files depending on whether they fail, clean to the same inchi,
   * or get modified.
   *
   * @param outputPrefix The output file prefix where the analysis output will reside in
   */
  public void examineReactionChemicals(String outputPrefix) {
    // Grab a large sample of chemicals that are in reactions. We do not read anything to the WRITE_DB below.
    List<String> saltyChemicals = getSaltyReactions(new NoSQLAPI(DESALTER_READ_DB, WRITE_DB), BULK_NUMBER_OF_REACTIONS);
    LOGGER.debug(String.format("Total number of reactions being processed: %d", saltyChemicals.size()));
    generateAnalysisOfDesaltingSaltyChemicals(saltyChemicals, outputPrefix);
  }

  /**
   * This function extracts a set number of reactions containing salts from the DB
   *
   * @param api               The api to extract data from.
   * @param numberOfChemicals The total number of reactions being examined.
   * @return A list of reaction strings
   */
  private List<String> getSaltyReactions(NoSQLAPI api, Integer numberOfChemicals) {
    Set<String> saltyChemicals = new HashSet<>();
    Iterator<Reaction> allReactions = api.readRxnsFromInKnowledgeGraph();

    Set<Long> previouslyEncounteredChemicalIDs = new HashSet<>();
    List<String> outputSaltyChemicals = new ArrayList<>();

    allReactionsLoop:
    while (allReactions.hasNext()) {
      Reaction reaction = allReactions.next();
      Set<Long> reactionParticipants = Stream.concat(Arrays.asList(reaction.getSubstrates()).stream(),
          Arrays.asList(reaction.getProducts()).stream()).collect(Collectors.toSet());

      for (Long reactionId : reactionParticipants) {

        if (saltyChemicals.size() >= numberOfChemicals) {
          break allReactionsLoop;
        }

        if (previouslyEncounteredChemicalIDs.contains(reactionId)) {
          continue;
        }

        previouslyEncounteredChemicalIDs.add(reactionId);
        Chemical chemical = api.readChemicalFromInKnowledgeGraph(reactionId);
        String inchi = chemical.getInChI();

        if (inchi.contains(FAKE)) {
          continue;
        }

        try {
          desalter.InchiToSmiles(inchi);
        } catch (Exception err) {
          LOGGER.error(String.format("Exception caught while trying to convert inchi to smile: %s\n", err.getMessage()));
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
   *
   * @param salties      A list of reactions
   * @param outputPrefix The output prefix for the generated files
   */
  private void generateAnalysisOfDesaltingSaltyChemicals(List<String> salties, String outputPrefix) {
    try (
        BufferedWriter substrateModifiedFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_modified.txt")));
        BufferedWriter substrateUnchangedFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_unchanged.txt")));
        BufferedWriter substrateErrorsFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_errors.txt")));
        BufferedWriter substrateComplexFileWriter =
            new BufferedWriter(new FileWriter(new File(outputPrefix + "_complex.txt")))
    ) {
      for (int i = 0; i < salties.size(); i++) {
        String salty = salties.get(i);
        String saltySmile = null;
        try {
          saltySmile = desalter.InchiToSmiles(salty);
        } catch (Exception err) {
          LOGGER.error(String.format("Exception caught while desalting inchi: %s with error message: %s\n", salty,
              err.getMessage()));
          substrateErrorsFileWriter.write("InchiToSmiles1\t" + salty);
          substrateErrorsFileWriter.newLine();
          continue;
        }

        Set<String> results = null;
        try {
          int j = 0;
          //desalter.desaltMolecule(salty);
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
            cleanSmile = desalter.InchiToSmiles(cleaned);
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
        } else {
          //Otherwise there were multiple organic products
          substrateComplexFileWriter.append(">>\t" + salty + "\t" + saltySmile);
          substrateComplexFileWriter.newLine();
          for (String inchi : results) {
            substrateComplexFileWriter.append("\t" + inchi + "\t" + desalter.InchiToSmiles(inchi));
            substrateComplexFileWriter.newLine();
          }
        }
      }
    } catch (IOException exception) {
      LOGGER.error(String.format("IOException: %s", exception.getMessage()));
    }
  }
}
