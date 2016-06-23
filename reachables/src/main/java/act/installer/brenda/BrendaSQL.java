package act.installer.brenda;

import act.shared.ConsistentInChI;
import act.installer.sequence.BrendaEntry;
import act.installer.sequence.SequenceEntry;
import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Organism;
import act.shared.Reaction;
import act.shared.Seq;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.StepDirection;

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

  /**
   * Add/merge all BRENDA chemicals into the chemicals collection in the DB, marking chemicals as cofactors if they
   * appear in a list of cofactors.
   *
   * @param cofactorInchis A list of cofactors' InChIs, which are used to tag chemicals as cofactors.
   * @throws SQLException
   */
  public void installChemicals(List<String> cofactorInchis) throws SQLException {
    int numEntriesProcessed = 0;
    SQLConnection brendaDB = new SQLConnection();
    // This expects an SSH tunnel to be running, one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 3306, "brenda_user", "");

    // Convert cofactor InChIs list to a set for faster lookup than List.contains.
    Set<String> cofactorInchisSet = new HashSet<>(cofactorInchis);

    long cofactor_num = 0;
    Iterator<BrendaSupportingEntries.Ligand> ligands = brendaDB.getLigands();
    while (ligands.hasNext()) {
      // this ligand iterator will not give us unique chemical
      // inchis. so we have to lookup what inchis we have already seen
      // and if a repeat shows up, we only add the name/other metadata...

      BrendaSupportingEntries.Ligand ligand = ligands.next();
      Chemical c = createActChemical(ligand);
      if (cofactorInchisSet.contains(c.getInChI())) {
        c.setAsCofactor();
      }

      if (c.getUuid() == -1) {
        // indeed a new chemical inchi => install new

        /*
           This use of a count-based installid counter
           will not be safe if multiple processes are
           writing to the DB. E.g., if we distribute the installer

           If that is the case, then use some remote synchronization
           to ensure that the counter is incremented atomically globally.
        */
        long installid = db.getNextAvailableChemicalDBid();
        db.submitToActChemicalDB(c, installid);
        if (c.isCofactor()) {
          System.out.format("Installed cofactor #%d, dbid #%d\n", cofactor_num++, installid);
        }

        numEntriesProcessed++;
      } else {
        // chemical already seen, just merge with existing in db
        // submitToActChemicalDB checks pre-existing, and if yes
        // ignores the provided installid, and just merges with existing
        db.submitToActChemicalDB(c, (long) -1);
      }
    }

    brendaDB.disconnect();
    System.out.format("Main.addChemicals: Num processed %d\n", numEntriesProcessed);
  }

  /**
   * Create a new chemical entry for the DB, falling back to a FAKE InChI if the chemical doesn't have one defined.
   * @param ligand The BRENDA DB entry to convert to a chemical object.
   * @return A Chemical object representing the specified BRENDA ligand.
   */
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

    String inchi = ConsistentInChI.consistentInChI(brenda_inchi, "BRENDA SQL install");

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

  /**
   * Convert an InChI to SMILES using Indigo.
   * @param inchi An InChI to convert.
   * @return The corresponding SMILES.
   */
  private String inchi2smiles(String inchi) {
    Indigo ind = new Indigo();
    IndigoInchi ic = new IndigoInchi(ind);
    try {
      return ic.loadMolecule(inchi).canonicalSmiles();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Install all BRENDA reactions in the DB specified in the constructor.
   *
   * This installation follows a three step process for each reaction:
   * 1) A BRENDA reaction is added to the DB without protein information (which includes sequence references); this
   * generates a new id for the reaction.
   * 2) The reaction's sequence entries are added to the DB with references to the reaction's id.
   * 3) The reaction is updated with a protein entry, which contains references to the sequences' ids created in (2).
   *
   * The bi-directional id references require that one object (reaction or sequence) have its id generated first, which
   * means creating a new but incomplete object in the DB.
   *
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws RocksDBException
   * @throws SQLException
   */
  public void installReactions() throws IOException, ClassNotFoundException, RocksDBException, SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    System.out.println("Connecting to brenda DB.");
    // This expects an SSH tunnel to be running, like the one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 3306, "brenda_user", "");
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

    numEntriesAdded += installReactions(brendaDB, rocksDB, columnFamilyHandleMap, recommendNameTable,
        brendaDB.getRxns(), numEntriesAdded);
    numEntriesAdded += installReactions(brendaDB, rocksDB, columnFamilyHandleMap, recommendNameTable,
        brendaDB.getNaturalRxns(), numEntriesAdded);

    rocksDB.close();
    brendaDB.disconnect();

    if (this.cleanUpSupportingIndex) {
      brendaDB.deleteSupportingIndex(supportingIndex);
    }

    System.out.format("Main.addBrendaReactionsFromSQL: Num entries added %d\n", numEntriesAdded);
  }

  /**
   * Actually installs reactions given connections to a BRENDA DB and its corresponding on-disk indexes.
   *
   * @param brendaDB A connection to a BRENDA SQL DB.
   * @param rocksDB A handle to the local RocksDB index of entities that support BRENDA reactions.
   * @param columnFamilyHandleMap A handle to the RocksDB type -> column family map, required for lookups.
   * @param recommendNameTable A brenda RecommendedNameTable entry for reactions, which contains names for EC numbers.
   * @param rxns An iterator over all BRENDA reaction entries.
   * @param numEntriesAdded A number of reactions already added, used for progress reporting.
   * @return An updated number of reactions added to the DB.
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws RocksDBException
   * @throws SQLException
   */
  private int installReactions(
      SQLConnection brendaDB, RocksDB rocksDB, Map<String, ColumnFamilyHandle> columnFamilyHandleMap,
      BrendaSupportingEntries.RecommendNameTable recommendNameTable, Iterator<BrendaRxnEntry> rxns, int numEntriesAdded)
      throws IOException, ClassNotFoundException, RocksDBException, SQLException {
    while (rxns.hasNext()) {
      BrendaRxnEntry brendaTblEntry = rxns.next();
      Reaction r = createActReaction(brendaTblEntry);
      // Store the reaction and get the id so we can create seq -> reaction references.
      int id = db.submitToActReactionDB(r);

      // Extract the organism id once so it can be used for all sequences.
      long orgID = getOrgID(brendaTblEntry.getOrganism());
      // SequenceEntry doesn't expose a source accessor, so save the source alongside the sequence when we convert it.
      List<Pair<Seq.AccDB, SequenceEntry>> sequenceEntry =
          getSequenceInfo(brendaTblEntry, id, r, orgID, brendaDB, rocksDB, columnFamilyHandleMap);

      // Store the sequences (which now reference the reaction) and collect all the ids to add to the reaction.
      List<Long> sequenceIds = new ArrayList<>();
      for (Pair<Seq.AccDB, SequenceEntry> seqSrc : sequenceEntry) {
        sequenceIds.add(Long.valueOf(seqSrc.getRight().writeToDB(db, seqSrc.getLeft())));
      }

      // Generate the reaction's protein info with the freshly generated sequence ids.
      JSONObject proteinInfo = getProteinInfo(brendaTblEntry, orgID, sequenceIds, rocksDB,
          columnFamilyHandleMap, recommendNameTable);
      r.addProteinData(proteinInfo);
      // Update the reaction in the DB to write the protein data and sequence references.
      db.updateActReaction(r, id);

      numEntriesAdded++;
      if (numEntriesAdded % 10000 == 0) {
        System.out.println("Processed " + numEntriesAdded + " reactions.");
      }
    }

    return numEntriesAdded;
  }

  public void installOrganisms() throws SQLException {
    int numEntriesAdded = 0;
    SQLConnection brendaDB = new SQLConnection();
    // This expects an SSH tunnel to be running, like the one created with the command
    // $ ssh -L10000:brenda-mysql-1.ciuibkvm9oks.us-west-1.rds.amazonaws.com:3306 ec2-user@ec2-52-8-241-102.us-west-1.compute.amazonaws.com
    brendaDB.connect("127.0.0.1", 3306, "brenda_user", "");

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

  public void installChebiApplications() throws IOException, SQLException {
    SQLConnection brendaDB = new SQLConnection();
    brendaDB.connect("127.0.0.1", 3306, "brenda_user", "");
    BrendaChebiOntology brendaChebiOntology = new BrendaChebiOntology();
    brendaChebiOntology.addChebiApplications(db, brendaDB);
  }


  /**
   * Create a reaction object from a BRENDA reaction entry.  Doesn't do anything with protein info.
   *
   * @param entry A BRENDA reaction entry to convert to a Reaction object.
   * @return A Reaction object representing the direction, substrates/products, and EC number of the BRENDA entry.
   */
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

    // We do not get *modified* cofactor reactants from the raw data
    // They are usually just stuffed into the substrates/products. We
    // leave these fields empty here, and let the biointerpretation layer
    // infer those from within the {substrates, products} and move them.
    Map<Long, Integer> substrateCofactors = new HashMap<>();
    Map<Long, Integer> productCofactors = new HashMap<>();

    // Cofactor table entries in BRENDA 
    // are attached to reactions, so semantically correspond to coenzymes
    // (those that don't get modified in the reaction). The ones that
    // get modified should go into {substrate, product}Cofactors above.
    // But that comes in un-populated from the raw data, but will be 
    // inferred by the biointerpretation layer.
    // TODO: Later we will look into the "Cofactors" table of BRENDA SQL
    // and populate this field with it. 
    // E.g., http://brenda-enzymes.org/enzyme.php?ecno=1.3.1.1#COFACTOR
    Map<Long, Integer> coenzymes = new HashMap<>();

    String readable = constructReadable(org, sub, prd, REVERSIBILITY.brendaCode(rev));

    Long[] substrates_ids = substrates.keySet().toArray(new Long[0]);
    Long[] products_ids = products.keySet().toArray(new Long[0]);

    Long[] substrateCofactors_ids = substrateCofactors.keySet().toArray(new Long[0]);
    Long[] productCofactors_ids = productCofactors.keySet().toArray(new Long[0]);

    Long[] coenzyme_ids = coenzymes.keySet().toArray(new Long[0]);

    Reaction rxn = new Reaction(-1L, 
        substrates_ids, products_ids, 
        substrateCofactors_ids, productCofactors_ids,
        coenzyme_ids,
        ecnum, 
        // TODO: see if brenda specifies the conversion direction
        ConversionDirectionType.LEFT_TO_RIGHT,
        // TODO: see if brenda specifies the step direction
        StepDirection.LEFT_TO_RIGHT,
        readable,
        Reaction.RxnDetailType.CONCRETE);

    rxn.addReference(Reaction.RefDataSource.PMID, litref);

    for (Long s : substrates.keySet())
      rxn.setSubstrateCoefficient(s, substrates.get(s));
    for (Long p : products.keySet())
      rxn.setProductCoefficient(p, products.get(p));

    rxn.setDataSource(Reaction.RxnDataSource.BRENDA);

    return rxn;
  }

  /**
   * Look up an entry in a RocksDB by column family and key.
   *
   * @param rocksDB The DB to access.
   * @param cfh A handle to the appropriate column family.
   * @param key The key for which to search.
   * @param <T> The type of object to use when deserializing the list of values for the specified column family and key.
   * @return A list of objects from the DB corresponding to the specified column family and key.
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws RocksDBException
   */
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

  private List<Pair<Seq.AccDB, SequenceEntry>> getSequenceInfo(
      BrendaRxnEntry sqlrxn, long rxnId, Reaction rxn, Long orgId, SQLConnection sqldb,
      RocksDB rocksDB, Map<String, ColumnFamilyHandle> columnFamilyHandleMap)
      throws ClassNotFoundException, IOException, RocksDBException, SQLException {

    List<BrendaSupportingEntries.Sequence> brendaSequences = sqldb.getSequencesForReaction(sqlrxn);
    List<Pair<Seq.AccDB, SequenceEntry>> sequences = new ArrayList<>(brendaSequences.size());

    // ADD sequence information
    for (BrendaSupportingEntries.Sequence seqdata : brendaSequences) {
      SequenceEntry seq = BrendaEntry.initFromBrendaEntry(rxnId, rxn, sqlrxn, seqdata, orgId);
      Seq.AccDB src = Seq.AccDB.brenda;
      switch (seqdata.getSource()) {
        // These strings were copied from the BRENDA MySQL dump.
        case "TrEMBL":
          src = Seq.AccDB.trembl;
          break;
        case "Swiss-Prot":
          src = Seq.AccDB.swissprot;
          break;
        // Note: there is intentionally no default here, as the default value (Seq.AccDB.brenda) is already set.
      }
      sequences.add(Pair.of(src, seq));
    }

    return sequences;
  }

  /**
   * Get protein info for a particular reaction from an on-disk index (RockDB) of BRENDA supporting entities.
   *
   * This assumes that the sequences corresponding to the reaction have already been installed; their ids will be added
   * to the protein structure.
   *
   * @param sqlrxn The reaction whose protein data to source.
   * @param orgid The id of the associated organism to store on the protein entry.
   * @param sequenceIds A list of ids of sequence documents associated with this reaction.
   * @param rocksDB A handle to the on-disk index of supporting entities from BRENDA.
   * @param columnFamilyHandleMap A map of column family names to handles within the specified RocksDB instance.
   * @param recommendNameTable The table to use when fetching recommended names for the specified reaction.
   * @return A JSON object containing attributes for the protein represented by the specified reaction.
   * @throws ClassNotFoundException
   * @throws IOException
   * @throws RocksDBException
   * @throws SQLException
   */
  private JSONObject getProteinInfo(BrendaRxnEntry sqlrxn, Long orgid, List<Long> sequenceIds,
                                    RocksDB rocksDB, Map<String, ColumnFamilyHandle> columnFamilyHandleMap,
                                    BrendaSupportingEntries.RecommendNameTable recommendNameTable)
      throws ClassNotFoundException, IOException, RocksDBException, SQLException {
    String org = sqlrxn.getOrganism();
    String litref = sqlrxn.getLiteratureRef();

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
      // Add sequence ids, produced when seq objects are stored in the DB.
      JSONArray seqs = new JSONArray();
      for (Long id : sequenceIds) {
        seqs.put(id);
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

  /**
   * Create protein entries for a BRENDA reaction by fetching data directly from the BRENDA DB.
   *
   * Warning: direct DB access for reaction-associated entities is very, very slow compared to constructing an
   * on-disk index of supporting entries as a pre-processing step (the latter should be the default installer behavior).
   * This method is only for use when an on-disk index of BRENDA data is not an option, which hopefully will never
   * be the case.
   *
   * @param sqlrxn The reaction whose protein data to source.
   * @param orgid The id of the associated organism to store on the protein entry.
   * @param sequenceIds A list of ids of sequence documents associated with this reaction.
   * @param sqldb A connection to the BRENDA DB from which to fetch protein info.
   * @param recommendNameTable The table to use when fetching recommended names for the specified reaction.
   * @return A JSON object containing attributes for the protein represented by the specified reaction.
   * @throws SQLException
   */
  private JSONObject getProteinInfo(BrendaRxnEntry sqlrxn, Long orgid,
                                    List<Long> sequenceIds, SQLConnection sqldb,
                                    BrendaSupportingEntries.RecommendNameTable recommendNameTable)
      throws SQLException {
    String org = sqlrxn.getOrganism();
    String litref = sqlrxn.getLiteratureRef();

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
      // Add sequence ids, produced when seq objects are stored in the DB.
      JSONArray seqs = new JSONArray();
      for (Long id : sequenceIds) {
        seqs.put(id);
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

  /**
   * Look up an organism in the DB by name and return its id if it exists.
   *
   * @param organism The organism name to look up.
   * @return The organism's id in the DB, or -1 if it wasn't found.
   */
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
