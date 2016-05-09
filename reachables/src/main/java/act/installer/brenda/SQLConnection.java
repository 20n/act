package act.installer.brenda;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class SQLConnection {

  public static final String QUERY_SUBSTRATES_PRODUCTS = StringUtils.join(new String[] {
          "select",
          "  EC_Number,", // 1
          "  Substrates,", // 2
          "  Commentary_Substrates,", // 3
          "  Literature_Substrates,", // 4
          "  Organism_Substrates,", // 5
          "  Products,", // 6
          "  Reversibility,", // 7
          "  id", // 8
          "from Substrates_Products",
  }, " ");

  public static final String QUERY_NATURAL_SUBSTRATES_PRODUCTS = StringUtils.join(new String[] {
          "select",
          "  EC_Number,",
          "  Natural_Substrates,",
          "  Commentary_Natural_Substrates,",
          "  Literature_Natural_Substrates,",
          "  Organism_Natural_Substrates,",
          "  Natural_Products,",
          "  Reversibility,",
          "  id",
          "from Natural_Substrates_Products",
  }, " ");

  public static final String QUERY_GET_SYNONYMS = StringUtils.join(new String[] {
          "select",
          "  lm.Ligand",
          "from ligand_molfiles lm1",
          "join ligand_molfiles lm on lm1.groupID = lm.groupID",
          "where lm1.Ligand = ?",
  }, " ");


  private Connection brendaConn;
  private Connection brendaLigandConn;

  public SQLConnection() {
  }

  public void connect(String host, Integer port, String username, String password) throws SQLException {
    String brendaConnectionUrl =
            String.format("jdbc:mysql://%s:%s/brenda", host, port == null ? "3306" : port.toString());
    String brendaLigandConnectionUrl =
            String.format("jdbc:mysql://%s:%s/brenda_ligand", host, port == null ? "3306" : port.toString());
    brendaConn = DriverManager.getConnection(brendaConnectionUrl, username, password);
    brendaLigandConn = DriverManager.getConnection(brendaLigandConnectionUrl, username, password);
  }

  public void disconnect() throws SQLException {
    if (!brendaConn.isClosed()) {
      brendaConn.close();
    }
    if (!brendaLigandConn.isClosed()) {
      brendaLigandConn.close();
    }
  }

  /**
   * A handy function that closes a result set when an iterator has hit the end.  Does some ugly stuff with exceptions
   * but needs to be used inside an iterator.
   * @param results The result set to check for another row.
   * @param stmt A statement to close when we're out of results.
   * @return True if the result set has more rows, false otherwise (after closing).
   */
  private static boolean hasNextHelper(ResultSet results, Statement stmt) {
    try {
      // TODO: is there a better way to do this?
      if (results.isLast()) {
        results.close(); // Tidy up if we find we're at the end.
        stmt.close();
        return false;
      } else {
        return true;
      }
    } catch (SQLException e) {
      /* Note: this is usually not a great thing to do.  In this circumstance we don't expect the
       * calling code to do anything but crash anyway, so... */
      throw new RuntimeException(e);
    }
  }


  private Iterator<BrendaRxnEntry> runSPQuery(final boolean isNatural) throws SQLException {
    String query = isNatural ? QUERY_NATURAL_SUBSTRATES_PRODUCTS : QUERY_SUBSTRATES_PRODUCTS;
    final PreparedStatement stmt = brendaConn.prepareStatement(query);
    final ResultSet results = stmt.executeQuery();
    return new Iterator<BrendaRxnEntry>() {
      @Override
      public boolean hasNext() {
        return hasNextHelper(results, stmt);
      }

      @Override
      public BrendaRxnEntry next() {
        try {
          results.next();
          Integer literatureSubstrates = results.getInt(4);

          if (results.wasNull()) {
            literatureSubstrates = null;
          }
          BrendaRxnEntry sp = new BrendaRxnEntry(
              results.getString(1),
              results.getString(2),
              results.getString(3),
              literatureSubstrates == null ? null : literatureSubstrates.toString(),
              results.getString(5),
              results.getString(6),
              results.getString(7),
              results.getInt(8),
              isNatural
          );
          return sp;
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }

      }
    };
  }

  /**
   * Query the DB's Substrates_Products table, producing an iterator of results rows.
   * @return An iterator that returns one Substrates_Products object at a time.
   * @throws SQLException
   */
  public Iterator<BrendaRxnEntry> getRxns() throws SQLException {
    return runSPQuery(false);
  }

  /**
   * Query the DB's Natural_Substrates_Products table, producing an iterator of results rows.
   * @return An iterator that returns one Substrates_Products object at a time.
   * @throws SQLException
   */
  public Iterator<BrendaRxnEntry> getNaturalRxns() throws SQLException {
    return runSPQuery(true);
  }

  /**
   * Look up all BRENDA synonyms for a particular chemical name.
   * @param name The name for which to search.
   * @return A list of synonyms.
   * @throws SQLException
   */
  public List<String> getSynonymsForChemicalName(String name) throws SQLException {
    try (PreparedStatement stmt = brendaLigandConn.prepareStatement(QUERY_GET_SYNONYMS)) {
      stmt.setString(1, name);
      try (ResultSet resultSet = stmt.executeQuery()) {
        List<String> synonyms = new ArrayList<>();
        while (resultSet.next()) {
          synonyms.add(resultSet.getString(1));
        }
        return synonyms;
      }
    }
  }

  /**
   * Iterate over all BRENDA ligands (from the ligands_molfiles table).
   * @return An iterator over all BRENDA ligands.
   * @throws SQLException
   */
  public Iterator<BrendaSupportingEntries.Ligand> getLigands() throws SQLException {
    final PreparedStatement stmt = brendaLigandConn.prepareStatement(BrendaSupportingEntries.Ligand.QUERY);
    final ResultSet results = stmt.executeQuery();

    return new Iterator<BrendaSupportingEntries.Ligand>() {
      @Override
      public boolean hasNext() {
        return hasNextHelper(results, stmt);
      }

      @Override
      public BrendaSupportingEntries.Ligand next() {
        try {
          results.next();
          return BrendaSupportingEntries.Ligand.fromResultSet(results);
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }

      }
    };
  }

  /**
   * Iterate over all BRENDA organisms.
   * @return An iterator over all BRENDA organisms.
   * @throws SQLException
   */
  public Iterator<BrendaSupportingEntries.Organism> getOrganisms() throws SQLException {
    final PreparedStatement stmt = brendaConn.prepareStatement(BrendaSupportingEntries.Organism.QUERY);
    final ResultSet results = stmt.executeQuery();

    return new Iterator<BrendaSupportingEntries.Organism>() {
      @Override
      public boolean hasNext() {
        return hasNextHelper(results, stmt);
      }

      @Override
      public BrendaSupportingEntries.Organism next() {
        try {
          results.next();
          return BrendaSupportingEntries.Organism.fromResultSet(results);
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public Iterator<BrendaChebiOntology.ChebiOntology> getChebiOntologies() throws SQLException {
    final PreparedStatement stmt = brendaConn.prepareStatement(BrendaChebiOntology.ChebiOntology.QUERY);
    final ResultSet results = stmt.executeQuery();

    return new Iterator<BrendaChebiOntology.ChebiOntology>() {
      @Override
      public boolean hasNext() {
        return SQLConnection.hasNextHelper(results, stmt);
      }

      @Override
      public BrendaChebiOntology.ChebiOntology next() {
        try {
          results.next();
          return BrendaChebiOntology.ChebiOntology.fromResultSet(results);
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }

      }
    };
  }

  public Iterator<BrendaChebiOntology.ChebiRelationship> getChebiRelationships(int relationshipType)
      throws SQLException {
    PreparedStatement stmt = brendaConn.prepareStatement(BrendaChebiOntology.ChebiRelationship.QUERY);
    stmt.setInt(1, relationshipType);
    final ResultSet results = stmt.executeQuery();

    return new Iterator<BrendaChebiOntology.ChebiRelationship>() {
      @Override
      public boolean hasNext() {
        return SQLConnection.hasNextHelper(results, stmt);
      }

      @Override
      public BrendaChebiOntology.ChebiRelationship next() {
        try {
          results.next();
          return BrendaChebiOntology.ChebiRelationship.fromResultSet(results);
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }

      }
    };
  }

  /**
   * Fetch all sequences corresponding to the specified reaction.
   * @param rxnEntry A reaction whose sequences to search for.
   * @return A list of all matching BRENDA sequence entries.
   * @throws SQLException
   */
  public List<BrendaSupportingEntries.Sequence> getSequencesForReaction(BrendaRxnEntry rxnEntry) throws SQLException{
    try (
        PreparedStatement stmt = BrendaSupportingEntries.Sequence.prepareStatement(brendaConn, rxnEntry);
        ResultSet resultSet = stmt.executeQuery();
    ) {
      List<BrendaSupportingEntries.Sequence> results = new ArrayList<>();
      while (resultSet.next()) {
        results.add(BrendaSupportingEntries.Sequence.sequenceFromResultSet(resultSet));
      }
      return results;
    }
  }


  // Helpers for reaction-associated data sets.

  /**
   * Get all values of a particular BRENDA DB type.
   * @param instance An instance to use when reading the table rows.
   * @param query The query to run against the BRENDA MySQL DB.
   * @param ecNumber The EC number to use when querying the DB (should come from a reaction).
   * @param literatureId The literature id to use when querying the DB (should come from a reaction).
   * @param organism The organism name to use when querying the DB (should come from a reaction).
   * @param <T> The type of data to retrieve; corresponds to a BRENDA DB table.
   * @return A list of all instances of the secified type that share the EC number, literature id, and organism name.
   * @throws SQLException
   */
  private <T extends FromBrendaDB<T> & Serializable> List<T> getRSValues(
      T instance, String query, String ecNumber, String literatureId, String organism) throws SQLException {
    try (PreparedStatement st = brendaConn.prepareStatement(query)) {
      st.setString(1, ecNumber);
      st.setString(2, "%" + literatureId + "%");
      st.setString(3, organism);
      try (ResultSet resultSet = st.executeQuery()) {
        List<T> results = new ArrayList<>();
        while (resultSet.next()) {
          if (BrendaSupportingEntries.findIdInList(resultSet.getString(instance.getLiteratureField()), literatureId)) {
            results.add(instance.fromResultSet(resultSet));
          }
          // TODO: log when we can't find the exact literature ID in the query results.
        }
        return results;
      }
    }
  }

  // TODO: these could probably be consolidated via a single polymorphic method.
  public List<BrendaSupportingEntries.KMValue> getKMValue(BrendaRxnEntry reaction) throws SQLException {
    return getRSValues(BrendaSupportingEntries.KMValue.INSTANCE, BrendaSupportingEntries.KMValue.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.SpecificActivity> getSpecificActivity(BrendaRxnEntry reaction)
      throws SQLException {
    return getRSValues(BrendaSupportingEntries.SpecificActivity.INSTANCE,
        BrendaSupportingEntries.SpecificActivity.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.OrganismCommentary> getOrganismCommentary(BrendaRxnEntry reaction)
      throws SQLException {
    return getRSValues(BrendaSupportingEntries.OrganismCommentary.INSTANCE,
        BrendaSupportingEntries.OrganismCommentary.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.GeneralInformation> getGeneralInformation(BrendaRxnEntry reaction)
      throws SQLException {
    return getRSValues(BrendaSupportingEntries.GeneralInformation.INSTANCE,
        BrendaSupportingEntries.GeneralInformation.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.Cofactor> getCofactors(BrendaRxnEntry reaction) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Cofactor.INSTANCE,
        BrendaSupportingEntries.Cofactor.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.Inhibitors> getInhibitors(BrendaRxnEntry reaction) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Inhibitors.INSTANCE,
        BrendaSupportingEntries.Inhibitors.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.ActivatingCompound> getActivatingCompounds(BrendaRxnEntry reaction)
      throws SQLException {
    return getRSValues(BrendaSupportingEntries.ActivatingCompound.INSTANCE,
        BrendaSupportingEntries.ActivatingCompound.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.KCatKMValue> getKCatKMValues(BrendaRxnEntry reaction) throws SQLException {
    return getRSValues(BrendaSupportingEntries.KCatKMValue.INSTANCE,
        BrendaSupportingEntries.KCatKMValue.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.Expression> getExpression(BrendaRxnEntry reaction) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Expression.INSTANCE,
        BrendaSupportingEntries.Expression.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.Subunits> getSubunits(BrendaRxnEntry reaction) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Subunits.INSTANCE,
        BrendaSupportingEntries.Subunits.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public List<BrendaSupportingEntries.Localization> getLocalization(BrendaRxnEntry reaction) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Localization.INSTANCE,
        BrendaSupportingEntries.Localization.QUERY,
        reaction.getEC(), reaction.getLiteratureRef(), reaction.getOrganism());
  }

  public BrendaSupportingEntries.RecommendNameTable fetchRecommendNameTable() throws SQLException {
    return BrendaSupportingEntries.RecommendNameTable.fetchRecommendedNameTable(brendaConn);
  }

  /**
   * Create an on-disk index of BRENDA data that supports reactions at the specified path.
   * @param path A path where the index will be stored.
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws RocksDBException
   * @throws SQLException
   */
  public void createSupportingIndex(File path)
      throws IOException, ClassNotFoundException, RocksDBException, SQLException {
    new BrendaSupportingEntries().constructOnDiskBRENDAIndex(path, this.brendaConn);
  }

  public void deleteSupportingIndex(File path) throws IOException {
    // With help from http://stackoverflow.com/questions/779519/delete-files-recursively-in-java.
    FileUtils.deleteDirectory(path);
  }

  public Pair<RocksDB, Map<String, ColumnFamilyHandle>> openSupportingIndex(File supportingIndex)
      throws RocksDBException {
    List<FromBrendaDB> instances = BrendaSupportingEntries.allFromBrendaDBInstances();
    List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>(instances.size() + 1);
    columnFamilyDescriptors.add(new ColumnFamilyDescriptor("default".getBytes()));
    for (FromBrendaDB instance : instances) {
      columnFamilyDescriptors.add(new ColumnFamilyDescriptor(instance.getColumnFamilyName().getBytes()));
    }
    List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>(columnFamilyDescriptors.size());

    DBOptions dbOptions = new DBOptions();
    dbOptions.setCreateIfMissing(false);
    RocksDB rocksDB = RocksDB.open(dbOptions, supportingIndex.getAbsolutePath(),
        columnFamilyDescriptors, columnFamilyHandles);
    Map<String, ColumnFamilyHandle> columnFamilyHandleMap = new HashMap<>(columnFamilyHandles.size());
    // TODO: can we zip these together more easily w/ Java 8?

    for (int i = 0; i < columnFamilyDescriptors.size(); i++) {
      ColumnFamilyDescriptor cfd = columnFamilyDescriptors.get(i);
      ColumnFamilyHandle cfh = columnFamilyHandles.get(i);
      columnFamilyHandleMap.put(new String(cfd.columnFamilyName(), BrendaSupportingEntries.UTF8), cfh);
    }

    return Pair.of(rocksDB, columnFamilyHandleMap);
  }
}
