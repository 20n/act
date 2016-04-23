package com.act.biointerpretation.step4_mechanisminspection;

import act.server.NoSQLAPI;
import act.shared.Reaction;
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
 *
 * Created by jca20n on 1/11/16.
 */
public class MechanisticValidator {
  private static final String WRITE_DB = "mambo";
  private static final String READ_DB = "jarvis";
  private static final Logger LOGGER = LogManager.getLogger(MechanisticValidator.class);
  private static final Integer DEFAULT_LOWEST_SCORE = -1;
  private static final String DB_PERFECT_CLASSIFICATION = "perfect";
  private NoSQLAPI api;
  private ErosCorpus erosCorpus;
  private Map<Eros, Reactor> reactors;
  private BlacklistedInchisCorpus blacklistedInchisCorpus;

  // See https://docs.chemaxon.com/display/FF/InChi+and+InChiKey+export+options for MolExporter options.
  public static final String MOL_EXPORTER_INCHI_OPTIONS = new StringBuilder("inchi:").
      append("SNon").append(','). // Exclude stereo information.
      append("AuxNone").append(','). // Don't write the AuxInfo block--it just gets in the way.
      append("Woff").append(','). // Disable warnings.  We'll catch any exceptions this produces, but don't care about warnings.
      append("DoNotAddH"). // Overrides inchi_Atom::num_iso_H[0] == -1.
      toString();

  public static void main(String[] args) throws IOException, LicenseProcessingException, ReactionException {
    NoSQLAPI.dropDB(WRITE_DB);
    MechanisticValidator mechanisticValidator = new MechanisticValidator(new NoSQLAPI(READ_DB, WRITE_DB));
    mechanisticValidator.loadCorpus();
    mechanisticValidator.initReactors();
    mechanisticValidator.run();
  }

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
      Reaction rxn = iterator.next();
      Set<JSONObject> oldProteinData = new HashSet<>(rxn.getProteinData());
      int oldUUID = rxn.getUUID();

      TreeMap<Integer, List<Eros>> scoreToListOfRos = findBestRosThatCorrectlyComputeTheReaction(rxn);

      // Write the reaction to the write DB
      Reaction templateReaction = new Reaction(
          -1, // Assume the id will be set when the reaction is written to the DB.
          rxn.getSubstrates(),
          rxn.getProducts(),
          rxn.getSubstrateCofactors(),
          rxn.getProductCofactors(),
          rxn.getCoenzymes(),
          rxn.getECNum(),
          rxn.getConversionDirection(),
          rxn.getPathwayStepDirection(),
          rxn.getReactionName(),
          rxn.getRxnDetailType()
      );

      reactionMerger.migrateChemicals(rxn, templateReaction);

      int newId = api.writeToOutKnowlegeGraph(rxn);

      rxn.removeAllProteinData();

      for (JSONObject protein : oldProteinData) {
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        protein.put("source_reaction_id", oldUUID);
        JSONObject newProteinData = reactionMerger.migrateProteinData(protein, Long.valueOf(newId), rxn);
        rxn.addProteinData(newProteinData);
      }

      if (scoreToListOfRos != null && scoreToListOfRos.size() > 0) {
        JSONObject matchingEros = new JSONObject();
        for (Map.Entry<Integer, List<Eros>> entry : scoreToListOfRos.entrySet()) {
          for (Eros e : entry.getValue()) {
            matchingEros.put(e.getId().toString(), entry.getKey().toString());
          }
        }

        rxn.setMechanisticValidatorResult(matchingEros);
      }

      // Update the reaction in the DB with the newly migrated protein data.
      api.getWriteDB().updateActReaction(rxn, newId);
    }

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
  }

  private TreeMap<Integer, List<Eros>> findBestRosThatCorrectlyComputeTheReaction(Reaction rxn) throws IOException {
    List<Molecule> substrateMolecules = new ArrayList<>();
    for (Long id : rxn.getSubstrates()) {
      String inchi = api.readChemicalFromInKnowledgeGraph(id).getInChI();
      if (inchi.contains("FAKE")) {
        return null;
      }

      try {
        substrateMolecules.add(MolImporter.importMol(blacklistedInchisCorpus.renameInchiIfFoundInBlacklist(inchi)));
      } catch (chemaxon.formats.MolFormatException e) {
        LOGGER.error(String.format("Error occurred while trying to import inchi %s with error message %s", inchi, e.getMessage()));
        return null;
      }
    }

    Set<String> expectedProducts = new HashSet<>();
    for (Long id: rxn.getProducts()) {
      String inchi = api.readChemicalFromInKnowledgeGraph(id).getInChI();
      if (inchi.contains("FAKE")) {
        return null;
      }

      String transformedInchi = removeChiralityFromChemical(api.readChemicalFromInKnowledgeGraph(id).getInChI());
      if (transformedInchi == null) {
        return null;
      }
      expectedProducts.add(transformedInchi);
    }

    TreeMap<Integer, List<Eros>> scoreToListOfRos = new TreeMap<>(Collections.reverseOrder());
    for (Eros ero : reactors.keySet()) {
      Integer score = scoreReactionBasedOnRO(ero, substrateMolecules, expectedProducts);
      if (score > DEFAULT_LOWEST_SCORE) {
        List<Eros> vals = scoreToListOfRos.get(score);
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
    for (Eros ro : erosCorpus.getRos()) {
      try {
        Reactor reactor = new Reactor();
        reactor.setReactionString(ro.getRo());
        reactors.put(ro, reactor);
      } catch (java.lang.NoSuchFieldError e) {
        // TODO: Investigate why so many ROs are failing at this point.
        LOGGER.error(String.format("Ros is throwing a no such field error. The ro is: %s", ro.getRo()));
      }
    }
  }

  private String removeChiralityFromChemical(String inchi) throws IOException {
    try {
      Molecule importedMol = MolImporter.importMol(blacklistedInchisCorpus.renameInchiIfFoundInBlacklist(inchi));
      return MolExporter.exportToFormat(importedMol, MOL_EXPORTER_INCHI_OPTIONS);
    } catch (chemaxon.formats.MolFormatException e) {
      LOGGER.error(String.format("Error occur while trying to import molecule from inchi %s. The error is %s", inchi, e.getMessage()));
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
      LOGGER.error(String.format("Error while trying to project substrates and RO. The detailed error message is: %s", e.getMessage()));
      return null;
    }

    if (products == null || products.length == 0) {
      LOGGER.error(String.format("No products were found through the projection"));
      return null;
    }

    Set<String> result = new HashSet<>();
    for (Molecule product : products) {
      String inchi = MolExporter.exportToFormat(product, MOL_EXPORTER_INCHI_OPTIONS);
      result.add(inchi);
    }

    return result;
  }

  public Integer scoreReactionBasedOnRO(Eros ero, List<Molecule> substrates, Set<String> expectedProductInchis) {

    Set<String> productInchis;

    try {
      Reactor reactor = new Reactor();
      reactor.setReactionString(ero.getRo());
      productInchis = projectRoOntoMoleculesAndReturnInchis(reactor, substrates);
    } catch (IOException e) {
      LOGGER.debug(String.format("Encountered IOException when projecting reactor onto substrates. The error message" +
          "is: %s", e.getMessage()));
      return DEFAULT_LOWEST_SCORE;
    } catch (ReactionException e) {
      LOGGER.debug(String.format("Encountered ReactionException when projecting reactor onto substrates. The error message" +
          "is: %s", e.getMessage()));
      return DEFAULT_LOWEST_SCORE;
    }

    if (productInchis == null) {
      LOGGER.debug(String.format("No products were generated from the projection"));
      return DEFAULT_LOWEST_SCORE;
    }

    for (String product : productInchis) {

      // If one of the products matches the expected product inchis set, we are confident that the reaction can be
      // explained by the RO.
      if (expectedProductInchis.contains(product)) {
        if(ero.getCategory().equals(DB_PERFECT_CLASSIFICATION)) {
          return 4;
        }

        if(ero.getManual_validation()) {
          return 3;
        }

        if(ero.getManual_validation() == null) {
          return 2;
        }

        if(!ero.getManual_validation()) {
          return 0;
        }

        else {
          return 1;
        }
      }
    }

    return DEFAULT_LOWEST_SCORE;
  }
}
