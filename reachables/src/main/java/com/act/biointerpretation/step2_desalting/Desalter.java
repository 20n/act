package com.act.biointerpretation.step2_desalting;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import act.server.Molecules.RxnTx;
import act.shared.Chemical;
import act.shared.Reaction;
import com.act.biointerpretation.FileUtils;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

import java.io.File;
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
 */
public class Desalter {
  private Indigo indigo;
  private IndigoInchi iinchi;
  private List<DesaltRO> ros;

  private StringBuilder log = new StringBuilder();

  public static void main(String[] args) {
    Desalter cnc = new Desalter();
    try {
      cnc.test();
    } catch (Exception e) {
    }
///        cnc.examineAllDBChems();
    cnc.examineReactionChems();
  }

  private class DesaltRO {
    String ro;
    String name;
    List<String> testInputs = new ArrayList<>();
    List<String> testOutputs = new ArrayList<>();
    List<String> testNames = new ArrayList<>();
  }

  /**
   * TODO: Replace with JUnit test or equivalent
   * <p>
   * Iterates through all the ROs and their curated
   * tests, and confirms that the product inchi
   * matches the expected inchi
   */
  public void test() throws Exception {
    //Test all the things that should get cleaned for proper cleaning
    for (DesaltRO ro : ros) {
      String roSmarts = ro.ro;

      for (int i = 0; i < ro.testInputs.size(); i++) {
        String input = ro.testInputs.get(i);
        String output = ro.testOutputs.get(i);
        String name = ro.testNames.get(i);
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

        String cleaned = results.iterator().next();

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

    //Check things that should not be cleaned for identity
    String data = FileUtils.readFile("data/desalter_constants.txt");
    String[] lines = data.split("\\r|\\r?\\n");
    for (String inchi : lines) {
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
    FileUtils.writeFile(sbModified.toString(), "output/desalter/Desalter_" + mode + "_modified.txt");
    FileUtils.writeFile(sbUnchanged.toString(), "output/desalter/Desalter_" + mode + "_unchanged.txt");
    FileUtils.writeFile(sbErrors.toString(), "output/desalter/Desalter_" + mode + "_errors.txt");
    FileUtils.writeFile(sbComplex.toString(), "output/desalter/Desalter_" + mode + "_complex.txt");
  }


  public Desalter() {
    indigo = new Indigo();
    iinchi = new IndigoInchi(indigo);
    ros = new ArrayList<>();

    String data = FileUtils.readFile("data/desalting_ros.txt");
    data = data.replace("\"", "");
    String[] regions = data.split("###");
    for (String region : regions) {
      if (region == null || region.equals("")) {
        continue;
      }
      DesaltRO ro = new DesaltRO();
      String[] lines = region.split("(\\r|\\r?\\n)");
      ro.name = lines[0].trim();
      ro.ro = lines[1].trim();
      for (int i = 2; i < lines.length; i++) {
        String line = lines[i];
        String[] tabs = line.split("\t");
        ro.testInputs.add(tabs[0].trim());
        ro.testOutputs.add(tabs[1].trim());
        try {
          ro.testNames.add(tabs[2].trim());
        } catch (Exception err) {
          ro.testNames.add("noname");
        }
      }
      ros.add(ro);
    }
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

      for (DesaltRO ro : ros) {
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

  private List<String> project(String inchi, DesaltRO dro) {
    String ro = dro.ro;
    log.append("\n\tprojecting: " + dro.name + "\n");
    log.append("\tro :" + ro + "\n");
    log.append("\tinchi :" + inchi + "\n");


    String smiles = InchiToSmiles(inchi);
    log.append("\tsmiles :" + smiles + "\n");
    List<String> substrates = new ArrayList<>();
    substrates.add(smiles);


    //Do the projection of the ro
    Indigo indigo = new Indigo();
    try {
      List<List<String>> pdts = RxnTx.expandChemical2AllProducts(substrates, ro, indigo, new IndigoInchi(indigo));
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

}
