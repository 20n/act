package act.installer.brenda;

import act.client.CommandLineRun;
import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.helpers.P;
import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.Binary;
import org.json.JSONArray;
import org.json.JSONObject;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrendaSQL {
  /* This offset is added to the BRENDA organism id to differentiate organisms in the BRENDA Organism table from
   * organisms in the NCBI taxonomy (also retrieved from BRENDA tables).
   * TODO: properly label organism IDs by source so we can get rid of this hack. */
  public static final long BRENDA_ORGANISMS_ID_OFFSET = 4000000000l;
  public static final long BRENDA_NO_NCBI_ID = -1;

	private MongoDB db;
  private File supportingIndex;
  private Boolean cleanUpSupportingIndex = false;

  public BrendaSQL(MongoDB db, File index) {
    this.db = db;
    this.supportingIndex = index;
  }

  public BrendaSQL(MongoDB db, File index, Boolean cleanUpSupportingIndex) {
    this.db = db;
    this.supportingIndex = index;
    this.cleanUpSupportingIndex = cleanUpSupportingIndex;
  }

  public void installChemicals(List<String> cofactor_inchis) throws SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    // This expects an SSH tunnel to be running, one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 10000, "brenda_user", "micv395-pastille");

    long installid = db.getNextAvailableChemicalDBid(), cofactor_num = 0;
    Iterator<BrendaSupportingEntries.Ligand> ligands = brendaDB.getLigands();
    while (ligands.hasNext()) {
      // this ligand iterator will not give us unique chemical
      // inchis. so we have to lookup what inchis we have already seen
      // and if a repeat shows up, we only add the name/other metadata...

      BrendaSupportingEntries.Ligand ligand = ligands.next();
      Chemical c = createActChemical(ligand);
      if (cofactor_inchis.contains(c.getInChI()))
        c.setAsCofactor();
      if (c.getUuid() == -1) {
        // indeed a new chemical inchi => install new

        /* 
           This use of a locally incremented installid counter 
           will not be safe if multiple processes are
           writing to the DB. E.g., if we distribute the installer

           If that is the case, then use some locks and
           long installid = db.getNextAvailableChemicalDBid();
           to pick the next available id to install this chem to
        */
        db.submitToActChemicalDB(c, installid);
        if (c.isCofactor()) {
          System.out.format("Installed cofactor #%d, dbid #%d\n", cofactor_num++, installid);
        }

        installid++;
        numEntriesAdded++;
      } else {
        // chemical already seen, just merge with existing in db
        // submitToActChemicalDB checks pre-existing, and if yes
        // ignores the provided installid, and just merges with existing
        db.submitToActChemicalDB(c, (long) -1);
      }
    }

    brendaDB.disconnect();
    System.out.format("Main.addChemicals: Num added %d\n", numEntriesAdded);
  }

  private Chemical createActChemical(BrendaSupportingEntries.Ligand ligand) {
    // read all fields from the BRENDA SQL ligand table
    String brenda_inchi = ligand.getInchi();
    String name = ligand.getLigand().toLowerCase();
    String molfile = ligand.getMolfile();
    Integer group_id_synonyms = ligand.getGroupId();
    Integer brenda_id = ligand.getLigandId();

    if (brenda_inchi == null || brenda_inchi.equals("")) {
      brenda_inchi = "InChI=/FAKE/BRENDA/" + brenda_id;
    }

    String inchi = CommandLineRun.consistentInChI(brenda_inchi, "BRENDA SQL install");

    // check if this inchi has already been installed as a db chemical
    Chemical exists = db.getChemicalFromInChI(inchi);

    if (exists != null) {
      // chemical already exists in db, return as is.
      setMetaData(exists, name, molfile, brenda_id, group_id_synonyms);
      return exists;
    }

    // this is the first time we are seeing a ligand with this inchi
    // create a chemical with this new inchi
    Chemical c = new Chemical((long) -1); // id we set here ignored on install
    c.setInchi(inchi);
    c.setSmiles(inchi2smiles(inchi));
    setMetaData(c, name, molfile, brenda_id, group_id_synonyms);

    return c;
  }

  private void setMetaData(Chemical c, String name, String molfile, Integer bid, Integer group_id_synonyms) {
    // we add to Brenda Names because getChemicalIDFromExactBrendaName
    // looks up in that field of the chemical for a match.
    c.addBrendaNames(name);

    // set molfile, bid, and group_id_synonyms in brenda.xref
    JSONObject brendaMetadata = new JSONObject();
    brendaMetadata.put("brenda_id", bid);
    brendaMetadata.put("group_id_synonyms", group_id_synonyms);
    if (molfile != null) 
      brendaMetadata.put("molfile", molfile);
    c.putRef(Chemical.REFS.BRENDA, brendaMetadata);
  }

  private String inchi2smiles(String inchi) {
    Indigo ind = new Indigo();
    IndigoInchi ic = new IndigoInchi(ind);
    try {
      return ic.loadMolecule(inchi).canonicalSmiles();
    } catch (Exception e) {
      return null;
    }
  }

  public void installReactions() throws IOException, ClassNotFoundException, RocksDBException, SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    System.out.println("Connecting to brenda DB.");
    // This expects an SSH tunnel to be running, like the one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 10000, "brenda_user", "micv395-pastille");
    System.out.println("Connection established.");

    // Create a local index of the BRENDA tables that share the same simple access pattern.
    System.out.println("Creating supporting index of BRENDA data");
    try {
      brendaDB.createSupportingIndex(supportingIndex);
    } catch (Exception e) {
      System.err.println("Caught exception while building BRENDA index: " + e.getMessage());
      e.printStackTrace(System.err);
      throw new RuntimeException(e);
    }
    System.out.println("Supporting index creation complete.");

    // Open the BRENDA index for reading.
    System.out.println("Opening supporting idex at " + this.supportingIndex.getAbsolutePath());
    Pair<RocksDB, Map<String, ColumnFamilyHandle>> openedDbObjects = brendaDB.openSupportingIndex(supportingIndex);
    RocksDB rocksDB = openedDbObjects.getLeft();
    Map<String, ColumnFamilyHandle> columnFamilyHandleMap = openedDbObjects.getRight();

    /* Create a table of recommended names.  There's at most 1 per EC number, so these can just live in memory for
     * super fast lookup. */
    System.out.println("Retrieving recommended name table.");
    BrendaSupportingEntries.RecommendNameTable recommendNameTable = brendaDB.fetchRecommendNameTable();
    System.out.println("Recommended name table retrieved.");

    Iterator<BrendaRxnEntry> rxns = brendaDB.getRxns();
    while (rxns.hasNext()) {
      BrendaRxnEntry brendaTblEntry = rxns.next();
      Reaction r = createActReaction(brendaTblEntry);
      r.addProteinData(getProteinInfo(brendaTblEntry, brendaDB, rocksDB, columnFamilyHandleMap, recommendNameTable));
      db.submitToActReactionDB(r);
      numEntriesAdded++;
      if (numEntriesAdded % 10000 == 0) {
        System.out.println("Processed " + numEntriesAdded + " reactions.");
      }
    }

    rxns = brendaDB.getNaturalRxns();
    while (rxns.hasNext()) {
      BrendaRxnEntry brendaTblEntry = rxns.next();
      Reaction r = createActReaction(brendaTblEntry);
      r.addProteinData(getProteinInfo(brendaTblEntry, brendaDB,  rocksDB, columnFamilyHandleMap, recommendNameTable));
      db.submitToActReactionDB(r);
      numEntriesAdded++;
      if (numEntriesAdded % 10000 == 0) {
        System.out.println("Processed " + numEntriesAdded + " reactions.");
      }
    }

    rocksDB.close();
    brendaDB.disconnect();

    if (this.cleanUpSupportingIndex) {
      brendaDB.deleteSupportingIndex(supportingIndex);
    }

    System.out.format("Main.addBrendaReactionsFromSQL: Num entries added %d\n", numEntriesAdded);
  }

  public void installOrganisms() throws SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    // This expects an SSH tunnel to be running, like the one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 10000, "brenda_user", "micv395-pastille");

    Iterator<BrendaSupportingEntries.Organism> organisms = brendaDB.getOrganisms();
    while (organisms.hasNext()) {
      BrendaSupportingEntries.Organism organism = organisms.next();
      numEntriesAdded++;
      // TODO: continue here.
      // TODO: what is the space of organism ids we get from other sources, and how can we avoid collisions?
      Organism o = new Organism(organism.getOrganismId().longValue() + BRENDA_ORGANISMS_ID_OFFSET,
          BRENDA_NO_NCBI_ID,
          organism.getOrganism()
      );
      db.submitToActOrganismNameDB(o);
    }

    brendaDB.disconnect();
    System.out.format("Main.addBrendaReactionsFromSQL: Num entries added %d\n", numEntriesAdded);
  }

  private Reaction createActReaction(BrendaRxnEntry entry) {
    String org = entry.getOrganism();
    String litref = entry.getLiteratureRef();
    Long orgid = getOrgID(org);

    String rev = entry.getReversibility();
    String sub = entry.getSubstrateStr();
    String prd = entry.getProductStr();
    String ecnum = entry.getEC();
    String brendaID = entry.getBrendaID();

    Map<Long, Integer> substrates = splitAndGetCmpds(sub);
    Map<Long, Integer> products = splitAndGetCmpds(prd);

    String readable = constructReadable(org, sub, prd, REVERSIBILITY.brendaCode(rev));

    Long[] substrates_ids = substrates.keySet().toArray(new Long[0]);
    Long[] products_ids = products.keySet().toArray(new Long[0]);

    Reaction rxn = new Reaction(-1L, substrates_ids, products_ids, ecnum, readable);

    rxn.addReference(Reaction.RefDataSource.PMID, litref);

    for (Long s : substrates.keySet())
      rxn.setSubstrateCoefficient(s, substrates.get(s));
    for (Long p : products.keySet())
      rxn.setProductCoefficient(p, products.get(p));

    rxn.setDataSource(Reaction.RxnDataSource.BRENDA);

    return rxn;
  }

  public <T> List<T> getRocksDBEntry(RocksDB rocksDB, ColumnFamilyHandle cfh, byte[] key)
      throws IOException, ClassNotFoundException, RocksDBException {
    byte[] bytes = rocksDB.get(cfh, key);
    if (bytes == null) {
      return new ArrayList<>(0);
    }
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
    List<T> vals = (List<T>) ois.readObject(); // TODO: check this?
    return vals;
  }

  private JSONObject getProteinInfo(BrendaRxnEntry sqlrxn, SQLConnection sqldb,
                                    RocksDB rocksDB, Map<String, ColumnFamilyHandle> columnFamilyHandleMap,
                                    BrendaSupportingEntries.RecommendNameTable recommendNameTable)
      throws ClassNotFoundException, IOException, RocksDBException, SQLException {
    String org = sqlrxn.getOrganism();
    String litref = sqlrxn.getLiteratureRef();
    Long orgid = getOrgID(org);

    JSONObject protein = new JSONObject();

    protein.put("datasource", "BRENDA");
    protein.put("organism", orgid);
    protein.put("literature_ref", litref);

    BrendaSupportingEntries.RecommendName recommendName =
        recommendNameTable.getRecommendedNameForECNumber(sqlrxn.getEC());
    if (recommendName != null) {
      // TODO: can we use Jackson's annotations API to make this quick and easy?
      JSONObject rn = new JSONObject();
      rn.put("recommended_name", recommendName.getRecommendedName());
      rn.put("go_num", recommendName.getGoNumber());
      protein.put("recommended_name", rn);
    }

    byte[] supportingEntryKey = BrendaSupportingEntries.IndexWriter.makeKey(sqlrxn.ecNumber, litref, org);

    {
      // ADD sequence information
      JSONArray seqs = new JSONArray();
      for (BrendaSupportingEntries.Sequence seqdata : sqldb.getSequencesForReaction(sqlrxn)) {
        JSONObject s = new JSONObject();
        s.put("seq_brenda_id", seqdata.getBrendaId());
        s.put("seq_acc", seqdata.getFirstAccessionCode());
        s.put("seq_source", seqdata.getSource());
        s.put("seq_sequence", seqdata.getSequence());
        s.put("seq_name", seqdata.getEntryName());
        seqs.put(s);
      }
      protein.put("sequences", seqs);
    }

    /* Rather than querying the BRENDA DB for these supporting entries, we fetch them from an on-disk index. */
    {
      List<BrendaSupportingEntries.KMValue> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.KMValue.COLUMN_FAMILY_NAME), supportingEntryKey);
      addKMValues(protein, vals);
    }

    {
      List<BrendaSupportingEntries.KCatKMValue> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.KCatKMValue.COLUMN_FAMILY_NAME), supportingEntryKey);
      addKCatKMValues(protein, vals);
    }


    {
      List<BrendaSupportingEntries.SpecificActivity> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.SpecificActivity.COLUMN_FAMILY_NAME), supportingEntryKey);
      addSpecificActivity(protein, vals);
    }

    {
      List<BrendaSupportingEntries.Subunits> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.Subunits.COLUMN_FAMILY_NAME), supportingEntryKey);
      addSubunits(protein, vals);
    }

    {
      List<BrendaSupportingEntries.Expression> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.Expression.COLUMN_FAMILY_NAME), supportingEntryKey);
      addExpressions(protein, vals);
    }

    {
      List<BrendaSupportingEntries.Localization> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.Localization.COLUMN_FAMILY_NAME), supportingEntryKey);
      addLocalizations(protein, vals);
    }

    {
      List<BrendaSupportingEntries.ActivatingCompound> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.ActivatingCompound.COLUMN_FAMILY_NAME), supportingEntryKey);
      addActivatingCompounds(protein, vals);
    }

    {
      List<BrendaSupportingEntries.Inhibitors> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.Inhibitors.COLUMN_FAMILY_NAME), supportingEntryKey);
      addInhibitors(protein, vals);
    }

    {
      List<BrendaSupportingEntries.Cofactor> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.Cofactor.COLUMN_FAMILY_NAME), supportingEntryKey);
      addCofactors(protein, vals);
    }

    {
      List<BrendaSupportingEntries.GeneralInformation> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.GeneralInformation.COLUMN_FAMILY_NAME), supportingEntryKey);
      addGeneralInformation(protein, vals);
    }

    {
      List<BrendaSupportingEntries.OrganismCommentary> vals = getRocksDBEntry(rocksDB,
          columnFamilyHandleMap.get(BrendaSupportingEntries.OrganismCommentary.COLUMN_FAMILY_NAME), supportingEntryKey);
      addOrganismCommentary(protein, vals);
    }

    return protein;
  }

  // For use when an on-disk index of BRENDA data is not an option.
  private JSONObject getProteinInfo(BrendaRxnEntry sqlrxn, SQLConnection sqldb,
                                    BrendaSupportingEntries.RecommendNameTable recommendNameTable)
      throws SQLException {
    String org = sqlrxn.getOrganism();
    String litref = sqlrxn.getLiteratureRef();
    Long orgid = getOrgID(org);

    JSONObject protein = new JSONObject();

    protein.put("datasource", "BRENDA");
    protein.put("organism", orgid);
    protein.put("literature_ref", litref);

    BrendaSupportingEntries.RecommendName recommendName =
        recommendNameTable.getRecommendedNameForECNumber(sqlrxn.getEC());
    if (recommendName != null) {
      // TODO: can we use Jackson's annotations API to make this quick and easy?
      JSONObject rn = new JSONObject();
      rn.put("recommended_name", recommendName.getRecommendedName());
      rn.put("go_num", recommendName.getGoNumber());
      protein.put("recommended_name", rn);
    }

    {
      // ADD sequence information
      JSONArray seqs = new JSONArray();
      for (BrendaSupportingEntries.Sequence seqdata : sqldb.getSequencesForReaction(sqlrxn)) {
        JSONObject s = new JSONObject();
        s.put("seq_brenda_id", seqdata.getBrendaId());
        s.put("seq_acc", seqdata.getFirstAccessionCode());
        s.put("seq_source", seqdata.getSource());
        s.put("seq_sequence", seqdata.getSequence());
        s.put("seq_name", seqdata.getEntryName());
        seqs.put(s);
      }
      protein.put("sequences", seqs);
    }

    addKMValues(protein, sqldb.getKMValue(sqlrxn));
    addKCatKMValues(protein, sqldb.getKCatKMValues(sqlrxn));
    addSpecificActivity(protein, sqldb.getSpecificActivity(sqlrxn));
    addSubunits(protein, sqldb.getSubunits(sqlrxn));
    addExpressions(protein, sqldb.getExpression(sqlrxn));
    addLocalizations(protein, sqldb.getLocalization(sqlrxn));
    addActivatingCompounds(protein, sqldb.getActivatingCompounds(sqlrxn));
    addInhibitors(protein, sqldb.getInhibitors(sqlrxn));
    addCofactors(protein, sqldb.getCofactors(sqlrxn));
    addGeneralInformation(protein, sqldb.getGeneralInformation(sqlrxn));
    addOrganismCommentary(protein, sqldb.getOrganismCommentary(sqlrxn));

    return protein;
  }

  public void addKMValues(JSONObject protein, List<BrendaSupportingEntries.KMValue> values) {
    // ADD Km information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.KMValue km : values) {
      JSONObject e = new JSONObject();
      e.put("val", km.getKmValue());
      e.put("comment", km.getCommentary());
      entries.put(e);
    }
    protein.put("km", entries);
  }

  public void addKCatKMValues(JSONObject protein, List<BrendaSupportingEntries.KCatKMValue> values) {
    // ADD Kcat/Km information
    JSONArray  entries = new JSONArray();
    for (BrendaSupportingEntries.KCatKMValue entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getKcatKMValue());
      e.put("substrate", entry.getSubstrate());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("kcat/km", entries);
  }

  public void addSpecificActivity(JSONObject protein, List<BrendaSupportingEntries.SpecificActivity> values) {
    // ADD Specific Activity
    JSONArray  entries = new JSONArray();
    for (BrendaSupportingEntries.SpecificActivity entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getSpecificActivity());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("specific_activity", entries);
  }

  public void addSubunits(JSONObject protein, List<BrendaSupportingEntries.Subunits> values) {
    // ADD Subunit information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.Subunits entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getSubunits());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("subunits", entries);
    }

  public void addExpressions(JSONObject protein, List<BrendaSupportingEntries.Expression> values) {
    // ADD Expression information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.Expression entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getExpression());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("expression", entries);
  }

  public void addLocalizations(JSONObject protein, List<BrendaSupportingEntries.Localization> values) {
    // ADD Localization information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.Localization entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getLocalization());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("localization", entries);
  }

  public void addActivatingCompounds(JSONObject protein, List<BrendaSupportingEntries.ActivatingCompound> values) {
    // ADD Activating Compounds information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.ActivatingCompound entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getActivatingCompound());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("activator", entries);
  }

  public void addInhibitors(JSONObject protein, List<BrendaSupportingEntries.Inhibitors> values) {
    // ADD Inhibiting Compounds information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.Inhibitors entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getInhibitors());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("inhibitor", entries);
  }

  public void addCofactors(JSONObject protein, List<BrendaSupportingEntries.Cofactor> values) {
    // ADD Cofactors information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.Cofactor entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getCofactor());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("cofactor", entries);
  }

  public void addGeneralInformation(JSONObject protein, List<BrendaSupportingEntries.GeneralInformation> values) {
    // ADD General information
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.GeneralInformation entry : values) {
      JSONObject e = new JSONObject();
      e.put("val", entry.getGeneralInformation());
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("general_information", entries);
  }

  public void addOrganismCommentary(JSONObject protein, List<BrendaSupportingEntries.OrganismCommentary> values) {
    // ADD Organism Commentary
    JSONArray entries = new JSONArray();
    for (BrendaSupportingEntries.OrganismCommentary entry : values) {
      JSONObject e = new JSONObject();
      e.put("comment", entry.getCommentary());
      entries.put(e);
    }
    protein.put("organism_commentary", entries);
  }

  private String constructReadable(String o, String s, String p, REVERSIBILITY r) {
    return " {" + o + "} " + s + " " + r + " " + p;

  }

  enum REVERSIBILITY { 
    R("<->"),
    IR("->"),
    UNK("-?>");

    private String inBetweenRxn;

    private REVERSIBILITY(String inBtwn) {
      this.inBetweenRxn = inBtwn;
    }

    public static REVERSIBILITY brendaCode(String revCode) {
      if (revCode.equals("r")) {
        return REVERSIBILITY.R;
      } else if (revCode.equals("ir")) {
        return REVERSIBILITY.IR;
      } else if (revCode.equals("?")) {
        return REVERSIBILITY.UNK;
      }

      // default
      // when junk entries populate the reversibility field
      return REVERSIBILITY.UNK;
    }

    @Override
    public String toString() {
      return this.inBetweenRxn;
    }
  };

  private Long getOrgID(String organism) {
    Long id = db.getOrganismId(organism);
    if (id == -1) logMsgBrenda("Organism: " + organism);

    return id;
  }

  private Map<Long, Integer> splitAndGetCmpds(String cmpdsSet) {
    String[] cmpds = cmpdsSet.split(" \\+ ");
    Map<Long, Integer> cids = new HashMap<Long, Integer>();
    long cid;
    for (int i = 0; i < cmpds.length; i++) {
      String name = cmpds[i].trim();
      cid = db.getChemicalIDFromExactBrendaName(name);
      if (cid != -1) {
        // was able to resolve this to a proper chemical; great!
        // install, with stoichiometry = 1
        cids.put(cid, 1);
      } else {
        // cid == -1
        P<Long, Integer> matched;

        if (checkIsVague(name)) {
          // these are not real chemicals, we cannot resolve them
          // skip, i.e., do nothing!
        } else if ((matched = checkStoichiometrySpecified(name)) != null) {
          cids.put(matched.fst(), matched.snd());
        } else if ((matched = checkHasPlusAtEnd(name)) != null) {
          cids.put(matched.fst(), matched.snd());
        } else if ((matched = checkHasNInFront(name)) != null) {
          cids.put(matched.fst(), matched.snd());
        } else {
          // still failed, after attempting to extract stoichiometric coeff
          logMsgBrenda("Chemical: " + name);
        }

      }
    }
    return cids;
  }

  private boolean checkIsVague(String name) {
    return name.equals("?") || name.equals("more") || name.equals("");
  }

  private P<Long, Integer> checkHasNInFront(String name) {
    if (name.startsWith("n ")) {
      String truename = name.substring(2);
      long cid = db.getChemicalIDFromExactBrendaName(truename);
      if (cid != -1) {
        // succeeded in matching a "n acetone" kinda chemical occurance
        return new P<Long, Integer>( cid, 1 );
      }
    }

    return null;
  }

  private P<Long, Integer> checkHasPlusAtEnd(String name) {
    if (name.endsWith(" +")) {
      String truename = name.substring(0, name.length() - 2);
      long cid = db.getChemicalIDFromExactBrendaName(truename);
      if (cid != -1) {
        // succeeded in matching a "n acetone" kinda chemical occurance
        return new P<Long, Integer>( cid, 1 );
      }
    }

    return null;
  }

  private P<Long, Integer> checkStoichiometrySpecified(String name) {
    // this could have happened because there was stoichiometry info
    // so, see if there is a "<number><space><rest>" prefix and 
    // attempt resolution of <rest>.
    P<Integer, String> n_rest = patternMatchStoichiometry(name);

    if (n_rest != null) {
      long cid = db.getChemicalIDFromExactBrendaName(n_rest.snd());
      if (cid != -1) {
        // succeeded in finding a stoichiometric coefficient
        return new P<Long, Integer>( cid, n_rest.fst() );
      }
    }
    return null;
  }

  private P<Integer, String> patternMatchStoichiometry(String kChems) {
    String pattern = "([0-9]+)\\s+(.*)";
    Pattern r = Pattern.compile(pattern);
    Matcher m = r.matcher(kChems);
    if (m.find()) {
      return new P<Integer, String>(Integer.parseInt(m.group(1)), m.group(2));
    }
    return null;
  }

  private void logMsgBrenda(String whatfailed) {
    System.out.format("Brenda (%s) did not resolve. Abort!\n", whatfailed);
  }

  private void abortBrenda(String whatfailed) {
    System.out.format("Brenda (%s) did not resolve. Abort!\n", whatfailed);
    System.exit(-1);
  }


}
