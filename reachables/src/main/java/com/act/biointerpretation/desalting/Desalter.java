package com.act.biointerpretation.desalting;

import chemaxon.calculations.hydrogenize.Hydrogenize;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.struc.PeriodicSystem;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Desalter tries to remove any ionization or secondary ions from an inchi.
 * To use, create an instance of Desalter then use the clean method
 * to convert one inchi to a desalted version.  One Desalter can be reused.
 *
 * Desalting is the process of standardizing ionized variants of a Molecule.
 * It also splits multi-component reactions into multiple entities.
 * Desalter currently uses Indigo for RO Projection, and this needs to
 * be replaced with ChemAxon.
 *
 * Desalter does all the business logic of inputting an inchi and outputting one
 * or more desalted versions of it (the "clean" method). Though it does a little
 * more than apply ROs, the most essential piece of the code is the ROs, which are
 * stored in a file called com/act/biointerpretation/desalting/desalting_ros.json.
 *
 * That RO file also includes tests. Running Desalter.main() directly will execute
 * these tests. They should all pass except one case in the title called secondary_ammoniums.
 * TODO: We have parked this test case and will get back to it once later during development.
 * {
 *  "input": "InChI=1S/C12H11N.C7H8O3S/c1-3-7-11(8-4-1)13-12-9-5-2-6-10-12;1-6-2-4-7(5-3-6)11(8,9)10/h1-10,13H;2-5H,1H3,(H,8,9,10)",
 *  "expected": "InChI=1S/C12H11N/c1-3-7-11(8-4-1)13-12-9-5-2-6-10-12/h1-10,13H",
 *  "label": "N-Phenylanilinium tosylate"
 * }
 *
 * There is a second file (com/act/biointerpretation/desalting/desalter_constants.txt)
 * that are additional tests which are also executed by this class.
 *
 * The main method also pulls 10000 entries from the database and bin each one
 * based on the result: (caused an error, got modified, didn't get modified, was
 * split into multiple inchis). I've gone through these lists somewhat and for the
 * most part found no errors. There are some edge cases (specifically
 * porphyrins and some rare ions like C[N-]) that are not handled
 * currently. I have also performed this analysis on 10000 entries that
 * are not necessarily in Reactions, and those looked fine too. After
 * running ReactionDesalter on Dr. Know and creating synapse, I examined
 * 1000 reaction entries from synapse. I looked at all the instances of
 * "+" in the SMILES and found no errors. I also inspected the first 200
 * in detail to confirm that no chemicals were lost relative to the text
 * description.
 *
 * TODO: Edge cases that remain to be handled are:  radioactive. heme
 * See Desalter_modified_alldb_checked for examples of errors that remain
 *
 * TODO:  Add as positive tests the 'ok' things in Desalter_modified_alldb_checked
 *
 * TODO: filter InChIs by string components before actually desalting for better efficiency.
 *
 * Note: to use the Chemaxon desalter, you'll need to have a Chemaxon license file installed in your home directory.
 * To do this, run (after connecting to the NAS):
 * $ /shared-data/3rdPartySoftware/Chemaxon/marvinbeans/bin/license [path to a valid license file]
 * This will copy the license to ~/.chemaxon/license.cxl, which the Chemaxon libraries will find automatically when
 * the license manager is invoked.
 */
public class Desalter {
  private static final DesaltingROCorpus DESALTING_CORPUS_ROS = new DesaltingROCorpus();
  private static final Integer MAX_NUMBER_OF_ROS_TRANSFORMATION_ITERATIONS = 1000;
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);
  private static final String INFINITE_LOOP_DETECTED_EXCEPTION_STRING = "The algorithm has encountered a loop for this " +
      "set of transformations %s on this transformed inchi: %s";
  private static final Integer PRODUCT_TO_CHOOSE_INDEX = 0;

  public static class InfiniteLoopDetectedException extends Exception {
    public InfiniteLoopDetectedException(String message) {
      super(message);
    }
  }

  public Desalter() {

  }

  /**
   * This function desalts a given inchi representation of a molecule by first preprocessing the molecule by taking
   * out extra representations like free radicals, only processing organics or a subset of an inorganic molecule
   * and then desalting those component only.
   *
   * @param inchi The inchi representation of the chemical
   * @return A set of desalted compounds within the input chemical
   * @throws Exception
   */
  public Map<String, Integer> desaltMolecule(String inchi)
      throws InfiniteLoopDetectedException, IOException, ReactionException {
    // Resolve the smiles to only those that are 2-carbon units.
    // Do not store the output of MolImporter, as the object will be destroyed during fragmentation.
    List<Molecule> resolved = resolveMixtureOfAtomicComponents(MolImporter.importMol(inchi));

    // Clean each compound
    final List<Molecule> desaltedAndDeionized = new ArrayList<>(resolved.size());
    for (Molecule organicOrBiggestInorganicMass : resolved) {
      Molecule desaltedChemicalFragment = applyROsToMolecule(organicOrBiggestInorganicMass, inchi);
      desaltedAndDeionized.add(desaltedChemicalFragment);
    }

    // Don't combine fragments in order to match Indigo behavior, but preserve the count of each desalted fragment.
    return mols2InchiCounts(desaltedAndDeionized);
  }

  // See https://docs.chemaxon.com/display/FF/InChi+and+InChiKey+export+options for MolExporter options.
  // See also https://www.chemaxon.com/forum/ftopic10424.html for why we want to use SAbs.
  public static final String MOL_EXPORTER_INCHI_OPTIONS = new StringBuilder("inchi:").
      append("SAbs").append(','). // Force absolute stereo to ensure standard InChIs are produced.
      append("AuxNone").append(','). // Don't write the AuxInfo block--it just gets in the way.
      append("Woff"). // Disable warnings.  We'll catch any exceptions this produces, but don't care about warnings.
      toString();

  public static String mol2Inchi(Molecule mol) throws IOException {
    return MolExporter.exportToFormat(mol, MOL_EXPORTER_INCHI_OPTIONS);
  }

  public static List<String> mols2Inchis(List<Molecule> mols) throws IOException {
    List<String> inchis = new ArrayList<>(mols.size());
    for (Molecule mol : mols) {
      inchis.add(mol2Inchi(mol));
    }
    return inchis;
  }

  public static Map<String, Integer> mols2InchiCounts(List<Molecule> mols) throws IOException {
    Map<String, Integer> inchiCounts = new LinkedHashMap<>(mols.size()); // Preserve order as well as count.
    for (Molecule mol : mols) {
      String inchi = mol2Inchi(mol);
      Integer count = inchiCounts.get(inchi);
      inchiCounts.put(inchi, (count == null ? 0 : count) + 1);
    }
    return inchiCounts;
  }

  /**
   * This function desalts an input inchi chemical by running it through a list of curated desalting ROs in a loop
   * and transforms the inchi till it reaches a stable state.
   *
   * @param baseMolecule The molecule on which to project desalting ROs.
   * @param inchi The inchi for the base molecule, used for logging.
   * @return The desalted inchi chemical
   * @throws Exception
   */
  protected Molecule applyROsToMolecule(Molecule baseMolecule, String inchi)
      throws IOException, InfiniteLoopDetectedException, ReactionException {
    Molecule transformedMolecule = baseMolecule;

    /* Add explicit hydrogens before projecting to ensure that the hydrogens in the ROs have something to match against.
     * Note that this didn't seem to actually have much (if any) effect on the InChIs used to validate the desalter
     * (the Reactor seems to work out implicit hydrogens itself), but it shouldn't cause any harm either. */
    Hydrogenize.convertImplicitHToExplicit(baseMolecule);

    // Then try all the ROs
    Set<Molecule> bagOfTransformedMolecules = new LinkedHashSet<>();

    int counter = 0;
    while (counter < MAX_NUMBER_OF_ROS_TRANSFORMATION_ITERATIONS) {
      boolean foundEffectiveReaction = false;

      for (DesaltingRO ro : corpus.getRos()) {
        Molecule product = getFirstPredictedProduct(baseMolecule, ro);

        // If there are no products from this transformation, skip to the next RO.
        if (product == null) {
          continue;
        }

        // Break out as soon as we find an RO that imparts some sort of change.
        if (!product.equals(transformedMolecule)) {
          transformedMolecule = product;
          foundEffectiveReaction = true;
          break;
        }
      }

      // Assume that if all of the transformations returned an equivalent molecule or null then we're done running ROs.
      if (transformedMolecule == null) {
        transformedMolecule = baseMolecule;
      }

      // If the transformed inchi is the same as the input inchi, we have reached a stable state in the chemical
      // transformation process, therefore break out of the loop.
      if (!foundEffectiveReaction) {
        break;
      }

      // If we see a similar transformed inchi as an earlier transformation, we know that we have enter a cyclical
      // loop that will go on to possibly infinity. Hence, we throw when such a situation happens.
      if (bagOfTransformedMolecules.contains(transformedMolecule)) {

        String generatedChemicalTransformations =
            StringUtils.join(mols2Inchis(new ArrayList<>(bagOfTransformedMolecules)), " -> ");

        String transformedInchi = mol2Inchi(transformedMolecule);
        String msg =
            String.format(INFINITE_LOOP_DETECTED_EXCEPTION_STRING, generatedChemicalTransformations, transformedInchi);

        LOGGER.error(msg);
        throw new InfiniteLoopDetectedException(msg);
      } else {
        if (transformedMolecule != null) {
          bagOfTransformedMolecules.add(transformedMolecule);
        }
      }

      baseMolecule = transformedMolecule;
      counter++;
    }

    LOGGER.debug("%d transformations for %s", counter, inchi);

    return transformedMolecule;
  }

  private Map<DesaltingRO, Reactor> reactors;
  private DesaltingROCorpus corpus;

  public void initReactors(File licenseFile) throws IOException, LicenseProcessingException, ReactionException {
    if (licenseFile != null) {
      LicenseManager.setLicenseFile(licenseFile.getAbsolutePath());
    }

    this.corpus = DESALTING_CORPUS_ROS.getDesaltingROS();
    List<DesaltingRO> ros = corpus.getRos();

    reactors = new HashMap<>(ros.size());

    for (DesaltingRO ro : ros) {
      Reactor reactor = new Reactor();
      reactor.setReactionString(ro.getReaction());
      reactors.put(ro, reactor);
    }
  }

  public void initReactors() throws IOException, LicenseProcessingException, ReactionException {
    initReactors(null);
  }

  private static List<Molecule> resolveMixtureOfAtomicComponents(Molecule molecule) {
    List<Molecule> fragments = Arrays.asList(molecule.convertToFrags());

    // Just return the whole molecule if there's only one fragment.
    if (fragments.size() == 1) {
      return fragments;
    }

    List<Molecule> resolvedFragments = new ArrayList<>(fragments.size());
    // Search for any carbon-containing fragments of the molecule.
    for (Molecule fragment : fragments) {
      if (fragment.getAtomCount(PeriodicSystem.C) > 0) {
        resolvedFragments.add(fragment);
      }
    }

    // If we have at least one organic component, return that.
    if (resolvedFragments.size() > 0) {
      return resolvedFragments;
    }

    // Return the largest component of inorganic molecules.
    Molecule fragmentWithHighestMass = null;
    double highestMass = 0.0;
    for (Molecule fragment : fragments) {
      double mass = fragment.getExactMass();
      if (fragmentWithHighestMass == null || mass > highestMass) {
        fragmentWithHighestMass = fragment;
        highestMass = mass;
      }
    }
    return Collections.singletonList(fragmentWithHighestMass);
  }

  /**
   * This function takes as input a Molecule and a Reactor and outputs the first possible product of the transformation.
   *
   * @param mol A Molecule representing the chemical reactant.
   * @param ro  A Reactor representing the reaction to apply.
   * @return The product of the reaction, or null if no product is produced.
   * @throws ReactionException
   */
  private Molecule getFirstPredictedProduct(Molecule mol, DesaltingRO ro) throws ReactionException, IOException {
    Reactor reactor = reactors.get(ro);
    List<Molecule[]> productSets = ReactionProjector.getAllProjectedProductSets(new Molecule[]{mol}, reactor);

    if (productSets.isEmpty()) {
      return null;
    }

    Molecule[] firstProducts = productSets.get(PRODUCT_TO_CHOOSE_INDEX);

    if (firstProducts.length != 1) {
      LOGGER.error("Reactor returned invalid number of products (%d), returning null.", productSets.size());
      return null;
    }

    return firstProducts[0];
  }

  /**
   * This function converts an input inchi to a smile representation of the chemical
   *
   * @param inchi The inchi representation of the chemical
   * @return The smile representation of the chemical
   */
  public String InchiToSmiles(String inchi) {
    try {
      return MolExporter.exportToFormat(MolImporter.importMol(inchi), "smiles");
    } catch (Exception err) {
      LOGGER.error(String.format("Error converting inchi %s to SMILES: %s\n", inchi, err.getMessage()));
      return null;
    }
  }
}
