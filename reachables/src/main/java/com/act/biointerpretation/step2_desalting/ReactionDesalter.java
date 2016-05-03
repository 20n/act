package com.act.biointerpretation.step2_desalting;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import act.shared.helpers.P;
import com.act.biointerpretation.Utils.ReactionComponent;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.ReactionException;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import static com.act.biointerpretation.Utils.ReactionComponent.PRODUCT;
import static com.act.biointerpretation.Utils.ReactionComponent.SUBSTRATE;

/**
 * ReactionDesalter itself does the processing of the database using an instance of Desalter.
 * This class creates Synapse from Dr. Know.  Synapse is the database in which the chemicals
 * have been inspected for containing multiple species or ionized forms, and corrected.
 *
 * Created by jca20n on 10/22/15.
 */
public class ReactionDesalter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ReactionDesalter.class);

  private static final Integer BULK_NUMBER_OF_REACTIONS = 10000;
  private static final String FAKE = "FAKE";
  private static final String WRITE_DB = "synapse";
  private static final String READ_DB = "actv01";
  private static final String DESALTER_READ_DB = "lucille";

  public static final String OPTION_OUTPUT_PREFIX = "o";
  public static final String OPTION_INCHI_INPUT_LIST = "i";
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
    add(Option.builder(OPTION_INCHI_INPUT_LIST)
        .argName("inchi list file")
        .desc("A file containing a list of InChIs to desalt")
        .hasArg()
        .longOpt("input-inchis")
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

  private NoSQLAPI api;
  private Map<Long, List<Long>> oldChemicalIdToNewChemicalIds;
  private Map<String, Long> inchiToNewId;
  private Desalter desalter;
  private int desalterFailuresCounter = 0;

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

    if (cl.hasOption(OPTION_INCHI_INPUT_LIST) && !cl.hasOption(OPTION_OUTPUT_PREFIX)) {
      System.err.format("Input argument %s requires output argument %s\n",
          OPTION_INCHI_INPUT_LIST, OPTION_OUTPUT_PREFIX);
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption(OPTION_OUTPUT_PREFIX)) {
      ReactionDesalter runner = new ReactionDesalter();
      String outAnalysis = cl.getOptionValue(OPTION_OUTPUT_PREFIX);
      if (cl.hasOption(OPTION_INCHI_INPUT_LIST)) {
        File inputFile = new File(cl.getOptionValue(OPTION_INCHI_INPUT_LIST));
        if (!inputFile.exists()) {
          System.err.format("Cannot find input file at %s\n", inputFile.getAbsolutePath());
          HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
          System.exit(1);
        }
        runner.exampleChemicalsList(outAnalysis, inputFile);

      } else {
        runner.examineReactionChemicals(outAnalysis);
      }
    } else {
      // Delete all records in the WRITE_DB
      NoSQLAPI.dropDB(WRITE_DB);

      ReactionDesalter runner = new ReactionDesalter(new NoSQLAPI(READ_DB, WRITE_DB), new Desalter());
      runner.run();
    }
  }

  public ReactionDesalter() {
    oldChemicalIdToNewChemicalIds = new HashMap<>();
    inchiToNewId = new HashMap<>();
    desalter = new Desalter();
  }

  public ReactionDesalter(NoSQLAPI inputApi, Desalter inputDesalter) {
    api = inputApi;
    oldChemicalIdToNewChemicalIds = new HashMap<>();
    inchiToNewId = new HashMap<>();
    desalter = inputDesalter;
  }

  /**
   * This function reads the products and reactions from the db, desalts them and writes it back.
   */
  public void run() throws IOException, LicenseProcessingException, ReactionException {
    LOGGER.debug("Starting Reaction Desalter");
    long startTime = new Date().getTime();

    // Don't forget to initialize the RO corpus!
    desalter.initReactors();

    desaltAllChemicals();
    desaltAllReactions();

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  public void desaltAllChemicals() throws IOException, LicenseProcessingException, ReactionException {
    Iterator<Chemical> chemicals = api.readChemsFromInKnowledgeGraph();
    while (chemicals.hasNext()) {
      Chemical chem = chemicals.next();
      desaltChemical(chem); // Ignore results, as the cached mapping will be used for reaction desalting.
    }
    LOGGER.info("Encountered %d failures while desalting all molecules", desalterFailuresCounter);
  }

  public void desaltAllReactions() throws IOException, LicenseProcessingException, ReactionException {
    ReactionMerger rxnMerger = new ReactionMerger(api);

    //Scan through all Reactions and process each one.
    Iterator<Reaction> reactionIterator = api.readRxnsFromInKnowledgeGraph();

    while (reactionIterator.hasNext()) {
      Reaction oldRxn = reactionIterator.next();
      Long oldUUID = Long.valueOf(oldRxn.getUUID());

      // I don't like modifying reaction objects in place, so we'll create a fresh one and write it to the new DB.
      Reaction desaltedReaction = new Reaction(
          -1, // Assume the id will be set when the reaction is written to the DB.
          new Long[0],
          new Long[0],
          new Long[0],
          new Long[0],
          new Long[0],
          oldRxn.getECNum(),
          oldRxn.getConversionDirection(),
          oldRxn.getPathwayStepDirection(),
          oldRxn.getReactionName(),
          oldRxn.getRxnDetailType()
      );

      // Add the data source and references from the source to the destination
      desaltedReaction.setDataSource(oldRxn.getDataSource());
      for (P<Reaction.RefDataSource, String> ref : oldRxn.getReferences()) {
        desaltedReaction.addReference(ref.fst(), ref.snd());
      }

      migrateReactionSubsProdsWCoeffs(desaltedReaction, oldRxn);

      int newId = api.writeToOutKnowlegeGraph(desaltedReaction);
      Long newIdL = Long.valueOf(newId);

      for (JSONObject protein : oldRxn.getProteinData()) {
        JSONObject newProteinData = rxnMerger.migrateProteinData(protein, newIdL, oldRxn);
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        newProteinData.put("source_reaction_id", oldUUID);
        desaltedReaction.addProteinData(newProteinData);
      }

      // Update the reaction in the DB with the newly migrated protein data.
      api.getWriteDB().updateActReaction(desaltedReaction, newId);
    }

  }

  private void migrateReactionSubsProdsWCoeffs(Reaction newReaction, Reaction oldReaction) {
    {
      Pair<List<Long>, Map<Long, Integer>> newSubstratesAndCoefficients =
          buildIdAndCoefficientMapping(oldReaction, SUBSTRATE);
      newReaction.setSubstrates(newSubstratesAndCoefficients.getLeft().toArray(
          new Long[newSubstratesAndCoefficients.getLeft().size()]));
      newReaction.setAllSubstrateCoefficients(newSubstratesAndCoefficients.getRight());

      List<Long> newSubstrateCofactors = buildIdMapping(oldReaction.getSubstrateCofactors());
      newReaction.setSubstrateCofactors(newSubstrateCofactors.toArray(new Long[newSubstrateCofactors.size()]));
    }

    {
      Pair<List<Long>, Map<Long, Integer>> newProductsAndCoefficients =
          buildIdAndCoefficientMapping(oldReaction, PRODUCT);
      newReaction.setProducts(newProductsAndCoefficients.getLeft().toArray(
          new Long[newProductsAndCoefficients.getLeft().size()]));
      newReaction.setAllProductCoefficients(newProductsAndCoefficients.getRight());

      List<Long> newproductCofactors = buildIdMapping(oldReaction.getProductCofactors());
      newReaction.setProductCofactors(newproductCofactors.toArray(new Long[newproductCofactors.size()]));
    }
  }

  private List<Long> buildIdMapping(Long[] oldChemIds) {
    LinkedHashSet<Long> newIDs = new LinkedHashSet<>(oldChemIds.length);

    for (Long oldChemId : oldChemIds) {
      List<Long> newChemIds = oldChemicalIdToNewChemicalIds.get(oldChemId);
      if (newChemIds == null) {
        throw new RuntimeException(
            String.format("Found old chemical id %d that is not in the old -> new chem id map", oldChemId));
      }

      newIDs.addAll(newChemIds);
    }

    List<Long> results = new ArrayList<>();
    // TODO: does ArrayList's constructor also add all the hashed elements in order?  I know addAll does.
    results.addAll(newIDs);
    return results;
  }

  private Pair<List<Long>, Map<Long, Integer>> buildIdAndCoefficientMapping(Reaction oldRxn, ReactionComponent sOrP) {
    Long[] oldChemIds = sOrP == SUBSTRATE ? oldRxn.getSubstrates() : oldRxn.getProducts();
    List<Long> resultIds = new ArrayList<>(oldChemIds.length);
    Map<Long, Integer> newIdToCoefficientMap = new HashMap<>(oldChemIds.length);

    for (Long oldChemId : oldChemIds) {
      Integer oldCoefficient = sOrP == SUBSTRATE ?
          oldRxn.getSubstrateCoefficient(oldChemId) : oldRxn.getProductCoefficient(oldChemId);

      List<Long> newChemIds = oldChemicalIdToNewChemicalIds.get(oldChemId);
      if (newChemIds == null) {
        throw new RuntimeException(
            String.format("Found old chemical id %d that is not in the old -> new chem id map", oldChemId));
      }

      for (Long newChemId : newChemIds) {
        // Deduplicate new chemicals in the list based on whether we've assigned coefficients for them or not.
        if (newIdToCoefficientMap.containsKey(newChemId)) {
          Integer newCoefficient = newIdToCoefficientMap.get(newChemId);

          // If only one coefficient is null, we have a problem.  Just write null and hope we can figure it out later.
          if ((newCoefficient == null && oldCoefficient != null) ||
              (newCoefficient != null && oldCoefficient == null)) {
            LOGGER.error(String.format("Found null coefficient that needs to be merged with non-null coefficient. " +
                "New chem id: %d, old chem id: %d, coefficient value: %d, old rxn id: %d",
                newChemId, oldChemId, oldCoefficient, oldRxn.getUUID()));
            newIdToCoefficientMap.put(newChemId, null);
          } else if (newCoefficient != null && oldCoefficient != null) {
            // If neither are null, sum them.
            newIdToCoefficientMap.put(newChemId, newCoefficient + oldCoefficient);
          } // Else both are null we don't need to do anything.

          // We don't need to add this new id to the list of substrates/products because it's already there.
        } else {
          resultIds.add(newChemId); // Add the new id to the subs/prods list.
          newIdToCoefficientMap.put(newChemId, oldCoefficient); // Don't care what the value is, just copy it over.
        }
      }
    }
    return Pair.of(resultIds, newIdToCoefficientMap);
  }


  /**
   * This function desalts a single chemical and returns the resulting ids of the modified chemicals that have been
   * written to the destination DB.  The results of the desalting process are also cached for later use in mapping
   * chemicals from the old DB to the new.  If the chemicals cannot be desalted, we just migrate the chemical unaltered.
   *
   * @param chemical A chemical to desalt.
   * @return A list of output ids of desalted chemicals
   */
  private List<Long> desaltChemical(Chemical chemical) throws IOException, ReactionException {
    Long originalId = chemical.getUuid();

    // If the chemical's ID maps to a single pre-seen entry, use its existing old id
    if (oldChemicalIdToNewChemicalIds.containsKey(originalId)) {
      LOGGER.error("desaltChemical was called on a chemical that was already desalted: %d", originalId);
    }

    // Otherwise need to clean the chemical
    String inchi = chemical.getInChI();

    // If it's FAKE, just go with it
    if (inchi.contains(FAKE)) {
      long newId = api.writeToOutKnowlegeGraph(chemical); //Write to the db
      List<Long> singletonId = Collections.unmodifiableList(Collections.singletonList(newId));
      inchiToNewId.put(inchi, newId);
      oldChemicalIdToNewChemicalIds.put(originalId, singletonId);
      return singletonId;
    }

    Set<String> cleanedInchis = null;
    try {
      cleanedInchis = desalter.desaltMolecule(inchi);
    } catch (Exception e) {
      // TODO: probably should handle this error differently, currently just letting pass unaltered
      LOGGER.error(String.format("Exception caught when desalting chemical %d: %s", originalId, e.getMessage()));
      desalterFailuresCounter++;
      long newId = api.writeToOutKnowlegeGraph(chemical); //Write to the db
      List<Long> singletonId = Collections.singletonList(newId);
      inchiToNewId.put(inchi, newId);
      oldChemicalIdToNewChemicalIds.put(originalId, singletonId);
      return Collections.singletonList(newId);
    }

    List<Long> newIds = new ArrayList<>();
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
    }

    // Store and return the cached list of chemical ids that we just created.  Make them immutable for safety's sake.
    List<Long> resultsToCache = Collections.unmodifiableList(newIds);
    oldChemicalIdToNewChemicalIds.put(originalId, resultsToCache);
    return resultsToCache;
  }

  /**
   * This method is used for testing Desalter. It pulls BULK_NUMBER_OF_REACTIONS salty inchis from the database that are
   * in reactions, then cleans them and bins them into output files depending on whether they fail, clean to the same inchi,
   * or get modified.
   *
   * @param outputPrefix The output file prefix where the analysis output will reside in
   */
  public void examineReactionChemicals(String outputPrefix) throws IOException, LicenseProcessingException, ReactionException {
    // Grab a large sample of chemicals that are in reactions. We do not read anything to the WRITE_DB below.
    desalter.initReactors();
    List<String> saltyChemicals = getSaltyReactions(new NoSQLAPI(DESALTER_READ_DB, WRITE_DB), BULK_NUMBER_OF_REACTIONS);
    LOGGER.debug(String.format("Total number of reactions being processed: %d", saltyChemicals.size()));
    generateAnalysisOfDesaltingSaltyChemicals(saltyChemicals, outputPrefix);
  }

  public void exampleChemicalsList(String outputPrefix, File inputFile)
      throws IOException, LicenseProcessingException, ReactionException {
    desalter.initReactors();
    List<String> inchis = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
      String line;
      // Slurp in the list of InChIs from the input file.
      while ((line = reader.readLine()) != null) {
        inchis.add(line.trim());
      }
    }
    generateAnalysisOfDesaltingSaltyChemicals(inchis, outputPrefix);
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
          results = desalter.desaltMolecule(salty);
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
