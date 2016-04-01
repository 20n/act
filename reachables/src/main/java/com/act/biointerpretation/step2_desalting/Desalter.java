package com.act.biointerpretation.step2_desalting;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  // TODO: Swap out indigo for chemaxon
  private static Indigo INDIGO = new Indigo();
  private static IndigoInchi IINCHI = new IndigoInchi(INDIGO);
  private static final DesaltingROCorpus DESALTING_CORPUS_ROS = new DesaltingROCorpus();
  private static final Integer MAX_NUMBER_OF_ROS_TRANSFORMATION_ITERATIONS = 1000;
  private static final Pattern CARBON_COUNT_PATTERN_MATCH = Pattern.compile("\\b[Cc](\\d*)\\b");
  private static final Logger LOGGER = LogManager.getLogger(Desalter.class);
  private static final String infiniteLoopDetectedExceptionString = "The algorithm has encountered a loop for this " +
      "set of transformations %s on this transformed inchi: %s";

  public static class InfiniteLoopDetectedException extends Exception {
    public InfiniteLoopDetectedException(String message) {
      super(message);
    }
  }

  /**
   * This function desalts a given inchi representation of a molecule by first preprocessing the molecule by taking
   * out extra representations like free radicals, only processing organics or a subset of an inorganic molecule
   * and then desalting those component only.
   * @param inchi The inchi representation of the chemical
   * @return A set of desalted compounds within the input chemical
   * @throws Exception
   */
  public static Set<String> desaltMolecule(String inchi) throws InfiniteLoopDetectedException, IOException {
    //First try dividing the molecule up
    String smiles = InchiToSmiles(inchi);

    //Clean up any unnecessary pipe-laced String
    String[] splitSmilesRepresentation = smiles.split("\\|");
    String sanitizedSmile = splitSmilesRepresentation[0].trim();

    // If there are pipes in the smiles representation, this indicates the presence of a radical. Normally, there
    // are two pipe symbols expected in this representation, as seen in the documentation here:
    // https://www.chemaxon.com/marvin-archive/latest/help/formats/cxsmiles-doc.html#cxsmiles. In this case, the
    // first element of splitSmilesRepresentation is not sanitized properly.
    if (splitSmilesRepresentation.length > 2) {
      LOGGER.error(String.format("Smile %s generated a pipe length of %s\n", smiles, splitSmilesRepresentation.length));
    }

    //Extract individual smiles into a List
    List<String> individualMolecules = new ArrayList<>();
    for (String str : sanitizedSmile.split("\\.")) {
      individualMolecules.add(str);
    }

    //Resolve the smiles to only those that are 2-carbon units
    Set<String> resolved = resolveMixtureOfSmiles(individualMolecules);

    //Clean each compound
    Set<String> out = new HashSet<>();
    for (String organicOrBiggestInorganicMass : resolved) {
      String desaltedChemicalModule = desaltChemicalComponent(organicOrBiggestInorganicMass);
      out.add(desaltedChemicalModule);
    }
    return out;
  }

  /**
   * This function desalts an input inchi chemical by running it through a list of curated desalting ROs in a loop
   * and transforms the inchi till it reaches a stable state.
   * @param inchi The inchi representation of a chemical
   * @return The desalted inchi chemical
   * @throws Exception
   */
  private static String desaltChemicalComponent(String inchi) throws IOException, InfiniteLoopDetectedException {
    String transformedInchi = null;
    String inputInchi = inchi;

    //Then try all the ROs
    Set<String> bagOfTrasformedInchis = new LinkedHashSet<>();

    int counter = 0;

    while (counter < MAX_NUMBER_OF_ROS_TRANSFORMATION_ITERATIONS) {
      // If the transformed inchi is the same as the input inchi, we have reached a stable state in the chemical
      // transformation process, therefore break out of the loop.
      if (inputInchi.equals(transformedInchi)) {
        break;
      }

      // If we see a similar transformed inchi as an earlier transformation, we know that we have enter a cyclical
      // loop that will go on to possibly infinity. Hence, we throw when such a situation happens.
      if (bagOfTrasformedInchis.contains(transformedInchi)) {
        String generatedChemicalTransformations = StringUtils.join(bagOfTrasformedInchis, " -> ");
        generatedChemicalTransformations += String.format(" Offending inchi: %s", transformedInchi);

        LOGGER.error(String.format(infiniteLoopDetectedExceptionString, generatedChemicalTransformations, transformedInchi));

        throw new InfiniteLoopDetectedException(String.format(infiniteLoopDetectedExceptionString,
            generatedChemicalTransformations, transformedInchi));
      } else {
        if (transformedInchi != null) {
          bagOfTrasformedInchis.add(transformedInchi);
        }
      }

      // In the first loop, assigned transformedInchi to inputInchi, so that we do no break out in the
      // inputInchi.equals(transformedInchi) check. After the first loop, we assign the inputInchi to the going to be
      // transformed inchi.
      if (transformedInchi == null) {
        transformedInchi = inputInchi;
      } else {
        inputInchi = transformedInchi;
      }

      for (DesaltingRO ro : DESALTING_CORPUS_ROS.getDesaltingROS().getRos()) {
        List<String> productsOfROTransformation = project(transformedInchi, ro);

        // If there are no productsOfROTransformation from the transformation, skip to the next RO.
        if (productsOfROTransformation == null || productsOfROTransformation.isEmpty()) {
          continue;
        }

        try {
          String mainProductOfROTransformation = productsOfROTransformation.get(0);
          transformedInchi = SmilesToInchi(mainProductOfROTransformation);
          if (!transformedInchi.equals(inputInchi)) {
            break;
          }
        } catch (Exception err) {
          LOGGER.error(String.format("Error resolving smiles during projection loop: %s\n", transformedInchi));
          LOGGER.error(String.format("Exception thrown: %s\n", err.getMessage()));
          transformedInchi = inchi; //Abort any projections, very rare
          break;
        }
      }
      counter++;
      LOGGER.debug("%d transformations for %s", counter, inchi);
    }

    return transformedInchi;
  }

  /**
   * Takes a list of smiles and decides which components of the mixture should be saved. For organics, this would be
   * molecules that contain atleast one carbon. For non-organics, it would be the molecule with the highest mass.
   * @param smiles A list of smile represented molecules.
   * @return A set of molecules that meet the resolved condition.
   */
  private static Set<String> resolveMixtureOfSmiles(List<String> smiles) {
    Set<String> resolvedMolecules = new HashSet<>();

    for (String smile : smiles) {
      IndigoObject mol = INDIGO.loadMolecule(smile);

      if (countCarbons(mol) > 0) {
        resolvedMolecules.add(IINCHI.getInchi(mol));
      }
    }

    // If that process collected at least 1 organic, all done
    if (resolvedMolecules.size() > 0) {
      return resolvedMolecules;
    }

    // Since there are no organics present, pick the largest component
    String inchiWithHighestMass = null;
    double highestMass = 0.0;
    for (String smile : smiles) {
      IndigoObject mol = INDIGO.loadMolecule(smile);
      double mass = mol.monoisotopicMass();
      if (mass > highestMass) {
        highestMass = mass;
        inchiWithHighestMass = IINCHI.getInchi(mol);
      }
    }

    resolvedMolecules.add(inchiWithHighestMass);
    return resolvedMolecules;
  }

  /**
   * This function counts the total number of carbons in the input molecule
   * @param molecule The indigo representation of the molecule.
   * @return The total number of carbon atoms in the molecule
   */
  private static int countCarbons(IndigoObject molecule) {
    String formula = molecule.grossFormula();

    // The representation from a formula is for example, C8 H7 N5, where there is a space between the molecules. We
    // need to split the formula based on the space in order to extract the number of carbons.
    String[] listOfAtomAndTheirCounts = formula.split("\\s");

    for (String atomEntry : listOfAtomAndTheirCounts) {
      Matcher matchAtomEntry = CARBON_COUNT_PATTERN_MATCH.matcher(atomEntry);

      if (matchAtomEntry.find()) {
        String matchedAtomCount = matchAtomEntry.group(1);
        int count = 1;

        if (matchedAtomCount.equals("")) {
          return count;
        }

        try {
          count = Integer.parseInt(matchedAtomCount);
        } catch (Exception err) {
          LOGGER.error(String.format("Error parsing atom count: %s", count));
          LOGGER.error(String.format("Exception thrown was: %s", err.getMessage()));
        }
        return count;
      }
    }

    // If we did not find any carbon atoms, return 0.
    return 0;
  }

  /**
   * This function takes as input an inchi and a RO and outputs the product of the transformation.
   * @param inchi The inchi chemical
   * @param desaltingRO The desalting RO
   * @return The product of the reaction
   */
  private static List<String> project(String inchi, DesaltingRO desaltingRO) {
    String ro = desaltingRO.getReaction();
    LOGGER.debug(String.format("Projecting: %s\n", desaltingRO.getDescription()));
    LOGGER.debug(String.format("RO: %s\n", ro));
    LOGGER.debug(String.format("Inchi: %s\n", inchi));

    String smiles = InchiToSmiles(inchi);
    LOGGER.debug(String.format("Smiles: %s\n", smiles));
    List<String> substrates = new ArrayList<>();
    substrates.add(smiles);

    // Do the projection of the ro
    try {
      List<List<String>> productTransformations = expandSubstratesAndROsToProducts(substrates, ro);

      if (productTransformations == null) {
        return null;
      }

      List<String> relevantProducts = new ArrayList<>();

      for (List<String> transformation : productTransformations) {
        for (String entry : transformation) {
          if (!relevantProducts.contains(entry)) {
            relevantProducts.add(entry);
            LOGGER.debug(String.format("Result: %s\n", entry));
          }
        }
      }

      return relevantProducts;
    } catch (Exception err) {
      LOGGER.error(String.format("Result: no projection\n"));
      LOGGER.error(String.format("Exception message: %s", err.getMessage()));
    }

    return null;
  }

  /**
   * This function converts an input inchi to a smile representation of the chemical
   * @param inchi The inchi representation of the chemical
   * @return The smile representation of the chemical
   */
  public static String InchiToSmiles(String inchi) {
    try {
      IndigoObject mol = IINCHI.loadMolecule(inchi);
      return mol.canonicalSmiles();
    } catch (Exception err) {
      LOGGER.error(String.format("Error converting InchiToSmile: %s\n", inchi));
      return null;
    }
  }

  /**
   * This function converts smiles to inchi
   * @param smiles The smiles representation of a chemical
   * @return The inchi representation of the chemical
   */
  public static String SmilesToInchi(String smiles) {
    IndigoObject mol = INDIGO.loadMolecule(smiles);
    return IINCHI.getInchi(mol);
  }

  /**
   * This function computes the products of a given reaction + RO combination.
   * @param substratesInSmilesFormat - Substrates of the reaction in smile format
   * @param roString - The RO for the reaction.
   * @return A list of an array of products, indexed by each substrate transformation by the RO.
   */
  public static List<List<String>> expandSubstratesAndROsToProducts(List<String> substratesInSmilesFormat, String roString) {
    // tutorial through example is here:
    // https://groups.google.com/d/msg/indigo-general/QTzP50ARHNw/7Y2U5ZOnh3QJ
    LOGGER.debug(String.format("Transforming substrates %s and ROs to products.",
        StringUtils.join(substratesInSmilesFormat, " "), roString));

    // Setting table of monomers (each reactant can have different monomers)
    IndigoObject monomersTable = INDIGO.createArray();

    try {
      int substrateIndex = 1;
      for (String substrate : substratesInSmilesFormat) {
        IndigoObject monomerArrayForSubstrate = INDIGO.createArray();
        IndigoObject monomer = INDIGO.loadMolecule(substrate);

        // We have to set the name for the monomer in order for it to work.
        monomer.setName(Integer.toString(substrateIndex));
        monomerArrayForSubstrate.arrayAdd(monomer);

        // We add the list of monomers per substarte to the monomers table.
        monomersTable.arrayAdd(monomerArrayForSubstrate);
        substrateIndex++;
      }

      // Enumerating reaction products. Fn returns array of output reactions.
      IndigoObject reactionObject = getReactionObject(roString);

      IndigoObject productsEnumeration = INDIGO.reactionProductEnumerate(reactionObject, monomersTable);

      List<List<String>> resultantProducts = new ArrayList<List<String>>();

      // After this you will get array of output reactions. Each one of them
      // consists of products and monomers used to build these products.
      for (int i = 0; i < productsEnumeration.count(); i++) {
        IndigoObject outputReaction = productsEnumeration.at(i);

        // Saving each product from each output reaction.
        // In this example each reaction has only one product
        List<String> outputProducts = new ArrayList<>();
        for (IndigoObject products : outputReaction.iterateProducts()) {
          for (IndigoObject chemicalComponent : products.iterateComponents()) {
            //IndigoObject mol = chemicalComponent.clone();
            outputProducts.add(chemicalComponent.clone().smiles());
          }
          /*
          * Other additional manipulations; see email thread:
					* https://groups.google.com/d/msg/indigo-general/QTzP50ARHNw/7Y2U5ZOnh3QJ
					* -- Applying layout to molecule
					*  if (!mol.hasCoord())
					*     mol.layout();
					*
					* -- Marking undefined cis-trans bonds
					* mol.markEitherCisTrans();
					*/
        }
        resultantProducts.add(outputProducts);
      }

      return resultantProducts;
    } catch(Exception e) {
      if (e.getMessage().equals("core: Too small monomers array")) {
        LOGGER.error("#args in operator > #args supplied");
      } else {
        LOGGER.error("Exception message is :%s", e.getMessage());
      }
      return null;
    }
  }

  /**
   * This function converts the input string representation of a smiles RO into an IndigoObject representing the RO.
   * @param roStringInSmilesFormat - The string representation of the RO
   * @return Indigo representation of the string.
   */
  private static IndigoObject getReactionObject(String roStringInSmilesFormat) {
    if (roStringInSmilesFormat.contains("|")) {
      LOGGER.debug(
          String.format("The operators DB was not correctly populated. It still has |f or |$ entries for the RO: %s\n",
              roStringInSmilesFormat));

      roStringInSmilesFormat = fixQueryAtomsMapping(roStringInSmilesFormat);
    }

    IndigoObject reaction = INDIGO.loadQueryReaction(roStringInSmilesFormat);
    return reaction;
  }

  /**
   * TODO: This version of the function is hacky. I do not understand this function since non of the test cases hit
   * this code. Please do a more thorough refactor once we have uses for this.
   *
   * @param smiles - A smiles representation of a chemical
   * @return
   */
  public static String fixQueryAtomsMapping(String smiles) {
    // Convert strings of the form
    // [*]C([*])=O>>[H]C([*])(O[H])[*] |$[*:3];;[*:8];;;;[*:8];;;[*:3]$|
    // or [H]C(=[*])[*].O([H])[H]>>[*]C(=[*])O |f:0.1,$;;_R2;_R1;;;;_R1;;_R2;$|
    // to real query strings:
    // [*:3]C([*:8])=O>>[H]C([*:3])(O[H])[*:8]
    //
    // Also, make sure that there are no 0-indexes. 1-indexed is what we want.

    int pipePivotPoint = smiles.indexOf("|");

    // sometimes, e.g., when computing EROs over very small molecules, the ERO is the entire concrete string
    // with no wild cards; and so no R groups appear there.. In that case we will not have have the " |$_R1;;_R2;;;;_R1;;_R2;$|"
    // portion of the string to lookup into. So return the input...
    if (pipePivotPoint == -1) {
      if (!smiles.contains("_R")) {
        LOGGER.debug(String.format("Does not contain _R Smiles %s\n", smiles));
        return smiles;
      } else {
        LOGGER.error(String.format("Query smiles with unexpected format encountered: %s", smiles));
        System.exit(-1);
      }
    }

    String smileBeforePivot = smiles.substring(0, pipePivotPoint);
    String smileAfterPivot = smiles.substring(pipePivotPoint);

    int pointer = 0, pointerIndex = 0;
    while ((pointer = smileBeforePivot.indexOf("[*]", pointer)) != -1) {
      // extract the next index from smiles_indexes and insert it here...
      pointerIndex = smileAfterPivot.indexOf("_R", pointerIndex);
      int end = pointerIndex + 2;
      char c;
      while ((c = smileAfterPivot.charAt(end)) >= '0' && c <= '9') end++;
      int index = Integer.parseInt(smileAfterPivot.substring(pointerIndex + 2, end));

      // Look at email thread titled "[indigo-general] Re: applying reaction smarts to enumerate products"
      // for why we need to convert each [*] |$_R1 to [H,*:1]
      //
      // Quote:`` I my previous letter I wrote that there is bug that [*,H] and [H,*] has different meanings.
      //          I realized that it is not bug. [H,*] mean "any atom except H, or H", while [*,H] means
      //          "any atom except H, or any atom except H with 1 connected hydrogen". ''

      // now update the string.. and the pointers...
      smileBeforePivot =
          smileBeforePivot.substring(0, pointer + 1) + // grab everything before and including the `['
              "H,*:" + index + // add the H,*:1
              smileBeforePivot.substring(pointer + 2); // grab everything after the *], excluding the `*', but including the `]'
      pointer += 6; // need to jump at least six chars to be at the ending `]'...
      pointerIndex = end; // the indexes pointer needs to be moved past the end of the index [*:124]
    }
    LOGGER.debug(String.format("Fixed %s to be %s\n", smiles, smileBeforePivot));
    return smileBeforePivot;
  }
}
