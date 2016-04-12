package com.act.biointerpretation.step2_desalting;

import chemaxon.calculations.hydrogenize.Hydrogenize;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import chemaxon.marvin.util.MolFragLoader;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.struc.PeriodicSystem;
import chemaxon.util.iterator.MoleculeIterator;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
 * stored in a file called com/act/biointerpretation/step2_desalting/desalting_ros.json.
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
 * There is a second file (com/act/biointerpretation/step2_desalting/desalter_constants.txt)
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
 * TODO: use Chemaxon's Reactor class to do RO projection
 */
public class Desalter {
  private static final DesaltingROCorpus DESALTING_CORPUS_ROS = new DesaltingROCorpus();
  private static final Integer MAX_NUMBER_OF_ROS_TRANSFORMATION_ITERATIONS = 1000;
  private static final Pattern CARBON_COUNT_PATTERN_MATCH = Pattern.compile("\\b[Cc](\\d*)\\b");
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);
  private static final String INFINITE_LOOP_DETECTED_EXCEPTION_STRING = "The algorithm has encountered a loop for this " +
      "set of transformations %s on this transformed inchi: %s";
  // TODO: Swap out indigo for chemaxon
  private Indigo INDIGO;
  private IndigoInchi IINCHI;

  public static class InfiniteLoopDetectedException extends Exception {
    public InfiniteLoopDetectedException(String message) {
      super(message);
    }
  }
  public static final boolean DEFAULT_USE_INCHI_FILTERING = true;

  private boolean useInChIFiltering = DEFAULT_USE_INCHI_FILTERING;

  public Desalter() {

  }

  public Desalter(boolean useInChIFiltering) {
    this.useInChIFiltering = useInChIFiltering;
  }

  /**
   * This function desalts a given inchi representation of a molecule by first preprocessing the molecule by taking
   * out extra representations like free radicals, only processing organics or a subset of an inorganic molecule
   * and then desalting those component only.
   * @param inchi The inchi representation of the chemical
   * @return A set of desalted compounds within the input chemical
   * @throws Exception
   */
  public Set<String> desaltMolecule(String inchi)
      throws InfiniteLoopDetectedException, IOException, ReactionException {
    // Resolve the smiles to only those that are 2-carbon units.
    // Do not store the output of MolImporter, as the object will be destroyed during fragmentation.
    List<Molecule> resolved = resolveMixtureOfAtomicComponents(MolImporter.importMol(inchi));

    //Clean each compound
    final List<Molecule> desaltedAndDeionized = new ArrayList<>(resolved.size());
    for (Molecule organicOrBiggestInorganicMass : resolved) {
      Molecule desaltedChemicalFragment = applyROsToMolecule(organicOrBiggestInorganicMass, inchi);
      desaltedAndDeionized.add(desaltedChemicalFragment);
    }

    // Don't combine fragments in order to match Indigo behavior.
    return new HashSet<>(mols2Inchis(desaltedAndDeionized));
  }

  public Molecule combineFragments(List<Molecule> fragments) throws IOException{
    MoleculeIterator molIterator = new MoleculeIterator() {
      Iterator<Molecule> iter = fragments.iterator();
      int index = 0;

      @Override
      public Molecule next() {
        index++;
        return iter.next();
      }

      @Override
      public boolean hasNext() {
        return iter.hasNext();
      }

      @Override
      public Throwable getThrowable() {
        return null;
      }

      @Override
      public double estimateProgress() {
        return Double.valueOf(index) / Double.valueOf(fragments.size());
      }
    };

    return new MolFragLoader(molIterator).loadFrags();
  }

  public static String mol2Inchi(Molecule mol) throws IOException {
    // See https://docs.chemaxon.com/display/FF/InChi+and+InChiKey+export+options for MolExporter options.
    // See also https://www.chemaxon.com/forum/ftopic10424.html for why we want to use SAbs.
    return MolExporter.exportToFormat(mol, "inchi:SAbs,AuxNone,Woff");
  }

  public static List<String> mols2Inchis(List<Molecule> mols) throws IOException {
    List<String> inchis = new ArrayList<>(mols.size());
    for (Molecule mol : mols) {
      inchis.add(mol2Inchi(mol));
    }
    return inchis;
  }

  /**
   * This function desalts an input inchi chemical by running it through a list of curated desalting ROs in a loop
   * and transforms the inchi till it reaches a stable state.
   * @param baseMolecule The molecule on which to project desalting ROs.
   * @param inchi The inchi for the base molecule, used for logging.
   * @return The desalted inchi chemical
   * @throws Exception
   */
  protected Molecule applyROsToMolecule(Molecule baseMolecule, String inchi)
      throws IOException, InfiniteLoopDetectedException, ReactionException {
    Molecule transformedMolecule = baseMolecule;

    Hydrogenize.convertImplicitHToExplicit(baseMolecule);

    //Then try all the ROs
    Set<Molecule> bagOfTransformedMolecules = new LinkedHashSet<>();

    int counter = 0;
    while(counter < MAX_NUMBER_OF_ROS_TRANSFORMATION_ITERATIONS) {
      boolean foundEffectiveReaction = false;

      //for (Reactor reactor : reactors) {
      for (DesaltingRO ro : corpus.getRos()) {
        Molecule product = project(baseMolecule, ro);

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

  private List<Reactor> reactors;
  private DesaltingROCorpus corpus;
  public void initReactors(File licenseFile) throws IOException, LicenseProcessingException, ReactionException {
    if (licenseFile != null) {
      LicenseManager.setLicenseFile(licenseFile.getAbsolutePath());
    }

    this.corpus = DESALTING_CORPUS_ROS.getDesaltingROS();
    List<DesaltingRO> ros = corpus.getRos();

    reactors = new ArrayList<>(ros.size());

    for (DesaltingRO ro : ros) {
      Reactor reactor = new Reactor();
      reactor.setReactionString(ro.getReaction());
      reactors.add(reactor);
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
   * This function takes as input aMolecule and a Reactor and outputs the product of the transformation.
   * @param mol A Molecule representing the chemical reactant.
   * @param reactor A Reactor representing the reaction to apply.
   * @return The product of the reaction
   */

  private static Molecule project(Molecule mol, Reactor reactor) throws ReactionException {
    //reactor.restart();
    reactor.setReactants(new Molecule[]{mol});
    Molecule[] products = reactor.react();
    if (products == null) {
      return null;
    }
    // reactor.restart();
    int productCount = products.length;
    if (productCount == 0) {
      // TODO: better log messages.
      LOGGER.error("Reactor returned no products %d", productCount);
      return null;
    } else if (productCount > 1) {
      LOGGER.error("Reactor returned multiple products (%d), taking first", productCount);
    }
    return products[0];
  }

  private static Molecule project(Molecule mol, DesaltingRO ro) throws ReactionException {
    Reactor reactor = new Reactor();
    reactor.setReactionString(ro.getReaction());
    Molecule result = project(mol, reactor);
    return result;
  }

  /**
   * This function converts an input inchi to a smile representation of the chemical
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
