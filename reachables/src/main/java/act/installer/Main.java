package act.installer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import act.installer.bing.BingSearcher;
import act.installer.brenda.SQLConnection;
import act.installer.kegg.KeggParser;
import act.installer.metacyc.MetaCyc;
import act.installer.sequence.SwissProt;
import act.installer.SeqIdentMapper;

import act.shared.ConsistentInChI;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.helpers.P;
import act.installer.patents.FTO;
import act.installer.brenda.BrendaSQL;


public class Main {
  private String brenda, chemicals, taxonomy, names, brendaNames, cofactors, cofactor_pair_AAM, natives, imp_chems; //file names
  private MongoDB db;
  private FileWriter chem, org;

  public Main(String brenda, String taxonomy, String names, String chemicals, String brendaNames, String cofactors, String cofactor_pair_AAM, String natives, String imp_chems, String path, String host, int port, String dbs) {
    this.brenda = path + "/" + brenda;
    this.taxonomy = path + "/" + taxonomy;
    this.names = path + "/" + names;
    this.chemicals = path + "/" + chemicals;
    this.brendaNames = path + "/" + brendaNames;
    this.cofactors = path + "/" + cofactors;
    this.cofactor_pair_AAM = path + "/" + cofactor_pair_AAM;
    this.natives = path + "/" + natives;
    this.imp_chems = path + "/" + imp_chems;
    db = new MongoDB(host, port, dbs);
  }

  private List<String> readCofactorInChIs() {
    System.out.println("reading cofactors");
    List<String> cofactorsl = new ArrayList<String>();
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(this.cofactors))));
      String strLine;
      while ((strLine = br.readLine()) != null)   {
        if (strLine.length() > 0 && strLine.charAt(0) != '#')
          cofactorsl.add(strLine);
      }
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return cofactorsl;
  }

  public void addChemicals(List<String> cofactor_inchis) {
    try {

      ImportantChemicals imp = addImportantChemicalsFromLists();

      new BrendaSQL(db, new File("")).installChemicals(cofactor_inchis);

      addImportantNotAlreadyAdded(imp);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private ImportantChemicals addImportantChemicalsFromLists() throws Exception {
    String strLine;
    ImportantChemicals imp = new ImportantChemicals();
    BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(imp_chems))));
    //Read the imp chemicals file (DB_SRC DB_ID InChI)
    while ((strLine = br.readLine()) != null) {
      if (strLine.startsWith("#"))
        continue;
      imp.parseAndAdd(strLine);
    }
    br.close();
    System.out.println("");

    return imp;
  }

  private void addImportantNotAlreadyAdded(ImportantChemicals imp) throws Exception {
    for (Chemical c : imp.remaining()) {
      long installid = db.getNextAvailableChemicalDBid();
      /*
         This use of a locally incremented installid counter
         will not be safe if multiple processes are
         writing to the DB. E.g., if we distribute the installer

         If that is the case, then use some locks and
         long installid = db.getNextAvailableChemicalDBid();
         to pick the next available id to install this chem to
      */
      db.submitToActChemicalDB(c, installid);
    }
  }

  private void tagNatives() {
    System.out.println("reading natives");
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(this.natives))));
      String compound;
      while ((compound = br.readLine()) != null) {
        String inchi = ConsistentInChI.consistentInChI(compound, "Tagging natives");
        db.updateChemicalAsNative(compound);
      }
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void addBrendaReactionsFromSQL(File indexPath) throws Exception {
    new BrendaSQL(db, indexPath, true).installReactions();
  }

  public void addBrendaOrganismsFromSQL() throws SQLException {
    new BrendaSQL(db, new File("")).installOrganisms();
  }

  public void writeErrors(FileWriter chemFW, FileWriter orgFW) {
    this.chem = chemFW;
    this.org = orgFW;
  }

  private static void MSG_USER_HOLD(String notice) throws Exception {
    // DEBUGGING MODE... inform the user
    System.out.println("DEBUGGING MODE. " + notice);
    System.out.println("DEBUGGING MODE. Remove after fixes. Press <Enter>");
    new BufferedReader(new InputStreamReader(System.in)).readLine();
  }

  /*
   * args should contain the following in:
   * data directory relative to working
   * brenda data
   * organisms data
   * names data
   * chemicals (pubchem)
   * brenda names
   *
   *
   * optionally:
   * filename to put unfound chemicals
   * filename to put unfound organisms
   */
  public static void main(String[] args) throws Exception {

    String operation = args[0];
    int dbPort = Integer.parseInt(args[1]);
    String server = args[2];
    String dbname = args[3];

    if (operation.equals("BRENDA")) {
      String unfoundChemNames = null, unfoundOrgNames = null;

      String path = System.getProperty("user.dir")+"/"+args[4];
      String brendafile = args[5];
      String taxonomy = args[6];
      String organismNames = args[7];
      String chemicals = args[8];
      String brendaNames = args[9];
      String cofactors = args[10];
      String cofactor_pair_AAM = args[11];
      String natives = args[12];
      String litmining_chem_cleanup = args[13]; // unused
      String imp_chemicals = args[14];

      if (args.length > 15) {
        unfoundChemNames = args[15];
        unfoundOrgNames = args[16];
      }

      File brendaIndexPath = new File(System.getProperty("user.dir"), "brenda_tables.rocksdb");

      Main installer = new Main(brendafile,taxonomy,organismNames,chemicals,brendaNames,cofactors, cofactor_pair_AAM, natives, imp_chemicals, path, server, dbPort, dbname);
      Long s = System.currentTimeMillis();

      boolean add_org = true,
          add_chem = true,
          add_natives = true,
          add_brenda_reactions = true,
          add_brenda_names = false
          ;

      if (!add_chem) { System.out.println("SKIPPING chemicals"); } else {
        System.out.println("inserting chemicals");
        installer.addChemicals(installer.readCofactorInChIs());
      }
      System.out.println((System.currentTimeMillis() - s)/1000);

      System.out.println("DONE CHEMICALS");

      if (!add_org) { System.out.println("SKIPPING organisms"); } else {
        System.out.println("inserting organisms");
        installer.addBrendaOrganismsFromSQL();
      }
      System.out.println((System.currentTimeMillis() - s)/1000);

      System.out.println("DONE ORGANISMS");

      System.out.println((System.currentTimeMillis() - s)/1000);

      System.out.println("DONE COFACTOR AAMs");

      if(unfoundChemNames != null) {
        File c = new File(unfoundChemNames);
        File o = new File(unfoundOrgNames);
        try {
          c.createNewFile();
          o.createNewFile();
        } catch (IOException e1) {
          e1.printStackTrace();
        }

        try {
          installer.writeErrors(new FileWriter(unfoundChemNames),new FileWriter(unfoundOrgNames));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }

      if (!add_brenda_reactions) { System.out.println("SKIPPING reactions"); } else {
        System.out.println("inserting reactions");
        installer.addBrendaReactionsFromSQL(brendaIndexPath);
      }

      System.out.println("DONE BRENDA RXNS");

      if (!add_natives) { System.out.println("SKIPPING natives tagging."); } else {
        System.out.println("tagging native chemicals");
        installer.tagNatives();
      }

      System.out.println("DONE NATIVES TAGGING");

      System.out.println((System.currentTimeMillis() - s)/1000);

    } else if (args[0].equals("PUBMED")) {
      String pubmedDir = args[4];
      int start = Integer.parseInt(args[5]);
      int end = Integer.parseInt(args[6]);
      PubmedDBCreator pmInstall = new PubmedDBCreator(pubmedDir, start, end, server, dbPort, dbname);
      pmInstall.addPubmedEntries();

    } else if (args[0].equals("KEGG")) {
      MongoDB db = new MongoDB(server, dbPort, dbname);
      String path = System.getProperty("user.dir")+"/"+args[4];
      KeggParser.parseKegg(path + "/reaction.lst", path + "/compound.inchi", path + "/compound", path + "/reaction", path + "/cofactors.txt", db);

    } else if (args[0].equals("SWISSPROT")) {
      String path = System.getProperty("user.dir")+"/"+args[4];
      int nfiles = SwissProt.getDataFileNames(path).size();
      int chunk = 1;
      MongoDB db = new MongoDB(server, dbPort, dbname);

      for (int i=0; i<nfiles; i+=chunk) {
        SwissProt s = new SwissProt(path);
        s.process(i, i + chunk, db);          // process the chunk
      }
      db.close();
    } else if (args[0].equals("MAP_SEQ")) {
      MongoDB db = new MongoDB(server, dbPort, dbname);

      SeqIdentMapper mapper = new SeqIdentMapper(db);
      // this maps rxnid (db.actfamilies) -> { seqid } (db.seq)
      // and creates the rev links seqid -> { rxnid } as well
      // additionally it might add more entries to db.seq through
      // web api lookup for accession numbers that are not
      // installed as part of the above SWISSPROT install. E.g.,
      // Some BRENDA acc#'s refer to GenBank, unreviewed
      // Uni/SwissProt (i.e., TrEBML, EMBL)
      // It also calls the NCBI Entrez API using biopython
      // to lookup sequences by their EC# + Organism
      mapper.map();

    } else if (args[0].equals("VENDORS")) {
      String vendors_file = System.getProperty("user.dir")+"/"+args[4];
      Set<String> priority_chems_files = new HashSet<String>();
      // assume the rest of the args are priority chem
      // files, i.e., list of inchis, e.g., reachables etc.
      for (int i=5; i<args.length; i++)
        priority_chems_files.add(args[i]);
      MongoDB db = new MongoDB(server, dbPort, dbname);
      new ChemSpider().addChemVendors(db, vendors_file, priority_chems_files);
      db.close();

    } else if (args[0].equals("FTO")) {
      String vendors_file = System.getProperty("user.dir")+"/"+args[4];
      Set<String> priority_chems_files = new HashSet<String>();
      // assume the rest of the args are priority chem
      // files, i.e., list of inchis, e.g., reachables etc.
      for (int i=5; i<args.length; i++)
        priority_chems_files.add(args[i]);
      MongoDB db = new MongoDB(server, dbPort, dbname);
      new FTO().addPatents(db, vendors_file, priority_chems_files);
      db.close();

    } else if (args[0].equals("METACYC")) {
      String path = System.getProperty("user.dir")+"/"+args[4];
      int start = Integer.parseInt(args[5]);
      int end = Integer.parseInt(args[6]);

      // http://biocyc.org/biocyc-pgdb-list.shtml
      // Note: by default, we process all Tier 1, 2, and 3 biopax files
      // from metacyc. 38 of them are Tier 1 and 2 files. If you
      // need to restrict the processing to those set
      // call m.loadOnlyTier12(true) in the loop below (and when
      // looking up the number of files to-be-processed.

      int nfiles = new MetaCyc(path).getNumFilesToBeProcessed();
      System.out.println("Total: " + nfiles + " level3 biopax files found.");
      System.out.println("Range: [" + start + ", " + end + ")");
      int chunk = 1; // you can go up to a max of about 20 chunks (mem:3gb)
      // see "Performance" section below for a run over 100 files
      MongoDB db = new MongoDB(server, dbPort, dbname);
      for (int i=start; i<nfiles && i<end; i+=chunk) {
        long startTime = System.currentTimeMillis();

        // Now create a new MetaCyc object for each chunk. Holds the entire
        // processed information in a HashMap of OrganismCompositions.
        //
        // By default, metacyc will load all Tier 1,2, and 3 files.
        // If you need it to load only 38 Tier 1,2 files call m.loadOnlyTier12(true)
        MetaCyc m = new MetaCyc(path);

        int chunkEnd = i + chunk > end ? end : i + chunk;
        System.out.format("Processing: [%d, %d)\n", i, chunkEnd);
        m.process(i, chunkEnd);          // process the chunk
        m.sendToDB(db);                 // install in DB
        long endTime = System.currentTimeMillis();
        long timeDiff = endTime - startTime;
        System.out.println(String.format("--- Total time for chunk [%d, %d): %d ms, %d ms per file",
            i, chunkEnd, timeDiff, timeDiff / chunk));
        // when iterating to new chunk, MetaCyc object will be GC'ed releasing
        // accumulated OrganismCompositions information for those organisms
        // but that is ok, since we already installed it in MongoDB.
      }
      db.close();

      // Performance:
      // int start =  1120; // 0; // 1120 is ecocyc
      // int end   =  1220; // Integer.MAX_VALUE; // Integer.MAX_VALUE;
      // Time: 1861s [1120,1220) @ 1/chunk -- therefore ~18 hours to do 3528
      // > print(db.chemicals.count()); print(db.actfamilies.count()); print(db.sequences.count())
      // 67543,  50809,  24329 -- old   chems, rxns, sequences
      // 95190, 232550, 206037 -- new   chems, rxns, sequences
      // 27647, 181741, 181708 -- delta chems, rxns, sequences
      // only 1271 chems are really small molecules with new inchis. rest big molecules
      // so wont appear in reachables search. All big molecules will be by default
      // unreachable in this setting; but we could make them reachable!?
      //
      // Resulting DB size:
      // 0.42, 0.28, 0.15 -- new chems, rxns, sequences db size in GB
      // So expected total size: (above * 35.28)
      // 14.82, 9.88, 3.80 -- sum = 27.5 GB

    } else if (args[0].equals("CHEBI")) {
      System.out.format("Adding ChEBI applications in ChEBI cross-reference metadata.");
      MongoDB db = new MongoDB(server, dbPort, dbname);
      new BrendaSQL(db, new File("")).installChebiApplications();
      System.out.format("Done adding ChEBI applications.");
    } else if (args[0].equals("BING")) {
      BingSearcher bingSearcher = new BingSearcher();
      System.out.format("Installing Bing Search cross-references.");
      try {
        System.out.format("Initializing Mongo database.");
        MongoDB db = new MongoDB(server, dbPort, dbname);
        System.out.format("Constructing a list of all non-Fake InChIs to compute Bing Search results.");
        Set<String> inchis = db.constructAllNonFakeInChIs();
        System.out.format("Adding Bing Search results for all non-Fake InChIs in the chemicals database.");
        System.out.format("%d non-Fake InChIs were found.");
        bingSearcher.addBingSearchResultsForInchiSet(db, inchis);
        System.out.format("Done adding Bing Search results.");
      } catch (Exception e) {
        System.err.format("An exception occurred while trying to install Bing Search results: %s", e);
      }
  } else {
      System.err.format("First argument needs to be BRENDA, RARITY, PUBMED, KEGG, METACYC, CHEBI or BING. " +
          "Aborting. [Given: %s]\n", args[0]);
    }
  }
}
