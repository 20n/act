package com.act.biointerpretation.step4_mechanisminspection;

import act.server.NoSQLAPI;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
    ReactionMerger reactionMerger = new ReactionMerger(api);

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

    while (iterator.hasNext()) {
      // Get reaction from the read db
      Reaction oldRxn = iterator.next();
      Long oldUUID = Long.valueOf(oldRxn.getUUID());

      // I don't like modifying reaction objects in place, so we'll create a fresh one and write it to the new DB.
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

      TreeMap<Integer, List<Ero>> scoreToListOfRos = findBestRosThatCorrectlyComputeTheReaction(oldRxn);
      reactionMerger.migrateChemicals(newRxn, oldRxn);

      int newId = api.writeToOutKnowlegeGraph(newRxn);
      Long newIdL = Long.valueOf(newId);

      for (JSONObject protein : oldRxn.getProteinData()) {
        JSONObject newProteinData = reactionMerger.migrateProteinData(protein, newIdL, oldRxn);
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        newProteinData.put("source_reaction_id", oldUUID);
        newRxn.addProteinData(newProteinData);
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
      // Update the reaction in the DB with the newly migrated protein data.
      api.getWriteDB().updateActReaction(newRxn, newId);
    }

    long endTime = new Date().getTime();
    LOGGER.debug("Time in seconds: %d", (endTime - startTime) / 1000);
  }

  private TreeMap<Integer, List<Ero>> findBestRosThatCorrectlyComputeTheReaction(Reaction rxn) throws IOException {
    List<Molecule> substrateMolecules = new ArrayList<>();

    for (Long id : rxn.getSubstrates()) {
      String inchi = api.readChemicalFromInKnowledgeGraph(id).getInChI();
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
        coefficient = 1;
      }

      for (int i = 0; i < coefficient; i++) {
        substrateMolecules.add(mol);
      }
    }

    Set<String> expectedProducts = new HashSet<>();

    for (Long id: rxn.getProducts()) {
      String inchi = api.readChemicalFromInKnowledgeGraph(id).getInChI();
      if (inchi.contains("FAKE")) {
        LOGGER.debug("The inchi is a FAKE, so just ignore the chemical.");
        continue;
      }

      String transformedInchi = removeChiralityFromChemical(api.readChemicalFromInKnowledgeGraph(id).getInChI());
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
