package com.act.biointerpretation.step4_mechanisminspection;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import act.shared.helpers.P;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.reactionmerging.ReactionMerger;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
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
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The mechanistic validator is used for evaluating whether a particular set of substrates and products represent a
 * valid enzymatic reaction. It takes as input a DB of cofactor processed reactions and tries to match each reaction
 * against a curated set of ROs. Depending on the quality of the match, it scores the RO-Reaction from a 0-5 score
 * scale. The default score is always -1, in which case, the results of the mechanistic validation run is not written
 * to the write DB. Else, the matched ROs will be packaged and written into the reaction in the write DB.
 */
public class MechanisticValidator {
  private static final Logger LOGGER = LogManager.getFormatterLogger(MechanisticValidator.class);

  private static final String DB_PERFECT_CLASSIFICATION = "perfect";
  private NoSQLAPI api;
  private ErosCorpus erosCorpus;
  private Map<Ero, Reactor> reactors;
  private BlacklistedInchisCorpus blacklistedInchisCorpus;
  private Map<Long, Long> oldChemIdToNewChemId = new HashMap<>();
  private Map<Long, String> newChemIdToInchi = new HashMap<>();

  private Map<Pair<Map<Long, Integer>, Map<Long, Integer>>, Pair<Long, TreeMap<Integer, List<Ero>>>> cachedEroResults =
      new HashMap<>();

  private enum ROScore {
    PERFECT_SCORE(4),
    MANUALLY_VALIDATED_SCORE(3),
    MANUALLY_NOT_VERIFIED_SCORE(2),
    MANUALLY_INVALIDATED_SCORE(0),
    DEFAULT_MATCH_SCORE(1),
    DEFAULT_UNMATCH_SCORE(-1);

    private int score;

    ROScore(int score) {
      this.score = score;
    }

    public int getScore() {
      return score;
    }
  }

  // See https://docs.chemaxon.com/display/FF/InChi+and+InChiKey+export+options for MolExporter options.
  public static final String MOL_EXPORTER_INCHI_OPTIONS_FOR_INCHI_COMPARISON = new StringBuilder("inchi:").
      append("SNon").append(','). // Exclude stereo information.
      append("AuxNone").append(','). // Don't write the AuxInfo block--it just gets in the way.
      append("Woff").append(','). // Disable warnings.  We'll catch any exceptions this produces, but don't care about warnings.
      append("DoNotAddH"). // Don't add H according to usual valences: all H are explicit
      toString();

  public MechanisticValidator(NoSQLAPI api) {
    this.api = api;
  }

  public void loadCorpus() throws IOException {
    erosCorpus = new ErosCorpus();
    erosCorpus.loadCorpus();

    blacklistedInchisCorpus = new BlacklistedInchisCorpus();
    blacklistedInchisCorpus.loadCorpus();
  }

  public void run() throws IOException {
    LOGGER.debug("Starting Mechanistic Validator");
    long startTime = new Date().getTime();

    migrateChemicals();
    runMechanisticValidatorOnAllReactions();

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  private void migrateChemicals() {
    Iterator<Chemical> chemicals = api.readChemsFromInKnowledgeGraph();
    while (chemicals.hasNext()) {
      Chemical chem = chemicals.next();
      Long oldId = chem.getUuid();
      Long newId = api.writeToOutKnowlegeGraph(chem);
      // Cache the old-to-new id mapping so we don't have to hit the DB for each chemical.
      oldChemIdToNewChemId.put(oldId, newId);
      // Cache the id to InChI mapping so we don't have to re-load the chem documents just to get the InChI.
      newChemIdToInchi.put(newId, chem.getInChI());
    }
  }

  private void runMechanisticValidatorOnAllReactions() throws IOException {
    ReactionMerger reactionMerger = new ReactionMerger(api);

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

    while (iterator.hasNext()) {
      // Get reaction from the read db
      Reaction oldRxn = iterator.next();
      Long oldId = Long.valueOf(oldRxn.getUUID());

      Reaction newRxn = new Reaction(
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
      newRxn.setDataSource(oldRxn.getDataSource());
      for (P<Reaction.RefDataSource, String> ref : oldRxn.getReferences()) {
        newRxn.addReference(ref.fst(), ref.snd());
      }

      int newId = api.writeToOutKnowlegeGraph(newRxn);
      Long newIdL = Long.valueOf(newId);
      migrateReactionChemicals(newRxn, oldRxn);

      for (JSONObject protein : oldRxn.getProteinData()) {
        JSONObject newProteinData = reactionMerger.migrateProteinData(protein, newIdL, newRxn);
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        protein.put("source_reaction_id", oldId);
        newRxn.addProteinData(newProteinData);
      }

      // Apply the EROs and save the results in the reaction object.
      TreeMap<Integer, List<Ero>> scoreToListOfRos;
      try {
        scoreToListOfRos = findBestRosThatCorrectlyComputeTheReaction(newRxn);
      } catch (IOException e) {
        // Log some information about the culprit when validation fails.
        LOGGER.error("Caught IOException when applying ROs to rxn %d (new id %d): %s", oldId, newId, e.getMessage());
        throw e;
      }

      if (scoreToListOfRos != null && scoreToListOfRos.size() > 0) {
        JSONObject matchingEros = new JSONObject();
        for (Map.Entry<Integer, List<Ero>> entry : scoreToListOfRos.entrySet()) {
          for (Ero e : entry.getValue()) {
            matchingEros.put(e.getId().toString(), entry.getKey().toString());
          }
        }
        newRxn.setMechanisticValidatorResult(matchingEros);
      }

      // Update the reaction in the DB with the newly migrated protein data.
      api.getWriteDB().updateActReaction(newRxn, newId);
    }
  }

  private void migrateReactionChemicals(Reaction newRxn, Reaction oldRxn) {
    Long[] oldSubstrates = oldRxn.getSubstrates();
    Long[] oldProducts = oldRxn.getProducts();
    // TODO: also migrate cofactors.
    List<Long> migratedSubstrates = new ArrayList<>(Arrays.asList(oldSubstrates).stream().map(oldChemIdToNewChemId::get).
        filter(x -> x != null).collect(Collectors.toList()));
    List<Long> migratedProducts = new ArrayList<>(Arrays.asList(oldProducts).stream().map(oldChemIdToNewChemId::get).
        filter(x -> x != null).collect(Collectors.toList()));

    // Substrate/product counts must be identical before and after migration.
    if (migratedSubstrates.size() != oldSubstrates.length ||
        migratedProducts.size() != oldProducts.length) {
      throw new RuntimeException(String.format(
          "Pre/post substrate/product migration lengths don't match for source reaction %d: %d -> %d, %d -> %d",
          oldRxn.getUUID(), oldSubstrates.length, migratedSubstrates.size(), oldProducts.length, migratedProducts.size()
      ));
    }

    newRxn.setSubstrates(migratedSubstrates.toArray(new Long[migratedSubstrates.size()]));
    newRxn.setProducts(migratedProducts.toArray(new Long[migratedProducts.size()]));

    // Copy over substrate/product coefficients one at a time based on index, which should be consistent.
    for (int i = 0; i < migratedSubstrates.size(); i++) {
      newRxn.setSubstrateCoefficient(migratedSubstrates.get(i), oldRxn.getSubstrateCoefficient(oldSubstrates[i]));
    }

    for (int i = 0; i < migratedProducts.size(); i++) {
      newRxn.setProductCoefficient(migratedProducts.get(i), oldRxn.getProductCoefficient(oldProducts[i]));
    }

    Long[] oldSubstrateCofactors = oldRxn.getSubstrateCofactors();
    Long[] oldProductCofactors = oldRxn.getProductCofactors();

    List<Long> migratedSubstrateCofactors =
        Arrays.asList(oldSubstrateCofactors).stream().map(oldChemIdToNewChemId::get).
            filter(x -> x != null).collect(Collectors.toList());
    List<Long> migratedProductCofactors =
        Arrays.asList(oldProductCofactors).stream().map(oldChemIdToNewChemId::get).
            filter(x -> x != null).collect(Collectors.toList());

    if (migratedSubstrateCofactors.size() != oldSubstrateCofactors.length ||
        migratedProductCofactors.size() != oldProductCofactors.length) {
      throw new RuntimeException(String.format(
          "Pre/post sub/prod cofactor migration lengths don't match for source reaction %d: %d -> %d, %d -> %d",
          oldRxn.getUUID(), oldSubstrateCofactors.length, migratedSubstrateCofactors.size(),
          oldProductCofactors.length, migratedProductCofactors.size()
      ));
    }

    newRxn.setSubstrateCofactors(migratedSubstrateCofactors.toArray(new Long[migratedSubstrateCofactors.size()]));
    newRxn.setProductCofactors(migratedProductCofactors.toArray(new Long[migratedProductCofactors.size()]));
  }

  private TreeMap<Integer, List<Ero>> findBestRosThatCorrectlyComputeTheReaction(Reaction rxn) throws IOException {
    /* Look up any cached results and return immediately if they're available.
     * Note: this only works while EROs ignore cofactors.  If cofactors need to be involved, we should just remove this.
     */
    Map<Long, Integer> substrateToCoefficientMap = new HashMap<>();
    Map<Long, Integer> productToCoefficientMap = new HashMap<>();

    for (Long id : rxn.getSubstrates()) {
      substrateToCoefficientMap.put(id, rxn.getSubstrateCoefficient(id));
    }
    for (Long id : rxn.getProducts()) {
      productToCoefficientMap.put(id, rxn.getSubstrateCoefficient(id));
    }

    {
      Pair<Long, TreeMap<Integer, List<Ero>>> cachedResults =
          cachedEroResults.get(Pair.of(substrateToCoefficientMap, productToCoefficientMap));
      if (cachedResults != null) {
        LOGGER.debug("Got hit on cached ERO results: %d == %d", rxn.getUUID(), cachedResults.getLeft());
        return cachedResults.getRight();
      }
    }

    List<Molecule> substrateMolecules = new ArrayList<>();
    for (Long id : rxn.getSubstrates()) {
      String inchi = newChemIdToInchi.get(id);
      if (inchi == null){
        String msg = String.format("Missing inchi for new chem id %d in cache", id);
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }

      if (inchi.contains("FAKE")) {
        LOGGER.debug("The inchi is a FAKE, so just ignore the chemical.");
        continue;
      }

      Molecule mol;
      try {
        mol = MolImporter.importMol(blacklistedInchisCorpus.renameInchiIfFoundInBlacklist(inchi));
      } catch (chemaxon.formats.MolFormatException e) {
        LOGGER.error("Error occurred while trying to import inchi %s: %s", inchi, e.getMessage());
        return null;
      }

      /* Some ROs depend on multiple copies of a given molecule (like #165), and the Reactor won't run without all of
       * those molecules available.  Duplicate a molecule in the substrates list based on its coefficient in the
       * reaction. */
      Integer coefficient = rxn.getSubstrateCoefficient(id);
      if (coefficient == null) {
        // Default to just one if we don't have a clear coefficient to use.
        LOGGER.warn("Converting coefficient null -> 1 for rxn %d/chem %d", rxn.getUUID(), id);
        coefficient = 1;
      }

      for (int i = 0; i < coefficient; i++) {
        substrateMolecules.add(mol);
      }
    }

    Set<String> expectedProducts = new HashSet<>();

    for (Long id: rxn.getProducts()) {
      String inchi = newChemIdToInchi.get(id);
      if (inchi == null){
        String msg = String.format("Missing inchi for new chem id %d in cache", id);
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }

      if (inchi.contains("FAKE")) {
        LOGGER.debug("The inchi is a FAKE, so just ignore the chemical.");
        continue;
      }

      String transformedInchi = removeChiralityFromChemical(inchi);
      if (transformedInchi == null) {
        return null;
      }
      expectedProducts.add(transformedInchi);
    }

    TreeMap<Integer, List<Ero>> scoreToListOfRos = new TreeMap<>(Collections.reverseOrder());
    for (Ero ero : reactors.keySet()) {
      Integer score = scoreReactionBasedOnRO(ero, substrateMolecules, expectedProducts);
      if (score > ROScore.DEFAULT_UNMATCH_SCORE.getScore()) {
        List<Ero> vals = scoreToListOfRos.get(score);
        if (vals == null) {
          vals = new ArrayList<>();
          scoreToListOfRos.put(score, vals);
        }
        vals.add(ero);
      }
    }

    // Cache results for any future similar reactions.
    cachedEroResults.put(Pair.of(substrateToCoefficientMap, productToCoefficientMap),
        Pair.of(Long.valueOf(rxn.getUUID()), scoreToListOfRos));

    return scoreToListOfRos;
  }

  public void initReactors(File licenseFile) throws IOException, LicenseProcessingException, ReactionException {
    if (licenseFile != null) {
      LicenseManager.setLicenseFile(licenseFile.getAbsolutePath());
    }

    reactors = new HashMap<>(erosCorpus.getRos().size());
    for (Ero ro : erosCorpus.getRos()) {
      try {
        Reactor reactor = new Reactor();
        reactor.setReactionString(ro.getRo());
        reactors.put(ro, reactor);
      } catch (java.lang.NoSuchFieldError e) {
        // TODO: Investigate why so many ROs are failing at this point.
        LOGGER.error("Ros is throwing a no such field error: %s", ro.getRo());
      }
    }
  }

  private String removeChiralityFromChemical(String inchi) throws IOException {
    try {
      Molecule importedMol = MolImporter.importMol(blacklistedInchisCorpus.renameInchiIfFoundInBlacklist(inchi));
      return MolExporter.exportToFormat(importedMol, MOL_EXPORTER_INCHI_OPTIONS_FOR_INCHI_COMPARISON);
    } catch (chemaxon.formats.MolFormatException e) {
      LOGGER.error("Error occur while trying to import molecule from inchi %s: %s", inchi, e.getMessage());
      return null;
    }
  }

  public void initReactors() throws IOException, LicenseProcessingException, ReactionException {
    initReactors(null);
  }

  public Set<String> projectRoOntoMoleculesAndReturnInchis(Reactor reactor, List<Molecule> substrates)
      throws IOException, ReactionException {

    Molecule[] products;
    try {
      products = ReactionProjector.projectRoOnMolecules(substrates.toArray(new Molecule[substrates.size()]), reactor);
    } catch (java.lang.NoSuchFieldError e) {
      LOGGER.error("Error while trying to project substrates and RO: %s", e.getMessage());
      return null;
    }

    if (products == null || products.length == 0) {
      LOGGER.debug("No products were found through the projection");
      return null;
    }

    Set<String> result = new HashSet<>();
    for (Molecule product : products) {
      String inchi = MolExporter.exportToFormat(product, MOL_EXPORTER_INCHI_OPTIONS_FOR_INCHI_COMPARISON);
      result.add(inchi);
    }

    return result;
  }

  public Integer scoreReactionBasedOnRO(Ero ero, List<Molecule> substrates, Set<String> expectedProductInchis) {

    // Check if the RO can physically transform the given reaction by comparing the substrate counts
    if ((ero.getSubstrate_count() != substrates.size())) {
      return ROScore.DEFAULT_UNMATCH_SCORE.getScore();
    }

    Set<String> productInchis;
    try {
      Reactor reactor = new Reactor();
      reactor.setReactionString(ero.getRo());
      productInchis = projectRoOntoMoleculesAndReturnInchis(reactor, substrates);
    } catch (IOException e) {
      LOGGER.error("Encountered IOException when projecting reactor onto substrates: %s", e.getMessage());
      return ROScore.DEFAULT_UNMATCH_SCORE.getScore();
    } catch (ReactionException e) {
      LOGGER.error("Encountered ReactionException when projecting reactor onto substrates: %s", e.getMessage());
      return ROScore.DEFAULT_UNMATCH_SCORE.getScore();
    }

    if (productInchis == null) {
      LOGGER.debug("No products were generated from the projection");
      return ROScore.DEFAULT_UNMATCH_SCORE.getScore();
    }

    for (String product : productInchis) {
      // If one of the products matches the expected product inchis set, we are confident that the reaction can be
      // explained by the RO.
      if (expectedProductInchis.contains(product)) {
        if (ero.getCategory().equals(DB_PERFECT_CLASSIFICATION)) {
          return ROScore.PERFECT_SCORE.getScore();
        }

        if (ero.getManual_validation()) {
          return ROScore.MANUALLY_VALIDATED_SCORE.getScore();
        }

        if (ero.getManual_validation() == null) {
          return ROScore.MANUALLY_NOT_VERIFIED_SCORE.getScore();
        }

        if (!ero.getManual_validation()) {
          return ROScore.MANUALLY_INVALIDATED_SCORE.getScore();
        }

        else {
          return ROScore.DEFAULT_MATCH_SCORE.getScore();
        }
      }
    }

    return ROScore.DEFAULT_UNMATCH_SCORE.getScore();
  }
}
