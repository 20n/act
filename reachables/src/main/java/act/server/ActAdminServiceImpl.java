package act.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.render.RenderPathways;

import act.client.ActAdminService;
import act.server.VariousSearchers.Strategy;
import act.server.AbstractPath.AbstractPath;
import act.server.AbstractSearch.AbstractSearch;
import act.server.EnumPath.Enumerator;
import act.server.EnumPath.OperatorSet;
import act.server.EnumPath.PathToNativeMetabolites;
import act.server.EnumPath.OperatorSet.OpID;
import act.server.FnGrpDomain.FnGrpDomainSearch;
import act.server.GameServer.PathwayGameServer;
import act.server.Molecules.CRO;
import act.server.Molecules.DotNotation;
import act.server.Molecules.NROClasses;
import act.server.Molecules.RO;
import act.server.Molecules.ReactionDiff;
import act.server.Molecules.SMILES;
import act.server.Molecules.TheoryROClasses;
import act.server.Molecules.RxnTx;
import act.server.SQLInterface.MongoDB;
import act.server.SQLInterface.MongoDBPaths;
import act.server.SQLInterface.MysqlDB;
import act.server.Search.SimpleConcretePath;
import act.shared.AugmentedReactionNetwork;
import act.shared.Chemical;
import act.shared.Configuration;
import act.shared.Organism;
import act.shared.Parameters;
import act.shared.Path;
import act.shared.ROApplication;
import act.shared.Reaction;
import act.shared.ReactionDetailed;
import act.shared.helpers.P;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoException;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * The server side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class ActAdminServiceImpl extends RemoteServiceServlet implements
    ActAdminService {
  private PathwayGameServer gameServer;

  public ActAdminServiceImpl() {
    super();
    startGameServer();
  }

  public ActAdminServiceImpl(boolean dontStartGameServer) {
    super();
  }

  public void startGameServer() {
    Configuration config = serverInitConfig(null, false);
    if (gameServer == null) {

      gameServer = new PathwayGameServer(new MongoDBPaths(config.actHost, config.actPort, config.actDB), 8080);
      new Thread( new Runnable() {
        @Override
        public void run() {
          gameServer.startServer();
        }
      }).start();
    }
  }

  public List<Path> getCommonPaths(int k) {
    SimpleConcretePath scp = new SimpleConcretePath();
    //scp.findSimplePaths(Long.parseLong("8024"), Long.parseLong("10779"),20,6);
    return scp.getDeadEnds();
  }

  @Override
  public Configuration serverInitConfig(String configfile, boolean quiet) {
    new ConfigurationReader(configfile, quiet); // init the server's configuration; read config.xml and populate shared.Configuration
    Logger.setMaxImpToShow(Configuration.getInstance().logLevel);

    return Configuration.getInstance();
  }

  // This function will be used by all server side function to initiate connection to the backend DB
  private static MongoDB createActConnection(String mongoActHost, int mongoActPort, String mongoActDB) {
    MongoDB db;
    // connect to Mongo database
    if (mongoActHost == null) {
      // this means that right now we are in simulation mode and only want to write to screen
      // as opposed to write to the actual database. So we System.out.println everything
      db = null;
    } else {
      db = new MongoDB( mongoActHost, mongoActPort, mongoActDB );
    }

    return db;
  }

  @Override
  public String dumpAct2File(String mongoActHost, int mongoActPort, String mongoActDB,
      String dumpFile) throws IllegalArgumentException {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    mongoDB.dumpActToFile(dumpFile);
    return "Mongo actfamilies collection written to " + dumpFile;
  }

  @Override
  public String diffReactions(String mongoActHost, int mongoActPort, String mongoActDB,
      Long lowUUID, Long highUUID, String rxns_list_file, boolean addToDB) throws IllegalArgumentException {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    ReactionDiff diff = new ReactionDiff(mongoDB);
    List<Long> rxns_whitelist = rxns_list_file == null ? null : getKnownGoodRxns(rxns_list_file);
    TheoryROClasses classes = diff.processAll(lowUUID, highUUID, rxns_whitelist, addToDB);

    boolean doNROs = false;
    if (doNROs) {
      NROClasses nros = new NROClasses(mongoDB, classes);
    }
    return "Diff'ed all families";
  }

  private List<Long> getKnownGoodRxns(String rxns_list_file) {
    List<Long> whitelist = new ArrayList<Long>();
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(rxns_list_file))));
      String strLine;
      while ((strLine = br.readLine()) != null)   {
        whitelist.add(Long.parseLong(strLine));
      }
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("Whitelist: " + whitelist);
    return whitelist;
  }
  public void checkCofactors(String mongoActHost, int mongoActPort, String mongoActDB,
      Long lowUUID, Long highUUID) {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    ReactionDiff diff = new ReactionDiff(mongoDB);
    diff.findCofactorPairs(lowUUID, highUUID);
  }

  public void computeAAMsForCofactorPairs(String mongoActHost, int mongoActPort, String mongoActDB, String cofactor_pair_file) {
      System.out.println("reading cofactor pairs");
      List<Long[]> cofactors_left, cofactors_right;
      cofactors_left = new ArrayList<Long[]>();
      cofactors_right = new ArrayList<Long[]>();
      try {
        BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(cofactor_pair_file))));
        String strLine;
        while ((strLine = br.readLine()) != null)   {
          String[] tokens = strLine.split("\t");
          if (tokens[0].trim().equals("yay")) {
            cofactors_left.add(toListFromFlattened(tokens[6]));
            cofactors_right.add(toListFromFlattened(tokens[8]));
          }
        }
        br.close();
      } catch (Exception e) {
        e.printStackTrace();
      }

      MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
      ReactionDiff diff = new ReactionDiff(mongoDB);
      diff.AAMCofactorPairs(cofactors_left, cofactors_right);
  }

  private Long[] toListFromFlattened(String escaped) {
    // convert a list of forms: "[32503, 17644]"  [17567] to their list representations
    if (escaped.startsWith("\""))
      escaped = escaped.substring(1, escaped.length() - 1); // remove the quotes if they are present
    escaped = escaped.substring(1, escaped.length() - 1); // remove the ending and starting []
    String[] tokens = escaped.split(",");

    Long[] list = new Long[tokens.length];
    for (int i = 0; i < tokens.length; i++)
      list[i] = Long.parseLong(tokens[i].trim());
    return list;
  }

  public void outputOperators(String mongoActHost, int mongoActPort, String mongoActDB, String outDir) {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    mongoDB.dumpOperators(outDir);
  }

  /*
   * Right now the function below imports reactions, (some referenced) chemicals
   * Later we can add these functions too:

db.brenda.save({
    uuid: 23234,
    ecnum: [1,1,1,2],
    reaction_name: 'reaction xyz',
    substrates: [{pubchem: 123}, {pubchem: 223}],
    products: [{cas: 440, pubchem: 112}],
    ecTyped: {L1: 1, L2:1, L3:1, L4:2},
});

db.gene.save({
    _id: 1234,
    enzyme_sequence: "AAACCCCCTGCCTGCTGCTG",
    organism: "name",
    reaction: {brenda: 23234},
});
   */

  public String populateActEnzymesFromSQL(String mongoActHost, int mongoActPort, String mongoActDB,
      String sqlHost, long reactionTableBlockSize, long dontGoPastUUID) throws IllegalArgumentException {

    // -- Do we care about serverInfo and userAgent?

    // String serverInfo = getServletContext().getServerInfo();
    // String userAgent = getThreadLocalRequest().getHeader("User-Agent");
    // userAgent = escapeHtml(userAgent);

    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );

    // connect to the mysql database
    MysqlDB sqlDB = new MysqlDB(sqlHost);
    // MysqlDBStateless sqlDB = new MysqlDBStateless(sqlHost);
    long maxUUID = sqlDB.getMaxUUIDInReactionsTable();

    System.out.println("Max UUID for reactions: " + maxUUID);
    for (long i = 0; i < maxUUID && i < dontGoPastUUID; i += reactionTableBlockSize) {
      // for each block get all the reactions in the table for that block
      List<Reaction> reactionSet = sqlDB.getReactionWithUUID(i, i + reactionTableBlockSize);
      if (reactionSet == null) System.out.println("Reaction set is NULL");
      for (Reaction r: reactionSet) {
        if (r == null) System.out.println("Reaction r is NULL");
        for (Long s : r.getSubstrates()) {
          if (s == null) System.out.println("Substrate s is NULL: " + r.getUUID());
          if (sqlDB == null) System.out.println("Sql DB is NULL!");
          Chemical c = sqlDB.getChemical(s);
          if (mongoDB != null) mongoDB.submitToActChemicalDB(c, mongoDB.getNextAvailableChemicalDBid());
        }
        for (Long p : r.getProducts()) {
          Chemical c = sqlDB.getChemical(p);
          if (mongoDB != null) mongoDB.submitToActChemicalDB(c, mongoDB.getNextAvailableChemicalDBid());
        }
        if (mongoDB != null) mongoDB.submitToActReactionDB(r);
      }
      System.out.println("___ At reaction UUID: " + i);

    }

    System.out.println("\n\nDone populating database: Query...");
    List<Long> reacts = mongoDB.getReactants((long) 49);
    System.out.println("Reactants for reaction 49: " + Arrays.toString(reacts.toArray(new Long[1])) + "\n\n");
    System.out.println("Now searching for all reactions that contain chemical pubchem:173...");
    List<Long> reactions = mongoDB.getRxnsWith((long) 173);
    System.out.println("Reactions that have substrate 173: " + Arrays.toString(reactions.toArray(new Long[1])) + "\n\n");
    System.out.println("Now canonicalizing the chemical pubchem: 173, 257...");
    List<Long> compounds = new ArrayList<Long>(); compounds.add((long) 173); compounds.add((long) 257);
    List<String> canon = mongoDB.getCanonNames(compounds);
    System.out.println("Compounds [257, 173] canonicalized: " + Arrays.toString(canon.toArray(new String[1])) + "\n\n");
    List<String> smiles = mongoDB.convertIDsToSmiles(compounds);
    System.out.println("Compounds [257, 173] smiles: " + Arrays.toString(smiles.toArray(new String[1])) + "\n\n");

    return "Hello!<br>" // "<br>I am on " + serverInfo
        // + ".<br><br>Your query came from:<br>" + userAgent
        + "Populated the Mongo db " + mongoActDB + " on server " + mongoActHost + ":" + mongoActPort + "<br>"
        + "Read from MySQL db on server " + sqlHost;
  }

  @Override
  public List<ReactionDetailed> execAnalyticsScript(String mongoActHost, int mongoActPort, String mongoActDB,
      Parameters.AnalyticsScripts script) throws IllegalArgumentException {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    ActAnalytics analytics = new ActAnalytics(mongoDB);

    return analytics.executeScript(script);
  }

  @Override
  public List<Chemical> canonicalizeName(String mongoActHost, int mongoActPort,
      String mongoActDB, String synonym) throws IllegalArgumentException {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    Canonicalizer canon = new Canonicalizer(mongoDB);

    return canon.canonicalize(synonym);
  }

  @Override
  public HashMap<String, List<Chemical>> canonicalizeAll(String mongoActHost, int mongoActPort,
      String mongoActDB, List<String> commonNames)
      throws IllegalArgumentException {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    Canonicalizer canon = new Canonicalizer(mongoDB);

    return canon.canonicalizeAll(commonNames);
  }

  @Override
  public List<Organism> lookupOrganism(String mongoActHost, int mongoActPort,
      String actDB, String organism) {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, actDB );
    Taxonomy taxa = new Taxonomy(mongoDB);
    return taxa.lookup(organism);
  }

  @Override
  public Integer augmentNetwork(String mongoActHost, int mongoActPort, String mongoActDB,
      int numOps, int augmentWithROSteps, String augmentedNwName, String rxns_list_file) {

    MongoDBPaths mongoDB = new MongoDBPaths( mongoActHost, mongoActPort, mongoActDB );
    // Create a network which k-closure using the above ROs
    HashMap<Integer, OperatorSet> categorizedOps = getCategorizedOperators(mongoDB, numOps, rxns_list_file);
    AugmentedReactionNetwork arn = new AugmentedReactionNetwork(mongoDB, augmentedNwName, categorizedOps, augmentWithROSteps);

    return 1; // success
  }

  public HashMap<Integer, OperatorSet> getOperators(String mongoActHost, int mongoActPort, String mongoActDB, int numOps, String rxns_list_file) {
    MongoDBPaths mongoDB = new MongoDBPaths( mongoActHost, mongoActPort, mongoActDB );
    return getCategorizedOperators(mongoDB, numOps, rxns_list_file);
  }

  private HashMap<Integer, OperatorSet> getCategorizedOperators(MongoDBPaths db, int numOps, String rxns_list_file) {

    List<Long> rxns_whitelist = rxns_list_file == null ? null : getKnownGoodRxns(rxns_list_file);
    List<Integer> ops_whitelist = null;
    if (rxns_whitelist != null){
      ops_whitelist = getOpIDsForRxnIDs(rxns_whitelist, db);
      System.out.println("Operators whitelist: " + ops_whitelist);
    }

    // Pull out some ROs from the DB
    Logger.println(0, "Pulling operators from DB.");
    OperatorSet ops = new OperatorSet(db, numOps, ops_whitelist,
        true /* filter duplicates */);

    Logger.println(0, "Categorizing operators by number of substrates.");
    HashMap<Integer, OperatorSet> opsByReactantNum = new HashMap<Integer, OperatorSet>();
    HashMap<OpID, CRO> allOps = ops.getAllCROs();
    /* If we want to limit ourselves to certain ROs
    int[] restrictedArray = {1978733610, -1313685365, 1582928636, -1365893843, 537950498, -1545474372};
    Set<Integer> restrictedIDs = new HashSet<Integer>();
    for (int x = 0; x < restrictedArray.length; x++) {
      restrictedIDs.add(restrictedArray[x]);
    }
    Set<OpID> toRemove = new HashSet<OpID>();
    for (OpID id : allOps.keySet()) {
      CRO cro = allOps.get(id);
      System.out.println(cro.ID());
      if (restrictedIDs.contains(cro.ID())) continue;
      toRemove.add(id);

    }
    for (OpID id : toRemove) {
      allOps.remove(id);
    }
    System.out.println("allOps size " + allOps.size());
    //END RESTRICTION */

    for (OpID id : allOps.keySet()) {
      CRO cro = allOps.get(id);
      int rNum = getNumReactantInOp(cro);
      if (!opsByReactantNum.containsKey(rNum))
        opsByReactantNum.put(rNum, new OperatorSet(db));
      opsByReactantNum.get(rNum).addToOpSet(id, cro, ops.getAllEROsFor(cro));
      System.out.println("RO ID " + id);
      System.out.println("CRO ID " + cro.ID());
    }

    return opsByReactantNum;
  }

  public List<ROApplication> applyROs(String inchi, HashMap<Integer, OperatorSet> opSets) {
    Indigo indigo = new Indigo();
    IndigoInchi indigoInchi = new IndigoInchi(indigo);
    List<ROApplication> newChems = new ArrayList<ROApplication>();

    // expanding single reactant operators is trivial
    OperatorSet ops = opSets.get(1); // get arity == 1 operators...
    for (CRO cro : ops.getAllCROs().values())
      AugmentedReactionNetwork.expandAndAddPrimaryWithCROs(inchi, null, cro, ops.getAllEROsFor(cro), newChems, indigo, indigoInchi);
    return newChems;
  }


  public static List<List<String>> applyRO_OnOneSubstrate_DOTNotation(String mongoActHost, int mongoActPort, String mongoActDB,
      String substrate, long roRep, String roType) {
    MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
    // roType is one of BRO, CRO, ERO, OP to pull from appropriate DB.
    RO ro = mongoDB.getROForRxnID(roRep, roType, true);
    return applyRO_OnOneSubstrate_DOTNotation(substrate, ro);
  }

  public static List<List<String>> applyRO_OnOneSubstrate_DOTNotation(String substrate, RO ro) {
    try {
      Indigo indigo = new Indigo();
      IndigoInchi indigoInchi = new IndigoInchi(indigo);

      boolean outputAsInchi = substrate.startsWith("InChI="); // if input was Inchi we should output Inchi
      List<String> substrates = getSubstrateForROAppl(substrate, indigo, indigoInchi);

      List<List<String>> rxnProducts =  RxnTx.expandChemical2AllProducts(substrates, ro, indigo, indigoInchi);

      if (rxnProducts == null) {
        System.err.println("NONE");
        return null;
      } else {
        List<List<String>> output = new ArrayList<List<String>>();
          for (List<String> products : rxnProducts) {
            List<String> pout = new ArrayList<String>();
            for (String p : products) {
              IndigoObject prod = indigo.loadMolecule(p);
              String prodSMILES = DotNotation.ToNormalMol(prod, indigo);
              String product = !outputAsInchi ? prodSMILES : indigoInchi.getInchi(indigo.loadMolecule(prodSMILES));
              pout.add(product);
            }
            output.add(pout);
          }
        return output;
      }

    } catch (NullPointerException e) {
      System.err.println("RO output molecule is not chemically sound.");
      e.printStackTrace();
      return null;
    } catch (IndigoException e) {
      if (e.getMessage().startsWith("element: bad valence"))
        System.err.println("We violated valence rules.");
      else
        e.printStackTrace();
      return null;
    }
  }

  public static List<List<String>> applyRO_MultipleSubstrates_DOTNotation(List<String> dotNotationSubstrates, RO ro) {
    Indigo indigo = new Indigo();
    IndigoInchi indigoInchi = new IndigoInchi(indigo);

    List<List<String>> rxnProducts =  RxnTx.expandChemical2AllProducts(dotNotationSubstrates, ro, indigo, indigoInchi);

    return rxnProducts;
  }

  public static List<String> getSubstrateForROAppl(String substrate, Indigo indigo, IndigoInchi indigoInchi) {
    List<String> substrates = new ArrayList<String>();
    if (substrate.startsWith("InChI=")) {
      // in inchi we cannot split them as easily, so assume there is only substrate provided.
      substrates.add(toDotNotation(substrate, indigo, indigoInchi));
    } else {
      // in SMILES, we can just split on "."
      for (String s : substrate.split("[.]"))
        substrates.add(toDotNotation(s, indigo));
    }
    return substrates;
  }

  private static String toDotNotation(String substrateInChI, Indigo indigo, IndigoInchi indigoInchi) {
    IndigoObject mol = indigoInchi.loadMolecule(substrateInChI);
    mol = DotNotation.ToDotNotationMol(mol);

    // Never compute InChI of a DOT notation molecule.
    // this is VERY BAD: inchi with [Ac] on it results in a complex
    // where the [Ac]'s are disconnected from the main molecule.
    // return indigoInchi.getInchi(mol);

    return mol.canonicalSmiles();
  }

  public static String toDotNotation(String substrateSMILES, Indigo indigo) {
    IndigoObject mol = indigo.loadMolecule(substrateSMILES);
    mol = DotNotation.ToDotNotationMol(mol);
    return mol.canonicalSmiles(); // do not necessarily need the canonicalSMILES
  }

  // @Deprecated // this one uses the old internal representation that was not DOT-ted. ROs are now stored as DOTs
  // public List<List<String>> applyRO_OnOneSubstrate(String mongoActHost, int mongoActPort, String mongoActDB,
  //     String substrate, long roRep, String roType) {
  //   MongoDB mongoDB = createActConnection( mongoActHost, mongoActPort, mongoActDB );
  //   Indigo indigo = new Indigo();
  //   IndigoInchi indigoInchi = new IndigoInchi(indigo);
  //
  //   if (!substrate.startsWith("InChI=")) {
  //     IndigoObject mol = indigo.loadMolecule(substrate);
  //     substrate = indigoInchi.getInchi(mol);
  //   }
  //   List<String> substrates = new ArrayList<String>();
  //   substrates.add(substrate);

  //   // roType is one of BRO, CRO, ERO, OP to pull from appropriate DB.
  //   RO ro = mongoDB.getROForRxnID(roRep, roType, true);
  //   List<List<String>> rxnProducts =  RxnTx.expandChemical2AllProducts(substrates, ro, indigo, indigoInchi, false); // false indicates we are passing InChI
  //       // but DOT notation does not play well with that: see RxnTx.expand..
  //   if (rxnProducts == null) {
  //     System.out.println("NONE");
  //   } else {
  //     for (List<String> products : rxnProducts) {
  //       for (String p : products) {
  //         System.out.println(indigoInchi.loadMolecule(p).smiles());
  //       }
  //     }
  //   }
  //   return rxnProducts;
  // }

  private int getNumReactantInOp(RO op) {
    String opStr = op.rxn();
    int left = opStr.indexOf(">>");
    String[] reactants = opStr.substring(0, left).split("[.]");
    return reactants.length;
  }

  private List<Integer> getOpIDsForRxnIDs(List<Long> rxns_whitelist, MongoDBPaths mongoDB) {
    List<Integer> opIDs = new ArrayList<Integer>();
    for (Long id : rxns_whitelist)
      opIDs.add(mongoDB.getROForRxnID(id, "OP"));
    return opIDs;
  }



  @Override
  public List<Path> findPathway(String mongoActHost, int mongoActPort, String mongoActDB,
      String optionalSrc, String target, List<String> targetSMILES, List<String> targetCommonNames,
      int numSimilar, int numOps, int maxNewChems, int numPaths,
      int augmentWithROSteps, String augmentedNetworkName,
      boolean addNativeSrcs, boolean findConcrete, boolean useFnGrpAbs, Set<Long> ignoreChemicals, boolean weighted) throws IllegalArgumentException {

    MongoDBPaths mongoDB = new MongoDBPaths( mongoActHost, mongoActPort, mongoActDB );

    VariousSearchers searchers = new VariousSearchers(mongoDB, addNativeSrcs);
    searchers.setTarget(target);
    searchers.setTargetSMILES(targetSMILES, targetCommonNames);
    searchers.setOptionalStart(optionalSrc);
    searchers.setNumPaths(numPaths);
    searchers.setMaxDepthInDFS(10);
    searchers.setTopLevelDirForOutput("./");
    searchers.setNumSimilarToLookupFor(numSimilar);
    searchers.setNumOpsToLookupFromDB(numOps);
    searchers.setNumMaxChemicalsInEnumeration(maxNewChems);
    searchers.setHopsForROAugmentation(augmentWithROSteps);
    searchers.setNetworkName(augmentedNetworkName);
    searchers.setIgnore(ignoreChemicals);
    if (findConcrete) {
      if (targetSMILES == null) {
        if (weighted)
          searchers.setStrategy(Strategy.WEIGHTED);
        else
          searchers.setStrategy(Strategy.DFS);
      } else {
        if (augmentWithROSteps == -1)
          searchers.setStrategy(Strategy.DFS_TO_SIMILAR);
        else
          searchers.setStrategy(Strategy.RO_AUGMENTED_DFS);
      }
      // this is old deprecated search code that does a BFS.
      // searchers.setStrategy(Strategy.OLDBFS);
    } else {
      if (useFnGrpAbs) {
        searchers.setStrategy(Strategy.FNGRPS);
      } else {
        searchers.setStrategy(Strategy.ENUM);
      }
    }

    return searchers.searchPaths();
  }

  @Override
  public void findAbstractPathway(String mongoActHost, int mongoActPort, String mongoActDB,
      List<String> targetSMILES_abs, List<String> targetCommonNames_abs, int numOps, String rxns_list_file) {

    MongoDBPaths mongoDB = new MongoDBPaths( mongoActHost, mongoActPort, mongoActDB );
    System.out.println(mongoActHost+" "+mongoActPort+" "+mongoActDB );
    HashMap<Integer, OperatorSet> categorizedOps = getCategorizedOperators(mongoDB, numOps, rxns_list_file);
    //HashMap<Integer, OperatorSet> categorizedOps = null;

    AbstractSearch search = new AbstractSearch(mongoDB, categorizedOps);
    search.findPath(targetSMILES_abs, targetCommonNames_abs);
  }

  public void runGameServer(String mongoActHost, int mongoActPort, String mongoActDB) {
    MongoDBPaths mongoDB = new MongoDBPaths( mongoActHost, mongoActPort, mongoActDB );

    int gamePort = 28000;
    PathwayGameServer gameServer = new PathwayGameServer(mongoDB, gamePort);
    gameServer.startServer();

  }

  /**
   * Escape an html string. Escaping data received from the client helps to
   * prevent cross-site script vulnerabilities.
   *
   * @param html the html string to escape
   * @return the escaped string
   */
  private String escapeHtml(String html) {
    if (html == null) {
      return null;
    }
    return html.replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
  }
}
