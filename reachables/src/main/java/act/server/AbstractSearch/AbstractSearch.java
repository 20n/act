package act.server.AbstractSearch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;


import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.google.gwt.dev.util.Pair;

import act.server.AbstractSearch.AbstractReactionsHypergraph.IdType;
import act.server.EnumPath.Enumerator;
import act.server.EnumPath.OperatorSet;
import act.server.Molecules.BadRxns;
import act.server.Molecules.RO;
import act.server.Molecules.TheoryROClasses;
import act.server.Molecules.TheoryROs;
import act.server.SQLInterface.DBIterator;
import act.server.SQLInterface.MongoDBPaths;
import act.server.Search.PathBFS;
import act.shared.AAMFailException;
import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.MalFormedReactionException;
import act.shared.NoSMILES4InChiException;
import act.shared.OperatorInferFailException;
import act.shared.ROApplication;
import act.shared.Reaction;
import act.server.ActAdminServiceImpl;
import act.client.CommandLineRun;

public class AbstractSearch {

  MongoDBPaths DB;
  HashMap<Integer, OperatorSet> ops;
  HashMap<String, MoleculeEquivalenceClass> chemicalMap = new HashMap<String, MoleculeEquivalenceClass>();
  HashMap<String, ReactionEquivalenceClass> reactionMap = new HashMap<String, ReactionEquivalenceClass>();
  HashMap<Long, CarbonSkeleton> UUIDToCarbonSkeletonMap = new HashMap<Long, CarbonSkeleton>();
  String label;
  Chemical target;
  boolean indexUpdate = false;
  HashMap<String, Boolean> visited = new HashMap<String, Boolean>();
  HashMap<String, List<MultiplePathNode>> visitedNodes = new HashMap<String, List<MultiplePathNode>>();
  HashMap<String, List<MultiplePathNode>> visitedChemicals = new HashMap<String, List<MultiplePathNode>>();
  boolean verbose = false;
  private int counter = 0;

  public enum TargetLookupType {
    INCHI, SMILES, NAME, UUID
  }

  public enum Status {
    UNEXPANDED, EXPANDED, SOLVED
  }

  public AbstractSearch(MongoDBPaths mongoDB, HashMap<Integer, OperatorSet> categorizedOps) {
    this.DB = mongoDB;
    this.ops = categorizedOps;

    //if we're updating our indexes, let's get the set of reachables, make the chemical map, make the reactions map, then put them to a file
    if (indexUpdate) {
      makeUUIDToCarbonSkeletonMap();
      //populate chemicalMap and reactionMapList<MultiplePathNode
      addChemicalsToChemicalMap();
      addReactionsFromDBToReactionMap();
    }
    //else we're searching for a target, let's load in the chemical and reactions maps from file
    else {
      readInChemicalMap();
      readInReactionMap();
      String inchi_capsacin = "InChI=1S/C18H27NO3/c1-14(2)8-6-4-5-7-9-18(21)19-13-15-10-11-16(20)17(12-15)22-3/h6,8,10-12,14,20H,4-5,7,9,13H2,1-3H3,(H,19,21)/b8-6+";
      String inchi_acetyl_coa = "InChI=1S/C23H38N7O17P3S/c1-12(31)51-7-6-25-14(32)4-5-26-21(35)18(34)23(2,3)9-44-50(41,42)47-49(39,40)43-8-13-17(46-48(36,37)38)16(33)22(45-13)30-11-29-15-19(24)27-10-28-20(15)30/h10-11,13,16-18,22,33-34H,4-9H2,1-3H3,(H,25,32)(H,26,35)(H,39,40)(H,41,42)(H2,24,27,28)(H2,36,37,38)/t13-,16-,17-,18+,22-/m1/s1";
      String inchi_butanol = "InChI=1S/C4H10O/c1-2-3-4-5/h5H,2-4H2,1H3";
      String smiles_nylon_7 = "NCCCCCCC(=O)O";
      String inchi_nylon_7 = "InChI=1S/C7H15NO2/c8-6-4-2-1-3-5-7(9)10/h1-6,8H2,(H,9,10)";

      TargetLookupType lookupMethod = TargetLookupType.INCHI;
      String target_identifier = inchi_nylon_7;
      Long target_uuid = new Long(0);
      this.label = "nylon_7_render_complete";

      switch (lookupMethod) {
        case INCHI:
          this.target = targetFromInchi(target_identifier);
          break;
        case SMILES:
          this.target = targetFromSmiles(target_identifier);
          break;
        case UUID:
          this.target = targetFromUUID(target_uuid);
          break;
        case NAME:
          this.target = targetFromName(target_identifier);
          break;
      }
    }
  }

  //================================================================================
  // Identifying targets
  //================================================================================

  Chemical targetFromInchi(String inchi) {
    return this.DB.getChemicalFromInChI(inchi);
  }

  Chemical targetFromUUID(Long uuid) {
    return this.DB.getChemicalFromChemicalUUID(uuid);
  }

  Chemical targetFromName(String name) {
    Long id = this.DB.getChemicalIDFromName(name);
    return targetFromUUID(id);
  }

  Chemical targetFromSmiles(String smiles) {
    Indigo indigo = new Indigo();
    IndigoObject mol = indigo.loadMolecule(smiles);
    IndigoInchi ic = new IndigoInchi(indigo);
    String inchi = ic.getInchi(mol);
    return targetFromInchi(inchi);
  }

  //================================================================================
  // Making the map from UUIDs to chemicals
  //================================================================================

  private void makeUUIDToCarbonSkeletonMap() {
    List<Chemical> chemicals = this.DB.constructAllChemicalsFromActData("", null);

    for (Chemical c : chemicals) {
      Long id = c.getUuid();
      System.out.println("chemicalToUUID: " + id);
      String smiles = c.getSmiles();
      if (smiles != null) {
        CarbonSkeleton cs = new CarbonSkeleton(c);
        this.UUIDToCarbonSkeletonMap.put(id, cs);
      }
    }
  }

  //================================================================================
  // Getting set of reachables
  //================================================================================

  //loads all reachables from a text file that lists reachables
  private List<Chemical> getAllConcretelyReachable() {
    // TODO Auto-generated method stub
    // Paul needs to run a wavefront starting from this.metaboliteChems and accumulate all reachables.
    List<Chemical> concretelyReachable = PathBFS.getReachables(this.DB);
    return concretelyReachable;
  }

  //adds all reachables to a map from inchi strings to MultiplePathNodes that represent the chemical
  private void addRechablesToVisitedChemicals() {
    List<Chemical> reachables = getAllConcretelyReachable();
    for (Chemical c : reachables) {
      String inchi = c.getInChI();
      List<MultiplePathNode> ls = new ArrayList<MultiplePathNode>();
      ls.add(new MultiplePathNode());
      this.visitedChemicals.put(inchi, ls);
    }
  }

  //================================================================================
  // Map from carbon skeletons to chemicals
  //================================================================================

  public void addChemicalsToChemicalMap() {
    List<Chemical> reachables = PathBFS.getReachables(this.DB);
    for (Chemical c : reachables) {
      addChemicalToChemicalMap(c.getUuid());
    }
  }

  public void addChemicalToChemicalMap(Long uuid) {
    CarbonSkeleton skeleton = this.UUIDToCarbonSkeletonMap.get(uuid);
    if (skeleton == null) {
      return;
    }
    MoleculeEquivalenceClass equivClass = this.chemicalMap.get(skeleton.inchiString);
    if (equivClass == null) {
      equivClass = new MoleculeEquivalenceClass();
      this.chemicalMap.put(skeleton.inchiString, equivClass);
    }
    equivClass.addChemical(uuid);
  }

  public void outputChemicalMap() {
    try {
      FileWriter fstream = new FileWriter("chemicalMap.txt");
      BufferedWriter out = new BufferedWriter(fstream);
      for (Map.Entry<String, MoleculeEquivalenceClass> entry : this.chemicalMap.entrySet()) {
        String skeletonInchi = entry.getKey();
        MoleculeEquivalenceClass equivClass = entry.getValue();
        out.write(skeletonInchi + equivClass.uuidListString() + "\n");
      }
      out.close();
    } catch (Exception e) {
      System.err.println("Failed to write chemical map to file.");
    }
  }

  public void readInChemicalMap() {
    try {
      File file = new File("chemicalMap.txt");
      BufferedReader reader = null;
      reader = new BufferedReader(new FileReader(file));
      String text = null;
      while ((text = reader.readLine()) != null) {
        String[] items = text.split("\t");
        String carbonSkeletonInchi = items[0];
        String[] chemicalUUIDs = Arrays.copyOfRange(items, 1, items.length);
        MoleculeEquivalenceClass equivClass = new MoleculeEquivalenceClass();
        for (String s : chemicalUUIDs) {
          Long id = Long.parseLong(s);
          equivClass.addChemical(id);
        }
        this.chemicalMap.put(carbonSkeletonInchi, equivClass);
      }
    } catch (Exception e) {
      System.err.println("Failed to load chemical map from file.  Do you have a 'chemicalMap.txt' file?  Try running AbstractSearch with indexUpdate set to true.");
    }
  }

  public MoleculeEquivalenceClass getSameSkeletonChemicalsFromSkeleton(CarbonSkeleton skeleton) {
    return this.chemicalMap.get(skeleton.inchiString);
  }

  //================================================================================
  // Making the map from carbon skeletons to reactions
  //================================================================================

  //add all reactions.inchiString
  public void addReactionsFromDBToReactionMap() {
    DBIterator iterator = this.DB.getIteratorOverReactions(this.DB.getRangeUUIDRestriction(new Long(0), new Long(60000)), true /* notimeout=true */, null);
    Reaction r;

    // since we are iterating until the end, the getNextReaction call will close the DB cursor...
    while ((r = this.DB.getNextReaction(iterator)) != null) {
      Long luuid = (long) r.getUUID();
      System.out.println(luuid);

      // when debug list contains "-1" then debug_dump_uuid == null; and it means that we dump everything
      if (Configuration.getInstance().debug_dump_uuid == null
          || Configuration.getInstance().debug_dump_uuid.contains(r.getUUID()))
        BadRxns.logReaction(r, this.DB); // not necessarily a bad reaction, but one the user wants rendered
      // some reactions we have seen before and they are either bad data; or too difficult to analyze
      if (BadRxns.isBad(r, this.DB)) {
        continue;
      }
      addReaction(r);
    }
  }

  //add a single reaction, indexed on all the relevant carbon skeletons
  public void addReaction(Reaction r) {
    Long[] substrates = r.getSubstrates();
    Long[] products = r.getProducts();

    //create the list of chemicals that will produce carbon skeleton keys that should map to reaction r
    ArrayList<CarbonSkeleton> substrateSkeletons = new ArrayList<CarbonSkeleton>();
    ArrayList<CarbonSkeleton> productSkeletons = new ArrayList<CarbonSkeleton>();
    for (Long s : substrates) {
      CarbonSkeleton cs = this.UUIDToCarbonSkeletonMap.get(s);
      if (cs != null) {
        substrateSkeletons.add(cs);
      }
    }
    for (Long p : products) {
      CarbonSkeleton cs = this.UUIDToCarbonSkeletonMap.get(p);
      if (cs != null) {
        substrateSkeletons.add(cs);
      }
    }

    ArrayList<CarbonSkeleton> carbonSkeletons = new ArrayList<CarbonSkeleton>();
    //add in any substrate skeleton that doesn't appear in the products (don't want to index on unchanged skeletons)
    for (CarbonSkeleton skeleton : substrateSkeletons) {
      if (!productSkeletons.contains(skeleton)) {
        carbonSkeletons.add(skeleton);
      }
    }
    //add in any product skeleton that doesn't appear in the substrates (don't want to index on unchanged skeletons)
    for (CarbonSkeleton skeleton : productSkeletons) {
      if (!substrateSkeletons.contains(skeleton)) {
        carbonSkeletons.add(skeleton);
      }
    }

    for (CarbonSkeleton skeleton : carbonSkeletons) {
      addReactionIndexedOnSkeleton(r, skeleton);
    }
  }

  //add a single reaction on one of the relevant carbon skeletons
  public void addReactionIndexedOnSkeleton(Reaction reaction, CarbonSkeleton skeleton) {
    if (skeleton == null) {
      return;
    }
    ReactionEquivalenceClass equivClass = this.reactionMap.get(skeleton.inchiString);
    //if map currently lacks an entry for this carbon skeleton, create a new empty equivalence class for the skeleton
    if (equivClass == null) {
      equivClass = new ReactionEquivalenceClass();
      this.reactionMap.put(skeleton.inchiString, equivClass);
    }
    //add the reaction to the equivalence class, whether old or new
    equivClass.addReaction(new Long(reaction.getUUID()));
  }

  public void outputReactionMap() {
    try {
      FileWriter fstream = new FileWriter("reactionMap.txt");
      BufferedWriter out = new BufferedWriter(fstream);
      for (Map.Entry<String, ReactionEquivalenceClass> entry : this.reactionMap.entrySet()) {
        String skeletonInchi = entry.getKey();
        ReactionEquivalenceClass equivClass = entry.getValue();
        out.write(skeletonInchi + equivClass.uuidListString() + "\n");
      }
      out.close();
    } catch (Exception e) {
      System.err.println("Failed to write chemical map to file.");
    }
  }

  public void readInReactionMap() {
    try {
      File file = new File("reactionMap.txt");
      BufferedReader reader = null;
      reader = new BufferedReader(new FileReader(file));
      String text = null;
      while ((text = reader.readLine()) != null) {
        String[] items = text.split("\t");
        String carbonSkeletonInchi = items[0];
        String[] reactionUUIDs = Arrays.copyOfRange(items, 1, items.length);
        ReactionEquivalenceClass equivClass = new ReactionEquivalenceClass();
        for (String s : reactionUUIDs) {
          Long id = Long.parseLong(s);
          equivClass.addReaction(id);
        }
        this.reactionMap.put(carbonSkeletonInchi, equivClass);
      }
    } catch (Exception e) {
      System.err.println("Failed to load reaction map from file.  Do you have a 'reactionMap.txt' file?  Try running AbstractSearch with indexUpdate set to true.");
    }
  }

  //================================================================================
  // Finding a path to the target or targets
  //================================================================================

  private String inchiFromSmiles(String smiles) {
    Indigo indigo = new Indigo();
    IndigoObject molecule = indigo.loadMolecule(smiles);
    IndigoInchi ic = new IndigoInchi(indigo);
    String inchi = ic.getInchi(molecule);
    return inchi;
  }

  public void findPath(List<String> targetSMILES, List<String> targetCommonNames) {
    // TODO Auto-generated method stub

    // Look into new AugmentedReactionNetwork(mongoDB, augmentedNwName, categorizedOps, augmentWithROSteps);
    // for how we apply the operators....

    //if all we want to do is update indexes, we'll just write those to a file and return
    if (this.indexUpdate) {
      outputChemicalMap();
      outputReactionMap();
      return;
    }

    this.visited = new HashMap<String, Boolean>();

    //if not indexupdate, we want to actually find a target


    CarbonSkeleton cs = new CarbonSkeleton(this.target);
    cs.setSmilesID(this.counter);
    this.counter++;
    AbstractReactionsHypergraph hypergraph = new AbstractReactionsHypergraph();
    hypergraph.setIdType(IdType.ERO);
    AbstractReactionsHypergraph simpleHypergraph = new AbstractReactionsHypergraph();
    simpleHypergraph.setIdType(IdType.ERO);
    List<MultiplePathNode> p = EROPath(this.target.getInChI(), hypergraph, simpleHypergraph);
    System.out.println(p);
    try {
      hypergraph.writeDOT(this.label, "reached_network.dot", this.DB, true);
      simpleHypergraph.writeDOT(this.label, "reached_network_simple.dot", this.DB, true);
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
    }
    if (p != null) {
      MultiplePathNode root = new MultiplePathNode(this.target);
      root.addChild(p);
      root.visualize(this.label, this.DB);
      root.render(this.label, this.DB);
    }
  }

  private void testROApplications() {
    String i1 = "InChI=1S/C12H21N3O3/c13-10(7-16)1-4-14-11(8-17)2-5-15-6-3-12(15)9-18/h7-12,14H,1-6,13H2";
    String i2 = "InChI=1S/C12H21N3O4/c13-11(12(18)19)1-4-14-9(7-16)2-5-15-6-3-10(15)8-17/h7-11,14H,1-6,13H2,(H,18,19)/t9?,10?,11-/m0/s1";

    String i3 = "InChI=1S/C26H46N8O8S2/c27-18(15-36)6-7-22(38)34-20(17-44)26(42)32-14-24(40)30-11-4-9-28-8-1-2-10-29-23(39)13-31-25(41)19(16-43)33-21(37)5-3-12-35/h12,15,18-20,28,43-44H,1-11,13-14,16-17,27H2,(H,29,39)(H,30,40)(H,31,41)(H,32,42)(H,33,37)(H,34,38)/t18?,19-,20-/m0/s1";
    String i4 = "InChI=1S/C27H46N8O10S2/c28-17(14-36)4-6-21(38)34-18(15-46)25(42)33-13-24(41)31-11-3-9-29-8-1-2-10-30-23(40)12-32-26(43)19(16-47)35-22(39)7-5-20(37)27(44)45/h14,17-19,29,46-47H,1-13,15-16,28H2,(H,30,40)(H,31,41)(H,32,43)(H,33,42)(H,34,38)(H,35,39)(H,44,45)/t17?,18-,19-/m0/s1";

    String i5 = "InChI=1S/C10H16N2O4/c11-8(6-14)3-4-10(16)12-9(7-15)2-1-5-13/h5-9H,1-4,11H2,(H,12,16)";
    String i6 = "InChI=1S/C10H16N2O5/c11-7(5-13)1-3-9(15)12-8(6-14)2-4-10(16)17/h5-8H,1-4,11H2,(H,12,15)(H,16,17)";

    String i7 = "InChI=1S/C10H16N2O4/c11-9(4-2-6-14)10(16)12-8(7-15)3-1-5-13/h5-9H,1-4,11H2,(H,12,16)/t8?,9-/m1/s1";
    String i8 = "InChI=1S/C10H16N2O5/c11-8(2-1-5-13)10(17)12-7(6-14)3-4-9(15)16/h5-8H,1-4,11H2,(H,12,17)(H,15,16)/t7?,8-/m1/s1";

    String i9 = "InChI=1S/C37H61N7O18/c1-16(11-45)39-35(57)23(8-6-7-21(38)12-46)44-26(52)10-9-22(13-47)43-33(55)17(2)40-34(56)18(3)59-32-28(42-20(5)51)36(58)60-25(15-49)31(32)62-37-27(41-19(4)50)30(54)29(53)24(14-48)61-37/h11-13,16-18,21-25,27-32,36-37,48-49,53-54,58H,6-10,14-15,38H2,1-5H3,(H,39,57)(H,40,56)(H,41,50)(H,42,51)(H,43,55)(H,44,52)/t16?,17?,18?,21?,22?,23?,24-,25-,27-,28-,29-,30-,31-,32-,36-,37+/m1/s1";
    String i10 = "InChI=1S/C37H61N7O19/c1-15(32(54)43-21(12-46)9-10-25(51)44-22(8-6-7-20(38)11-45)34(56)40-16(2)35(57)58)39-33(55)17(3)60-31-27(42-19(5)50)36(59)61-24(14-48)30(31)63-37-26(41-18(4)49)29(53)28(52)23(13-47)62-37/h11-12,15-17,20-24,26-31,36-37,47-48,52-53,59H,6-10,13-14,38H2,1-5H3,(H,39,55)(H,40,56)(H,41,49)(H,42,50)(H,43,54)(H,44,51)(H,57,58)/t15?,16?,17?,20?,21?,22?,23-,24-,26-,27-,28-,29-,30-,31-,36-,37+/m1/s1";

    String i11 = "InChI=1S/C10H18N2O3/c11-5-1-3-9(7-14)12-10(8-15)4-2-6-13/h6-10,12H,1-5,11H2";
    String i12 = "InChI=1S/C10H18N2O4/c11-5-1-2-8(6-13)12-9(7-14)3-4-10(15)16/h6-9,12H,1-5,11H2,(H,15,16)";

    String i13 = "InChI=1S/C9H15NO3/c1-7(2)8(6-12)10-9(13)4-3-5-11/h5-8H,3-4H2,1-2H3,(H,10,13)";
    String i14 = "InChI=1S/C10H15NO5/c1-6(2)7(5-12)11-9(14)4-3-8(13)10(15)16/h5-7H,3-4H2,1-2H3,(H,11,14)(H,15,16)";

    String i15 = "InChI=1S/C10H18N4O3/c11-8(6-16)2-1-4-13-10(12)14-9(7-17)3-5-15/h5-9H,1-4,11H2,(H3,12,13,14)";
    String i16 = "InChI=1S/C10H18N4O4/c11-7(5-15)2-1-3-13-10(12)14-8(6-16)4-9(17)18/h5-8H,1-4,11H2,(H,17,18)(H3,12,13,14)";

    printApplyROs(i1);
    printApplyROs(i3);
    printApplyROs(i5);
    printApplyROs(i7);
    printApplyROs(i9);
    printApplyROs(i11);
    printApplyROs(i13);
    printApplyROs(i15);

    printApplyROs(i2);
    printApplyROs(i4);
    printApplyROs(i6);
    printApplyROs(i8);
    printApplyROs(i10);
    printApplyROs(i12);
    printApplyROs(i14);
    printApplyROs(i16);
  }

  private void printApplyROs(String inchi) {
    System.out.println("-----------------");
    System.out.println("Expanding: " + inchi);
    ActAdminServiceImpl aasi = new ActAdminServiceImpl(false);
    List<ROApplication> roApplications = aasi.applyROs(inchi, this.ops);
    for (ROApplication application : roApplications) {
      for (String product : application.products) {
        System.out.println("Product  : " + product);
        System.out.println("This product from RO: " + application.ro.rxn());
      }
    }
    System.out.println("-----------------");
  }

  private class Subtree {
    ROApplication roapplication;
    List<String> unsolvedInchis;

    public Subtree(ROApplication roapplication, List<String> unsolvedInchis) {
      this.roapplication = roapplication;
      this.unsolvedInchis = unsolvedInchis;
    }

  }

  public List<MultiplePathNode> EROPath(String targetInchi, AbstractReactionsHypergraph hypergraph, AbstractReactionsHypergraph simpleHypergraph) {
    HashMap<String, Status> inchiStatus = new HashMap<String, Status>();
    HashMap<String, List<Subtree>> breakdownOptions = new HashMap<String, List<Subtree>>();
    HashMap<String, List<MultiplePathNode>> solvedChemicals = new HashMap<String, List<MultiplePathNode>>();
    HashMap<String, List<String>> parentPointers = new HashMap<String, List<String>>();

    System.out.println("Target: " + targetInchi);

    //populate solvedChemicals with all the reachables, since those are automatically solved
    List<Chemical> reachables = getAllConcretelyReachable();
    for (Chemical c : reachables) {
      String inchi = c.getInChI();
      List<MultiplePathNode> ls = new ArrayList<MultiplePathNode>();
      ls.add(new MultiplePathNode(inchi));
      solvedChemicals.put(inchi, ls);
    }

    List<String> frontier = new ArrayList<String>(); //the only things we'll add to the frontier are unexpanded things

    //let's shortcircuit this process if the targetInchi is in the list of reachables, since only unexpanded items must go in frontier
    if (solvedChemicals.containsKey(targetInchi)) {
      System.out.println("Our target was a reachable in the first place.");
      List<MultiplePathNode> targetNode = new ArrayList<MultiplePathNode>();
      targetNode.add(new MultiplePathNode(targetFromInchi(targetInchi)));
      return targetNode;
    }

    //add our target to our list of chemicals to explore
    frontier.add(targetInchi);

    while (frontier.size() > 0) {

      String currentInchi = selectFromFrontier(frontier);
      Indigo indigo = new Indigo();
      IndigoInchi ic = new IndigoInchi(indigo);
      String currentSmiles;
      try {
        String consistent = CommandLineRun.consistentInChI(currentInchi, "AbstractSearch");
        currentSmiles = ic.loadMolecule(consistent).canonicalSmiles();
      } catch (Exception e) {
        currentSmiles = currentInchi;
      }

      System.out.println("****************************");
      System.out.println("Selecting a new item from frontier.  Frontier size: " + frontier.size());
      System.out.println("Applying ROs to " + currentInchi);
      System.out.println("****************************");

      breakdownOptions.put(currentInchi, new ArrayList<Subtree>());
      //the currentinchi is unexpanded, so let's expand it.
      ActAdminServiceImpl aasi = new ActAdminServiceImpl(false);
      List<ROApplication> roApplications = aasi.applyROs(currentSmiles, this.ops);
      inchiStatus.put(currentInchi, Status.EXPANDED);
      // TODO: in future, don't want to be directly adding to frontier in case one breakdown succeeds and we no longer need the items added before
      //same goes for the status and all
      int counter = 0;
      System.out.println("Expanding: " + currentInchi);
      System.out.println("RO applications: " + roApplications.size());
      for (ROApplication application : roApplications) {
        List<String> breakdownInchis = new ArrayList<String>();

        //add to our network of seen chemicals
        String reactionString = application.ro.rxn();
        List<String> reactionStringList = new ArrayList<String>();
        reactionStringList.add(reactionString);
        hypergraph.addReactions(currentInchi, reactionStringList);
        hypergraph.addReactants(reactionString, application.products);

        counter++;
        for (String inchi : application.products) {
          System.out.println("Product: " + inchi);
          //only include the inchi if we haven't already found a path to it
          if (solvedChemicals.containsKey(inchi)) {
            System.out.println("Already solved this chemical, or it's a reachable");
            continue;
          }
          if (!inchiStatus.containsKey(inchi)) {
            System.out.println("Found a new unexpanded chemical to add to the frontier.");
            //this must be new!  let's make a new entry for it in our hashmaps
            inchiStatus.put(inchi, Status.UNEXPANDED);
            parentPointers.put(inchi, new ArrayList<String>());
            //since this is the first time we're seeing it, we also want to put it in our frontier
            frontier.add(inchi);
            //since this is the first time we're seeing it, it should also go in our simplified network
            List<String> ls = new ArrayList<String>();
            ls.add(inchi);
            simpleHypergraph.addReactions(currentInchi, reactionStringList);
            simpleHypergraph.addReactants(reactionString, ls);
          } else {
            System.out.println("We've already seen this inchi.  UNEXPANDED or EXPANDED, it's in the frontier.");
          }
          //now we want to add this inchi to our list of inchis for this breakdown
          breakdownInchis.add(inchi);
          //and we want to update the inchis that rely on this inchi to include currentInchi
          List<String> ls = parentPointers.get(inchi);
          if (ls != null && !ls.contains(currentInchi)) { //ls will be null if this inchi is a solvedchemical already
            ls.add(currentInchi);
          }
        }
        //now we've gotten through one of our breakdowns
        //if there are items in the breakdownInchis list, we haven't broken currentInchi down into reachables so nothing happens
        //but if breakdownInchis is empty, we've gotten our item down to reachables and we have some updating to do
        if (breakdownInchis.size() == 0) {
          //foundinchi will make a multiplepathnode for currentinchi, remove all instances of currentinchi in breakdownoptions
          //and deal with the cascading effects
          System.out.println("We've found an empty one.");
          System.out.println(breakdownInchis);
          System.out.println(breakdownInchis.size());
          foundInchi(currentInchi, application, breakdownOptions, solvedChemicals, parentPointers);
          //the times when we solve an inchi are the times when might have solved the target, so let's check
          if (solvedChemicals.containsKey(targetInchi)) {
            return solvedChemicals.get(targetInchi);
          }
          //since we solved currentinchi, ready to continue to the next round of the while loop
          continue;
        }
        //there are items in the breakdowninchis list, so just add to the set of breakdowns
        breakdownOptions.get(currentInchi).add(new Subtree(application, breakdownInchis));
      }
    }
    //we ran out of unexpanded chemicals to expand
    return null;
  }

  public String selectFromFrontier(List<String> frontier) {
    return frontier.remove(0);
  }

  public void foundInchi(String inchi, ROApplication application, HashMap<String, List<Subtree>> breakdownOptions, HashMap<String, List<MultiplePathNode>> solvedChemicals, HashMap<String, List<String>> parentPointers) {
    System.out.println("--------------");
    System.out.println("New multiple path node for roid: " + application.roid);
    MultiplePathNode reactionNode = new MultiplePathNode(application.roid);
    reactionNode.setDisplayStrings(inchi, application.ro.rxn(), application.products);
    reactionNode.eroNode();
    for (String product_inchi : application.products) {
      //solvedChemicals must contain product_inchi, or we wouldn't have concluded that it's reachable
      reactionNode.addChild(solvedChemicals.get(product_inchi));
      System.out.println("Adding a child node for roid " + application.roid + " with inchi: " + product_inchi);
    }
    List<MultiplePathNode> ls = new ArrayList<MultiplePathNode>();
    ls.add(reactionNode);
    solvedChemicals.put(inchi, ls);
    System.out.println("Using roid node with id " + application.roid + " to find inchi: " + inchi);

    List<String> affectedInchis = parentPointers.get(inchi);
    if (affectedInchis == null) {
      return;
    }
    for (String affectedInchi : affectedInchis) {
      List<Subtree> subtreeList = breakdownOptions.get(affectedInchi);
      for (Subtree subtree : subtreeList) {
        //subtree now represents one possible breakdown for affectedInchi
        if (subtree.unsolvedInchis.contains(inchi)) {
          subtree.unsolvedInchis.remove(inchi);
          //the list of inchis still to be resolved could now be empty, so we'd better check
          if (subtree.unsolvedInchis.size() == 0) {
            //yay, we've solved this one too!
            System.out.println("Removing inchi empties another chemical's things to solve.");
            foundInchi(affectedInchi, subtree.roapplication, breakdownOptions, solvedChemicals, parentPointers);
          }
        }
      }
    }
  }

  public PathNode findPathOneTarget(CarbonSkeleton cs) {
    String csInchi = cs.inchiString;
    if (verbose) {
      System.out.println("new branch");
      System.out.println(cs.smiles);
      System.out.println(cs.inchi);
    }
    if (this.visited.get(csInchi) != null) {
      return null;
    }
    this.visited.put(csInchi, true);
    MoleculeEquivalenceClass molEquivClass = this.chemicalMap.get(csInchi);
    if (molEquivClass != null) {
      //our chemical is in the set of reachables, so we're done
      if (this.verbose) {
        System.out.println("This molecule's skeleton is a reachable!");
        System.out.println("Skeleton: " + cs.smiles.toString());
      }
      return new PathNode(molEquivClass);
    }
    //if we're here, our chemical isn't in the set of reachReactionables.  must try to break it down
    ReactionEquivalenceClass rEquivClass = this.reactionMap.get(cs.inchiString);
    if (rEquivClass == null) {
      //we don't know how to break this molecule down, so we have to give up on this branch
      if (this.verbose) {
        System.out.println("Skeleton: " + cs.smiles.toString());
        System.out.println("No reactions involve chemicals with this molecule's carbon skeleton");
        return null;
      }
    }
    //if we're here, we have a set of reactions we can use to try to b.inchiStringreak down our molecule
    ArrayList<Long> reactions = rEquivClass.getMatchingReactions();
    //let's see if any of our reactions succeed
    for (Long reactionID : reactions) {
      //first let's see whether the matching carbon skeleton was a substrate or product.
      //if substrate, must be able to find paths to all products
      //if product, must be able to find paths to all substrates
      Reaction reaction = this.DB.getReactionFromUUID(reactionID);
      Long[] productIDs = reaction.getProducts();
      Long[] chemicalIDs = productIDs;
      for (Long productID : productIDs) {
        Chemical product = this.DB.getChemicalFromChemicalUUID(productID);
        CarbonSkeleton productCS = new CarbonSkeleton(product);
        if (cs.equals(productCS)) {
          chemicalIDs = reaction.getSubstrates();
          break;
        }
      }
      //we now have a list of the ids of chemicals to which we'd have to find pathways
      PathNode pn = new PathNode(reaction);
      boolean reactionFail = false;
      for (Long chemicalID : chemicalIDs) {
        Chemical chemToFind = this.DB.getChemicalFromChemicalUUID(chemicalID);
        PathNode pnChild = this.findPathOneTarget(new CarbonSkeleton(chemToFind));
        if (pnChild == null) {
          //if we fail to find one child, this reaction won't work
          //we should stop checking the chemicals for this reaction
          //and go on to the next reaction
          reactionFail = true;
          break;
        }
        //if we didn't fail, add this path as a child to pn
        pn.addChild(pnChild);
      }
      //if we went through the whole loop and found a path to every chemical we needed
      //this reaction was successful, and we can return the path
      if (!reactionFail) {
        if (this.verbose) {
          System.out.println("Skeleton: " + cs.smiles.toString());
          System.out.println("This chemical can be broken down with the reaction below:");
          System.out.println(reaction);
        }
        return pn;
      }
    }
    //if none of the reactions worked, return null
    if (this.verbose) {
      System.out.println("Skeleton: " + cs.smiles.toString());
      System.out.println("We found reactions for this molecule's carbon skeleton, but none worked for us.");
    }
    return null;
  }

  public void sanityCheck() {
    HashSet<String> solvedChemicals = new HashSet<String>();

    //populate solvedChemicals with all the reachables, since those are automatically solved
    List<Chemical> reachables = getAllConcretelyReachable();
    for (Chemical c : reachables) {
      String c_inchi = c.getInChI();
      solvedChemicals.add(c_inchi);
    }


    int counter = 0;
    for (Chemical c : reachables.subList(2000, reachables.size() - 1)) {
      Pair<String, Integer> picked = null;
      counter++;
      String inchi = c.getInChI();
      HashSet<String> solvedChemicalsForInchi = new HashSet<String>();
      solvedChemicalsForInchi.add(inchi);

      List<Pair<String, Integer>> frontier = new ArrayList<Pair<String, Integer>>();
      HashMap<String, String> parents = new HashMap<String, String>();
      frontier.add(Pair.create(inchi, 0));
      parents.put(inchi, null);

      while (frontier.size() > 0) {
        Pair<String, Integer> curr = frontier.remove(frontier.size() - 1);
        if (curr.right == 3) {
          picked = curr;
          break;
        }
        ActAdminServiceImpl aasi = new ActAdminServiceImpl(false);
        List<ROApplication> roApplications = aasi.applyROs(curr.left, this.ops);
        for (ROApplication application : roApplications) {
          for (String product : application.products) {
            if (!solvedChemicals.contains(product) && !solvedChemicalsForInchi.contains(product)) {
              solvedChemicalsForInchi.add(product);
              frontier.add(Pair.create(product, curr.right + 1));
              parents.put(product, curr.left);
            }
          }
        }
      }
      if (picked == null) {
      } else {
        System.out.println("Found forward working inchi: " + inchi);
        System.out.println("======================= Unreached");
        String curr = picked.left;
        while (curr != null) {
          System.out.println(curr);
          curr = parents.get(curr);
        }
        System.out.println("======================= Reachable");

        //search for the picked item
        AbstractReactionsHypergraph hypergraph = new AbstractReactionsHypergraph();
        hypergraph.setIdType(IdType.ERO);
        AbstractReactionsHypergraph simpleHypergraph = new AbstractReactionsHypergraph();
        simpleHypergraph.setIdType(IdType.ERO);
        List<MultiplePathNode> p = EROPath(picked.left, hypergraph, simpleHypergraph);
        System.out.println(p);
        try {
          hypergraph.writeDOT("sanity_check", "reached_network.dot", this.DB, true);
          simpleHypergraph.writeDOT("sanity_check", "reached_network_simple.dot", this.DB, true);
        } catch (Exception e) {
          System.out.println(e);
          e.printStackTrace();
        }
        if (p != null) {
          MultiplePathNode root = new MultiplePathNode(this.target);
          root.addChild(p);
          root.visualize("sanity_check", this.DB);
          root.render("sanity_check", this.DB);
          System.out.println("Looks like we were able to find a path.  Sanity check is good.");
          return;
        }
      }
    }

  }

  public PathNode findPathOneTargetBreakSkeletons(CarbonSkeleton cs) {
    String csInchi = cs.inchiString;
    if (verbose) {
      System.out.println("new branch");
      System.out.println(cs.smiles);
      System.out.println(cs.inchi);
    }
    if (this.visited.get(csInchi) != null) {
      return null;
    }
    this.visited.put(csInchi, true);
    MoleculeEquivalenceClass molEquivClass = this.chemicalMap.get(csInchi);
    if (molEquivClass != null) {
      //our chemical is in the set of reachables, so we're done
      if (this.verbose) {
        System.out.println("This molecule's skeleton is a reachable!");
        System.out.println("Skeleton: " + cs.smiles.toString());
      }
      return new PathNode(molEquivClass);
    }
    //if we couldn't find the whole skeleton, let's try breaking it into pieces.
    //but only if we have more than one component to try
    if (cs.smiles.size() > 1) {
      boolean breakingFailed = false;
      PathNode pnParent = new PathNode();
      for (String smiles : cs.smiles) {
        PathNode pnSub = findPathOneTargetBreakSkeletons(new CarbonSkeleton(smiles));
        if (pnSub == null) {
          breakingFailed = true;
          break;
        }
        pnParent.addChild(pnSub);
      }
      if (!breakingFailed) {
        return pnParent;
      }
    }

    //if we're here, our chemical isn't in the set of reachReactionables.  must try to break it down
    ReactionEquivalenceClass rEquivClass = this.reactionMap.get(cs.inchiString);
    if (rEquivClass == null) {
      //we don't know how to break this molecule down, so we have to give up on this branch
      if (this.verbose) {
        System.out.println("Skeleton: " + cs.smiles.toString());
        System.out.println("No reactions involve chemicals with this molecule's carbon skeleton");
        return null;
      }
    }
    //if we're here, we have a set of reactions we can use to try to b.inchiStringreak down our molecule
    ArrayList<Long> reactions = rEquivClass.getMatchingReactions();
    //let's see if any of our reactions succeed
    for (Long reactionID : reactions) {
      //first let's see whether the matching carbon skeleton was a substrate or product.
      //if substrate, must be able to find paths to all products
      //if product, must be able to find paths to all substrates
      Reaction reaction = this.DB.getReactionFromUUID(reactionID);
      Long[] productIDs = reaction.getProducts();
      Long[] chemicalIDs = productIDs;
      for (Long productID : productIDs) {
        Chemical product = this.DB.getChemicalFromChemicalUUID(productID);
        CarbonSkeleton productCS = new CarbonSkeleton(product);
        if (cs.equals(productCS)) {
          chemicalIDs = reaction.getSubstrates();
          break;
        }
      }
      //we now have a list of the ids of chemicals to which we'd have to find pathways
      PathNode pn = new PathNode(reaction);
      boolean reactionFail = false;
      for (Long chemicalID : chemicalIDs) {
        Chemical chemToFind = this.DB.getChemicalFromChemicalUUID(chemicalID);
        PathNode pnChild = this.findPathOneTargetBreakSkeletons(new CarbonSkeleton(chemToFind));
        if (pnChild == null) {
          //if we fail to find one child, this reaction won't work
          //we should stop checking the chemicals for this reaction
          //and go on to the next reaction
          reactionFail = true;
          break;
        }
        //if we didn't fail, add this path as a child to pn
        pn.addChild(pnChild);
      }
      //if we went through the whole loop and found a path to every chemical we needed
      //this reaction was successful, and we can return the path
      if (!reactionFail) {
        if (this.verbose) {
          System.out.println("Skeleton: " + cs.smiles.toString());
          System.out.println("This chemical can be broken down with the reaction below:");
          System.out.println(reaction);
        }
        return pn;
      }
    }
    //if none of the reactions worked, return null
    if (this.verbose) {
      System.out.println("Skeleton: " + cs.smiles.toString());
      System.out.println("We found reactions for this molecule's carbon skeleton, but none worked for us.");
    }
    return null;
  }

  public List<MultiplePathNode> findAllPathsOneTargetBreakSkeletons(CarbonSkeleton cs) {
    String csInchi = cs.inchiString;
    if (verbose) {
      System.out.println("new branch");
      System.out.println(cs.smiles);
      System.out.println(cs.inchi);
    }
    //visitedNodes will contain the key if we've seen this csInchi elsewhere before
    if (this.visitedNodes.containsKey(csInchi)) {
      //visitedNodes will contain null for csInchi if csInchi is a parent of the current node (don't start a cycle!)
      //or if we've already discovered that we don't know how to break csInchi down into reachables
      //in either case, we don't care to continue on this path, and we should return null
      if (this.visitedNodes.get(csInchi) == null) {
        return null;
      }
      //if it wasn't null, we already know how to get this carbon skeleton, so go ahead and return the PathNode
      else {
        return this.visitedNodes.get(csInchi);
      }
    }
    this.visitedNodes.put(csInchi, null);
    ArrayList<MultiplePathNode> pnList = new ArrayList<MultiplePathNode>();
    MoleculeEquivalenceClass molEquivClass = this.chemicalMap.get(csInchi);
    if (molEquivClass != null) {
      //our chemical is in the set of reachables, so we're done
      if (this.verbose) {
        System.out.println("This molecule's skeleton is a reachable!");
        System.out.println("Skeleton: " + cs.smiles.toString());
      }
      //we've reached a leaf
      pnList.add(new MultiplePathNode(molEquivClass));
      this.visitedNodes.put(csInchi, pnList);
      return pnList;
    }
    //if we couldn't find the whole skeleton, let's try breaking it into pieces.
    //but only if we have more than one component to try
    if (cs.smiles.size() > 1) {
      boolean breakingFailed = false;
      //we might like to add that this is glue, and have the reactants be the broken up carbon skeletons
      //but how do we represent the set of carbon skeletons?
      List<CarbonSkeleton> skeletons = new ArrayList<CarbonSkeleton>();
      List<String> smilesIDs = new ArrayList<String>();
      for (String smiles : cs.smiles) {
        CarbonSkeleton newSkeleton = new CarbonSkeleton(smiles);
        newSkeleton.setSmilesID(this.counter);
        this.counter++;
        skeletons.add(newSkeleton);
        smilesIDs.add(newSkeleton.smilesID);
      }
      MultiplePathNode pnParent = new MultiplePathNode();
      pnParent.setDisplayStrings(cs.smilesID, "glue", smilesIDs);
      pnParent.carbonSkeletonNode();
      for (CarbonSkeleton childSkeleton : skeletons) {
        List<MultiplePathNode> pnSub = findAllPathsOneTargetBreakSkeletons(childSkeleton);
        if (pnSub == null) {
          breakingFailed = true;
          break;
        }
        pnParent.addChild(pnSub);
      }
      if (!breakingFailed) {
        pnList.add(pnParent);
        this.visitedNodes.put(csInchi, pnList);
        return pnList;
      }
    }

    //if we're here, our chemical isn't in the set of reachReactionables.  must try to break it down
    ReactionEquivalenceClass rEquivClass = this.reactionMap.get(cs.inchiString);
    if (rEquivClass == null) {
      //we don't know how to break this molecule down, so we have to give up on this branch
      if (this.verbose) {
        System.out.println("Skeleton: " + cs.smiles.toString());
        System.out.println("No reactions involve chemicals with this molecule's carbon skeleton");
        return null;
      }
    }
    //if we're here, we have a set of reactions we can use to try to break down our molecule
    ArrayList<Long> reactions = rEquivClass.getMatchingReactions();
    System.out.println("Number of applicable reactions: " + reactions.size());
    //let's see if any of our reactions succeed
    for (Long reactionID : reactions) {
      //first let's see whether the matching carbon skeleton was a substrate or product.
      //if substrate, must be able to find paths to all products
      //if product, must be able to find paths to all substrates
      Reaction reaction = this.DB.getReactionFromUUID(reactionID);
      Long[] productIDs = reaction.getProducts();
      Long[] chemicalIDs = productIDs;
      for (Long productID : productIDs) {
        Chemical product = this.DB.getChemicalFromChemicalUUID(productID);
        CarbonSkeleton productCS = new CarbonSkeleton(product);
        if (cs.equals(productCS)) {
          chemicalIDs = reaction.getSubstrates();
          break;
        }
      }
      //we now have a list of the ids of chemicals to which we'd have to find pathways
      List<CarbonSkeleton> chemicalSkeletons = new ArrayList<CarbonSkeleton>();
      List<String> smilesIDs = new ArrayList<String>();
      for (Long chemicalID : chemicalIDs) {
        Chemical chemToFind = this.DB.getChemicalFromChemicalUUID(chemicalID);
        CarbonSkeleton skeletonToFind = new CarbonSkeleton(chemToFind);
        skeletonToFind.setSmilesID(this.counter);
        this.counter++;
        chemicalSkeletons.add(skeletonToFind);
        smilesIDs.add(skeletonToFind.smilesID);
      }
      MultiplePathNode pn = new MultiplePathNode(reaction);
      pn.setDisplayStrings(cs.smilesID, reaction.getUUID() + "", smilesIDs);
      pn.carbonSkeletonNode();
      boolean reactionFail = false;
      for (CarbonSkeleton newCS : chemicalSkeletons) {
        List<MultiplePathNode> pnChild = this.findAllPathsOneTargetBreakSkeletons(newCS);
        if (pnChild == null) {
          //if we fail to find one child, this reaction won't work
          //we should stop checking the chemicals for this reaction
          //and go on to the next reaction
          reactionFail = true;
          break;
        }
        //if we didn't fail, add this path as a child to pn
        pn.addChild(pnChild);
      }
      //if we went through the whole loop and found a path to every chemical we needed
      //this reaction was successful, and we can add the path to our set of paths
      if (!reactionFail) {
        if (this.verbose) {
          System.out.println("Skeleton: " + cs.smiles.toString());
          System.out.println("This chemical can be broken down with the reaction below:");
          System.out.println(reaction);
        }
        pnList.add(pn);
      }
    }
    if (this.verbose) {
      System.out.println("Skeleton: " + cs.smiles.toString());
      System.out.println("We found reactions for this molecule's carbon skeleton, but none worked for us.");
    }

    //if none of the reactions worked, return null
    if (pnList.size() == 0) {
      System.out.println("None of our reactions worked.");
      return null;
    } else {
      System.out.println("Number of applicable reactions that we found full paths for: " + pnList.size());
      this.visitedNodes.put(csInchi, pnList);
      return pnList;
    }
  }

}
