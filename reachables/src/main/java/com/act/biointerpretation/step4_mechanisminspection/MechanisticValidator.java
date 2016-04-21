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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * Created by jca20n on 1/11/16.
 */
public class MechanisticValidator {
  private static final String WRITE_DB = "jarvis";
  private static final String READ_DB = "actv01";
  private static final Logger LOGGER = LogManager.getLogger(MechanisticValidator.class);
  private static final Integer DEFAULT_LOWEST_SCORE = -1;
  private NoSQLAPI api;
  private ErosCorpus erosCorpus;
  private Map<Eros, Reactor> reactors;

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
  }

  private String replaceInchiIfNeeded(String inchi) {

    //nad
    if (inchi.equals("InChI=1S/C21H29N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1-4,7-8,10-11,13-16,20-21,29-32H,5-6H2,(H5-,22,23,24,25,33,34,35,36,37)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1")) {
      return "InChI=1S/C21H27N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1-4,7-8,10-11,13-16,20-21,29-32H,5-6H2,(H5-,22,23,24,25,33,34,35,36,37)/p+1/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1";
    }

    //nadp+
    if (inchi.equals("InChI=1S/C21H29N7O17P3/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(44-46(33,34)35)14(30)11(43-21)6-41-48(38,39)45-47(36,37)40-5-10-13(29)15(31)20(42-10)27-3-1-2-9(4-27)18(23)32/h1-4,7-8,10-11,13-16,20-21,29-31H,5-6H2,(H7-,22,23,24,25,32,33,34,35,36,37,38,39)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1")) {
      return "InChI=1S/C21H28N7O17P3/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(44-46(33,34)35)14(30)11(43-21)6-41-48(38,39)45-47(36,37)40-5-10-13(29)15(31)20(42-10)27-3-1-2-9(4-27)18(23)32/h1-4,7-8,10-11,13-16,20-21,29-31H,5-6H2,(H7-,22,23,24,25,32,33,34,35,36,37,38,39)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1";
    }

    // nad+
    if (inchi.equals("InChI=1S/C21H28N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1-4,7-8,10-11,13-16,20-21,29-32H,5-6H2,(H5-,22,23,24,25,33,34,35,36,37)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1")) {
      return "InChI=1S/C21H27N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1-4,7-8,10-11,13-16,20-21,29-32H,5-6H2,(H5-,22,23,24,25,33,34,35,36,37)/p+1/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1";
    }

    return inchi;
  }

  public void run() throws IOException {
    LOGGER.debug("Starting Mechanistic Validator");
    long startTime = new Date().getTime();
    ReactionMerger reactionMerger = new ReactionMerger();

    //Scan through all Reactions and process each
    Iterator<Reaction> iterator = api.readRxnsFromInKnowledgeGraph();

    loop: while (iterator.hasNext()) {
      // Get reaction from the read db
      Reaction rxn = iterator.next();
      List<Molecule> substrateMolecules = new ArrayList<>();
      Set<JSONObject> oldProteinData = new HashSet<>(rxn.getProteinData());
      int oldUUID = rxn.getUUID();

      if (rxn.getECNum().equals("1.1.1.1") && rxn.getReactionName().equals(" {Equus caballus} (R)-2-pentanol + NAD+ -?> 2-pentanone + NADH + H+")) {
        int j = 1;
      }

      for (Long id : rxn.getSubstrates()) {
        String inchi = api.readChemicalFromInKnowledgeGraph(id).getInChI();
        if (inchi.contains("FAKE")) {
          continue loop;
        }

        try {
          substrateMolecules.add(MolImporter.importMol(replaceInchiIfNeeded(inchi)));
        } catch (chemaxon.formats.MolFormatException e) {
          LOGGER.error(String.format("Error occurred while trying to import inchi %s with error message %s", inchi, e.getMessage()));
          continue loop;
        }
      }

      Set<String> expectedProducts = new HashSet<>();
      for (Long id: rxn.getProducts()) {
        String inchi = api.readChemicalFromInKnowledgeGraph(id).getInChI();
        if (inchi.contains("FAKE")) {
          continue loop;
        }

        String transformedInchi = removeChiralityFromChemical(api.readChemicalFromInKnowledgeGraph(id).getInChI());
        if (transformedInchi == null) {
          continue loop;
        }
        expectedProducts.add(transformedInchi);
      }

      Integer bestScore = DEFAULT_LOWEST_SCORE;
      Eros bestEro = null;
      for (Eros ero : reactors.keySet()) {
        Integer score = scoreReactionBasedOnRO(ero, substrateMolecules, expectedProducts);
        if (score > bestScore) {
          bestScore = score;
          bestEro = ero;
        }

        if (score >= 4) {
          break;
        }
      }

      if (bestEro != null) {
        int j = 10;
      }

      // Write the reaction to the write DB
      reactionMerger.migrateChemicals(rxn, rxn);

      int newId = api.writeToOutKnowlegeGraph(rxn);

      rxn.removeAllProteinData();

      for (JSONObject protein : oldProteinData) {
        // Save the source reaction ID for debugging/verification purposes.  TODO: is adding a field like this okay?
        protein.put("source_reaction_id", oldUUID);
        JSONObject newProteinData = reactionMerger.migrateProteinData(protein, Long.valueOf(newId), rxn);
        rxn.addProteinData(newProteinData);
      }

      // Update the reaction in the DB with the newly migrated protein data.
      api.getWriteDB().updateActReaction(rxn, newId);
    }

    long endTime = new Date().getTime();
    LOGGER.debug(String.format("Time in seconds: %d", (endTime - startTime) / 1000));
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
      Molecule importedMol = MolImporter.importMol(replaceInchiIfNeeded(inchi));
      return MolExporter.exportToFormat(importedMol, "inchi");
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
      String inchi = MolExporter.exportToFormat(product, "inchi");
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

    if (productInchis.size() != expectedProductInchis.size()) {
      LOGGER.debug(String.format("The projected products is not the same size as the actual products. The size of" +
          "project products is %d and the size of the actual products is %d", productInchis.size(), expectedProductInchis.size()));
      return DEFAULT_LOWEST_SCORE;
    }

    for (String product : productInchis) {
      if (!expectedProductInchis.contains(product)) {
        LOGGER.debug(String.format("Projected product %s is not present in the products", product));
        return DEFAULT_LOWEST_SCORE;
      }
    }

    // At this point, the projected and actual products match.
    if(ero.getCategory().equals("perfect")) {
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
