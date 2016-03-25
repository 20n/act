package com.act.biointerpretation.step2_desalting;

import act.api.NoSQLAPI;
import act.server.Logger;
import act.shared.Reaction;
//import act.server.Molecules.RxnTx;
import act.shared.Chemical;
import act.shared.Reaction;
//import com.act.biointerpretation.FileUtils;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.marvin.io.formats.mdl.MolImport;
import chemaxon.marvin.uif.resource.ClassLoaderIconFactory;
import chemaxon.reaction.Reactor;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Desalter tries to remove any ionization or secondary ions from an inchi.
 * To use, create an instance of Desalter then use the clean method
 * to convert one inchi to a desalted version.  One Desalter can be reused.
 *
 * TODO: Edge cases that remain to be handled are:  radioactive. heme
 * See Desalter_modified_alldb_checked for examples of errors that remain
 *
 * TODO:  Add as positive tests the 'ok' things in Desalter_modified_alldb_checked
 *
 * TODO: use Chemaxon's Reactor class to do RO projection
 */
public class Desalter {
  private Indigo indigo;
  private IndigoInchi iinchi;
  private DesaltingROCorpus corpus;

  private StringBuilder log = new StringBuilder();
  private final DesaltingROCorpus desaltingROCorpus = new DesaltingROCorpus();

  public static void main(String[] args) throws Exception {
    Desalter cnc = new Desalter();
    try {
      cnc.test();
    } catch (Exception e) {
    }
///        cnc.examineAllDBChems();
    cnc.examineReactionChems();

  }

  /**
   * TODO: Replace with JUnit test or equivalent
   * <p>
   * Iterates through all the ROs and their curated
   * tests, and confirms that the product inchi
   * matches the expected inchi
   */
  public void test() throws Exception {

    List<DesaltingRO> tests = desaltingROCorpus.getDesaltingROS().getRos();

    //Test all the things that should get cleaned for proper cleaning
    for (DesaltingRO ro : tests) {
      String roSmarts = ro.getReaction();

      for (int i = 0; i < ro.getTestCases().size(); i++) {
        String input = ro.getTestCases().get(i).getInput();
        String output = ro.getTestCases().get(i).getExpected();
        String name = ro.getTestCases().get(i).getLabel();
        System.out.println("Testing: " + name + "  " + input);

        Set<String> results = null;
        try {
          results = this.clean(input);
        } catch (Exception err) {
          System.out.println("!!!!error cleaning:" + input);
          err.printStackTrace();
          throw err;
        }

        try {
          assertTrue(results.size() == 1);
        } catch (Exception err) {
          System.out.println("!!!!error cleaning, results size wrong: " + results.size());
          for (String result : results) {
            System.out.println(result);
          }
          err.printStackTrace();
          throw err;
        }

        String cleaned = results.iterator().next();

        try {
          assertTrue(results.size() == 1);
        } catch (Exception err) {
          System.out.println("!!!!error cleaning, results size wrong: " + results.size());
          for (String result : results) {
            System.out.println(result);
          }
          err.printStackTrace();
          throw err;
        }

        try {
          assertTrue(output.equals(cleaned));
        } catch (Exception err) {
          System.out.println(log.toString());
          System.out.println("!!!!Cleaned doesn't match output: " + cleaned + "  " + output);
          throw err;
        }
        System.out.println("\n" + name + " is ok\n");
      }
    }

    BufferedReader desaltConstantsReader = desaltingROCorpus.getDesalterConstantsReader();

    String inchi = null;
    while ((inchi = desaltConstantsReader.readLine()) != null) {
      String cleaned = null;
      try {
        Set<String> results = this.clean(inchi);
        assertTrue(results.size() == 1);
        cleaned = results.iterator().next();
      } catch (Exception err) {
        System.out.println("!!!!error cleaning constant test:" + cleaned + "  " + inchi);
        log = new StringBuilder();
        throw err;
      }

      try {
        assertTrue(inchi.equals(cleaned));
      } catch (Exception err) {
        System.out.println(log.toString());
        log = new StringBuilder();
        System.out.println("!!!!error cleaning constant: " + inchi);
        System.out.println("raw:" + InchiToSmiles(inchi));
        System.out.println("cleaned:" + InchiToSmiles(cleaned));
        throw err;
      }
    }

    desaltConstantsReader.close();
  }

  public void assertTrue(boolean isit) {
    if (isit == false) {
      throw new RuntimeException();
    }
  }

  /**
   * This method is used for testing Desalter
   * It pulls 10,000 salty inchis from the database
   * that are in reactions,
   * then cleans them and sorts them as to whether
   * they fail, clean to the same inchi, or get modified
   */
  public void examineReactionChems() {
    //Grab a large sample of chemicals that are in reactions
    List<String> salties = getReactionSalties();
    System.out.println("Have x salties: " + salties.size());
    sortSalties(salties, "rxn");
  }

  /**
   * This method is used for testing Desalter
   * It pulls 10,000 salty inchis from the database,
   * then cleans them and sorts them as to whether
   * they fail, clean to the same inchi, or get modified
   */
  public void examineAllDBChems() {
    List<String> salties = getAllSalties();
    sortSalties(salties, "all");
  }

  private List<String> getReactionSalties() {
    Set<String> salties = new HashSet<>();
    NoSQLAPI api = new NoSQLAPI("lucille", "synapse");  //just reading lucille
    Iterator<Reaction> allRxns = api.readRxnsFromInKnowledgeGraph();

    Set<Long> encountered = new HashSet<>();

    outer:
    while (allRxns.hasNext()) {
      Reaction rxn = allRxns.next();
      Set<Long> participants = new HashSet<>();

      for (Long id : rxn.getSubstrates()) {
        participants.add(id);
      }
      for (Long id : rxn.getProducts()) {
        participants.add(id);
      }

      for (Long id : participants) {
        if (salties.size() >= 10000) {
          break outer;
        }

        if (encountered.contains(id)) {
          continue;
        }
        encountered.add(id);

        Chemical achem = api.readChemicalFromInKnowledgeGraph(id);

        String inchi = achem.getInChI();

        if (inchi.contains("FAKE")) {
          continue;
        }

        try {
          InchiToSmiles(inchi);
        } catch (Exception err) {
          continue;
        }

        salties.add(inchi);
//                System.out.println("salties.size: " + salties.size());
      }
    }

    List<String> out = new ArrayList<>();
    out.addAll(salties);
    return out;
  }

  private List<String> getAllSalties() {
    List<String> out = new ArrayList<>();

    int count = 0;

    //First inspect all the chemicals
    NoSQLAPI api = new NoSQLAPI("lucille", "synapse");  //just reading lucille
    Iterator<Chemical> allChems = api.readChemsFromInKnowledgeGraph();
    while (allChems.hasNext()) {
      if (count >= 10000) {
        break;
      }
      try {
        Chemical achem = allChems.next();
        String inchi = achem.getInChI();

        if (inchi.contains("FAKE")) {
          continue;
        }


        String chopped = inchi.substring(6); //Chop off the Inchi= bit
        if (chopped.contains(".") || chopped.contains("I") || chopped.contains("Cl") || chopped.contains("Br") || chopped.contains("Na") || chopped.contains("K") || chopped.contains("Ca") || chopped.contains("Mg") || chopped.contains("Fe") || chopped.contains("Mn") || chopped.contains("Mo") || chopped.contains("As") || chopped.contains("Mb") || chopped.contains("p-") || chopped.contains("p+") || chopped.contains("q-") || chopped.contains("q+")) {

          try {
            iinchi.loadMolecule(inchi);
            out.add(inchi);
            count++;
          } catch (Exception err) {
            continue;
          }

        }
      } catch (Exception err) {
        System.err.println("Error inspecting chemicals");
        err.printStackTrace();
      }
    }

    return out;
  }


  private void sortSalties(List<String> salties, String mode) {

    //Clean the salties
    StringBuilder sbModified = new StringBuilder();
    StringBuilder sbUnchanged = new StringBuilder();
    StringBuilder sbErrors = new StringBuilder();
    StringBuilder sbComplex = new StringBuilder();
    for (int i = 0; i < salties.size(); i++) {
      log = new StringBuilder();
      String salty = salties.get(i);
      //System.out.println("Working on " + i + ": " + salty);
      String saltySmile = null;
      try {
        saltySmile = InchiToSmiles(salty);
      } catch (Exception err) {
        sbErrors.append("InchiToSmiles1\t" + salty + "\r\n");
        continue;
      }

      Set<String> results = null;
      try {
        results = this.clean(salty);
      } catch (Exception err) {
        sbErrors.append("cleaned\t" + salty + "\r\n");
        System.out.println(log.toString());
        log = new StringBuilder();
        err.printStackTrace();
        continue;
      }

      //Not sure results can be size zero or null, but check anyway
      if (results == null) {
        sbErrors.append("clean results are null:\t" + salty + "\r\n");
        continue;
      }
      if (results.isEmpty()) {
        sbErrors.append("clean results are empty:\t" + salty + "\r\n");
        continue;
      }

      //If cleaning resulted in a single organic product
      if (results.size() == 1) {
        String cleaned = results.iterator().next();
        String cleanSmile = null;
        try {
          cleanSmile = InchiToSmiles(cleaned);
        } catch (Exception err) {
          sbErrors.append("InchiToSmiles2\t" + salty + "\r\n");
        }

        if (!salty.equals(cleaned)) {
          sbModified.append(salty + "\t" + cleaned + "\t" + saltySmile + "\t" + cleanSmile + "\r\n");
        } else {
          sbUnchanged.append(salty + "\t" + saltySmile + "\r\n");
        }
      }
      //Otherwise there were multiple organic products
      else {
        sbComplex.append(">>\t" + salty + "\t" + saltySmile + "\r\n");
        for (String inchi : results) {
          sbComplex.append("\t" + inchi + "\t" + InchiToSmiles(inchi) + "\r\n");
        }
      }
    }

    File dir = new File("output/desalter");
    if (!dir.exists()) {
      dir.mkdir();
    }
    /*
    FileUtils.writeFile(sbModified.toString(), "output/desalter/Desalter_" + mode + "_modified.txt");
    FileUtils.writeFile(sbUnchanged.toString(), "output/desalter/Desalter_" + mode + "_unchanged.txt");
    FileUtils.writeFile(sbErrors.toString(), "output/desalter/Desalter_" + mode + "_errors.txt");
    FileUtils.writeFile(sbComplex.toString(), "output/desalter/Desalter_" + mode + "_complex.txt");
    */
  }

  public Desalter() throws IOException {
    indigo = new Indigo();
    iinchi = new IndigoInchi(indigo);
  }

  public Set<String> clean(String inchi) throws Exception {

    //First try dividing the molecule up
    String smiles = InchiToSmiles(inchi);

    //Clean up any unnecessary pipe-laced String
    String[] pipes = null;
    try {
      pipes = smiles.split("\\|");
    } catch (Exception err) {
      System.err.println("Error splitting pipes on " + smiles);
      throw err;
    }
    if (pipes.length == 2 || pipes.length == 3) {
      smiles = pipes[0].trim();
    }
    if (pipes.length > 3) {
      log.append("pipes length off: " + pipes.length + "\t" + smiles + "\n");
      log.append("\n");
    }

    //Extract individual smiles into a List
    List<String> mols = new ArrayList<>();
    String[] splitted = smiles.split("\\.");
    for (String str : splitted) {
      mols.add(str);
    }

    //Resolve the smiles to only those that are 2-carbon units
    Set<String> resolved = null;
    try {
      resolved = resolveMixtureOfSmiles(mols);
    } catch (Exception err) {
      log.append("\n");
      log.append("Error resolving smiles: " + smiles + "\t" + inchi + "\n");

      //Since this failed, revert to the original inchi, this only fails for 3 things of 10,000
      resolved = new HashSet<>();
      resolved.add(inchi);
    }

    //Clean each organic compound
    Set<String> out = new HashSet<>();
    for (String organic : resolved) {
      try {
        String cleaned = cleanOne(organic);
        out.add(cleaned);
      } catch (Exception err) {
        log.append("\n");
        log.append("Error cleaning organic: " + organic);
        throw err;
      }
    }
    return out;
  }

  private String cleanOne(String inchi) throws Exception {
    String out = inchi;
    String inputInchi = null;

    //Then try all the ROs
    Set<String> seenBefore = new HashSet<>();
    Outer:
    while (!out.equals(inputInchi)) {
      //Check that it's not in a loop
      if (seenBefore.contains(inputInchi)) {
        log.append("Encountered a loop for\n");
        for (String str : seenBefore) {
          log.append("\t" + str + "\n");
        }
        throw new Exception();
      } else {
        seenBefore.add(inputInchi);
      }

      inputInchi = out;

      for (DesaltingRO ro : desaltingROCorpus.getDesaltingROS().getRos()) {
        List<String> results = project(inputInchi, ro);
        if (results == null || results.isEmpty()) {
          continue;
        }
        try {
          String asmile = results.get(0);
          out = SmilesToInchi(asmile);
          if (!out.equals(inputInchi)) {
            continue Outer;
          }
        } catch (Exception err) {
          log.append("Error resolving smiles during projection loop: " + out + "\n");
          out = inchi; //Abort any projections, very rare
          break Outer;
        }
      }
    }

    log.append("cleaned:" + out + "\n");
    return out;
  }

  /**
   * Takes a list of smiles and decides which components of the mixture should be saved
   *
   * @param smiles
   * @return
   */
  private Set<String> resolveMixtureOfSmiles(List<String> smiles) {
    Set<String> out = new HashSet<>();

    for (String smile : smiles) {
      IndigoObject mol = null;
      try {
        //Count the number of carbons
        mol = indigo.loadMolecule(smile);
        int carbonCount = countCarbons(mol);

        //If the carbon count is at least 1, keep in results
        if (carbonCount > 0) {
          out.add(iinchi.getInchi(mol));
        }

      } catch (Exception err) {
        log.append("Error filtering organics during resolution: " + smile + "\n");
        err.printStackTrace();
        throw err;
      }
    }

    //If that process collected at least 1 organic, all done
    if (out.size() > 0) {
      return out;
    }

    //If got here, then there is no organic present, so pick the largest component
    String bestInchi = null;
    double highestMass = 0.0;
    for (String smile : smiles) {
      IndigoObject mol = null;
      try {
        mol = indigo.loadMolecule(smile);
        double mass = mol.monoisotopicMass();
        if (mass > highestMass) {
          highestMass = mass;
          bestInchi = iinchi.getInchi(mol);
        }
      } catch (Exception err) {
        log.append("Error picking biggest inorganic: " + smile + "\n");
        err.printStackTrace();
        throw err;
      }
    }
    out.add(bestInchi);
    return out;
  }

  private int countCarbons(IndigoObject mol) {
    String formula = mol.grossFormula();

    String[] splitted = formula.split("\\s");
    for (String atomEntry : splitted) {
      //See if the atom is carbon
      String atom = atomEntry.replaceAll("[0-9]+", "");
      if (!atom.equals("C")) {
        continue;
      }

      //Extract the carbon atom count
      String scount = atomEntry.replaceAll("[A-Za-z]+", "");
      int count = 1;
      try {
        count = Integer.parseInt(scount);
      } catch (Exception err) {
      }

      return count;
    }
    return 0;
  }

  private List<String> project(String inchi, DesaltingRO dro) {
    String ro = dro.getReaction();
    log.append("\n\tprojecting: " + dro.getDescription() + "\n");
    log.append("\tro :" + ro + "\n");
    log.append("\tinchi :" + inchi + "\n");


    String smiles = InchiToSmiles(inchi);
    log.append("\tsmiles :" + smiles + "\n");
    List<String> substrates = new ArrayList<>();
    substrates.add(smiles);


    //Do the projection of the ro
    Indigo indigo = new Indigo();
    try {
      // TODO: fix this.
      List<List<String>> pdts = expandChemical2AllProducts(substrates, ro, indigo, new IndigoInchi(indigo));
      List<String> products = new ArrayList<>();
      for (List<String> listy : pdts) {
        for (String entry : listy) {
          if (!products.contains(entry)) {
            products.add(entry);
            log.append("\t+result:" + entry + "\n");
          }
        }
      }
      return products;
    } catch (Exception err) {
      log.append("\t-result: no projection\n");
    }

    return null;
  }

  public String InchiToSmiles(String inchi) {
    try {
      IndigoObject mol = iinchi.loadMolecule(inchi);
      return mol.canonicalSmiles();
    } catch (Exception err) {
      System.err.println("Error converting InchiToSmiles: " + inchi);
      return null;
    }
  }

  public String SmilesToInchi(String smiles) {
    IndigoObject mol = indigo.loadMolecule(smiles);
    return iinchi.getInchi(mol);
  }

  /*
 * input: substrates in smiles DotNotation, ro SMARTS string (with DotNotation)
 * output: products in smiles DotNotation
 */
  public static List<List<String>> expandChemical2AllProducts(List<String> substrates, String roStr, Indigo indigo, IndigoInchi indigoInchi) {
    boolean debug = false;
    boolean smiles = true;

    // tutorial through example is here:
    // https://groups.google.com/d/msg/indigo-general/QTzP50ARHNw/7Y2U5ZOnh3QJ

    if (debug)
      System.out.format("\nAttempt tfm: \n%s\n%s\n\n", substrates, roStr);

    // Setting table of monomers (each reactant can have different monomers)
    IndigoObject monomers_table = indigo.createArray();
    IndigoObject output_reactions;

    try {
      int idx = 1;
      for (String s : substrates) {
        IndigoObject monomer_array = indigo.createArray();
        IndigoObject monomer;
        if (!smiles) {
          monomer = indigoInchi.loadMolecule(s);
          System.err.println(
              "You provided the substrate as InChI, but inchi does not "
                  +"play very well with DOT notation."
                  +"\n\tStill attempting conversion, but it will most likely fail."
                  +"\n\tReason is, InChI does semantic transformations that end up "
                  +"\n\tbreaking apart the heavy atom covalent bond we use as a DOT,"
                  +"\n\tand putting it as a metal ion on the side. Not good.");
        } else {
          monomer = indigo.loadMolecule(s);
        }
        monomer.setName("" + (idx++)); // for correct working: need names (!)
        monomer_array.arrayAdd(monomer);
        monomers_table.arrayAdd(monomer_array);
      }

      // Enumerating reaction products. Fn returns array of output reactions.
      IndigoObject q_rxn = getReactionObject(indigo, roStr);
      output_reactions = indigo.reactionProductEnumerate(q_rxn, monomers_table);

      // String all_inchi = "";
      List<List<String>> output = new ArrayList<List<String>>();

      // After this you will get array of output reactions. Each one of them
      // consists of products and monomers used to build these products.
      for (int i = 0; i < output_reactions.count(); i++) {
        IndigoObject out_rxn = output_reactions.at(i);

        // Saving each product from each output reaction.
        // In this example each reaction has only one product

        List<String> products_1rxn = new ArrayList<String>();
        for (IndigoObject products : out_rxn.iterateProducts()) {
          if (debug) System.out.println("----- Product: " + products.smiles());

          for (IndigoObject comp : products.iterateComponents())
          {
            IndigoObject mol = comp.clone();
            if (smiles)
              products_1rxn.add(mol.smiles());
            else
              products_1rxn.add(indigoInchi.getInchi(mol));
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
        output.add(products_1rxn);
      }

      return output.size() == 0 ? null : output;
    } catch(Exception e) {
      if (e.getMessage().equals("core: Too small monomers array")) {
        System.err.println("#args in operator > #args supplied");
      } else {
        e.printStackTrace();
      }
      return null;
    }
  }

  private static IndigoObject getReactionObject(Indigo indigo, String transformSmiles) {
    if (transformSmiles.contains("|")) {
      Logger.print(0, "WOA! Need to fix this. The operators DB "
          + "was not correctly populated. It still has |f or |$ entries...\n");
      transformSmiles = HACKY_fixQueryAtomsMapping(transformSmiles);
    }

    IndigoObject reaction = indigo.loadQueryReaction(transformSmiles);
    return reaction;
  }

  public static String HACKY_fixQueryAtomsMapping(String smiles) {
    // Convert strings of the form
    // [*]C([*])=O>>[H]C([*])(O[H])[*] |$[*:3];;[*:8];;;;[*:8];;;[*:3]$|
    // or [H]C(=[*])[*].O([H])[H]>>[*]C(=[*])O |f:0.1,$;;_R2;_R1;;;;_R1;;_R2;$|
    // to real query strings:
    // [*:3]C([*:8])=O>>[H]C([*:3])(O[H])[*:8]
    //
    // Also, make sure that there are no 0-indexes. 1-indexed is what we want.

    int mid = smiles.indexOf("|");

    // sometimes, e.g., when computing EROs over very small molecules, the ERO is the entire concrete string
    // with no wild cards; and so no R groups appear there.. In that case we will not have have the " |$_R1;;_R2;;;;_R1;;_R2;$|"
    // portion of the string to lookup into. So return the input...
    if (mid == -1) {
      if (!smiles.contains("_R")) {
        Logger.printf(0, "HACKY_FIX: Does not contain _R Smiles %s\n", smiles);
        return smiles;
      } else {
        System.err.println("Query smiles with unexpected format encountered: " + smiles);
        System.exit(-1);
      }
    }

    String smiles_unindexed = smiles.substring(0, mid);
    String smiles_indexes = smiles.substring(mid);
    // System.out.format("{%s} {%s}\n", smiles_unindexed, smiles_indexes);

    int ptr = 0, ptr_ind = 0;
    while ((ptr = smiles_unindexed.indexOf("[*]", ptr)) != -1) {
      // extract the next index from smiles_indexes and insert it here...
      ptr_ind = smiles_indexes.indexOf("_R", ptr_ind);
      int end = ptr_ind+2;
      char c;
      while ((c = smiles_indexes.charAt(end)) >= '0' && c <= '9') end++;
      int index = Integer.parseInt(smiles_indexes.substring(ptr_ind + 2, end));

      // Look at email thread titled "[indigo-general] Re: applying reaction smarts to enumerate products"
      // for why we need to convert each [*] |$_R1 to [H,*:1]
      //
      // Quote:`` I my previous letter I wrote that there is bug that [*,H] and [H,*] has different meanings.
      //          I realized that it is not bug. [H,*] mean "any atom except H, or H", while [*,H] means
      //          "any atom except H, or any atom except H with 1 connected hydrogen". ''

      // now update the string.. and the pointers...
      smiles_unindexed =
          smiles_unindexed.substring(0, ptr+1) + // grab everything before and including the `['
              "H,*:" + index + // add the H,*:1
              smiles_unindexed.substring(ptr+2); // grab everything after the *], excluding the `*', but including the `]'
      ptr += 6; // need to jump at least six chars to be at the ending `]'...
      ptr_ind = end; // the indexes pointer needs to be moved past the end of the index [*:124]
    }
    Logger.printf(0, "HACKY_FIX: fixed %s to be %s\n", smiles, smiles_unindexed);

    return smiles_unindexed;
  }


}
