package act.installer.brenda;

import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


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

  public List<String> getSynonymsForChemicalName(String name) throws SQLException {
    PreparedStatement stmt = null;
    ResultSet resultSet = null;

    try {
      stmt = brendaLigandConn.prepareStatement(QUERY_GET_SYNONYMS);
      stmt.setString(1, name);
      resultSet = stmt.executeQuery();

      List<String> synonyms = new ArrayList<>();
      while (resultSet.next()) {
        synonyms.add(resultSet.getString(1));
      }
      return synonyms;
    } finally {
      if (resultSet != null) {
        resultSet.close();
      }
      if (stmt != null) {
        stmt.close();
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

  public List<BrendaSupportingEntries.Sequence> getSequencesForReaction(BrendaRxnEntry rxnEntry) throws SQLException{
    PreparedStatement stmt = BrendaSupportingEntries.Sequence.prepareStatement(brendaConn, rxnEntry);
    ResultSet resultSet = stmt.executeQuery();

    List<BrendaSupportingEntries.Sequence> results = new ArrayList<>();
    while (resultSet.next()) {
      results.add(BrendaSupportingEntries.Sequence.sequenceFromResultSet(resultSet));
    }
    return results;
  }

  // Helpers for reaction-associated data sets.
  private <T extends FromBrendaDB<T>> List<T> getRSValues(T instance, String query,
                                                          String ecNumber, String literatureId, String organism)
          throws SQLException {
    PreparedStatement st = brendaConn.prepareStatement(query);
    st.setString(1, ecNumber);
    st.setString(2, "%" + literatureId + "%");
    st.setString(3, organism);
    ResultSet resultSet = st.executeQuery();
    List<T> results = new ArrayList<>();
    while (resultSet.next()) {
      if (BrendaSupportingEntries.findIdInList(resultSet.getString(instance.getLiteratureField()), literatureId)) {
        results.add(instance.fromResultSet(resultSet));
      }
      // TODO: log when we can't find the exact literature ID in the query results.
    }
    return results;
  }

  // TODO: these could probably be consolidated via a single polymorphic method.
  public List<BrendaSupportingEntries.KMValue> getKMValue(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.KMValue.INSTANCE, BrendaSupportingEntries.KMValue.QUERY,
        ecNumber, literatureId, organism);
  }
  public List<BrendaSupportingEntries.SpecificActivity> getSpecificActivity(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.SpecificActivity.INSTANCE,
        BrendaSupportingEntries.SpecificActivity.QUERY,
        ecNumber,
        literatureId,
        organism);
  }
  public List<BrendaSupportingEntries.OrganismCommentary> getOrganismCommentary(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.OrganismCommentary.INSTANCE,
        BrendaSupportingEntries.OrganismCommentary.QUERY,
        ecNumber,
        literatureId,
        organism);
  }

  public List<BrendaSupportingEntries.GeneralInformation> getGeneralInformation(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.GeneralInformation.INSTANCE,
        BrendaSupportingEntries.GeneralInformation.QUERY,
        ecNumber,
        literatureId,
        organism);
  }

  public List<BrendaSupportingEntries.Cofactor> getCofactors(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Cofactor.INSTANCE,
        BrendaSupportingEntries.Cofactor.QUERY,
        ecNumber,
        literatureId,
        organism);
  }
  public List<BrendaSupportingEntries.Inhibitors> getInhibitors(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Inhibitors.INSTANCE,
        BrendaSupportingEntries.Inhibitors.QUERY,
        ecNumber,
        literatureId,
        organism);
  }

  public List<BrendaSupportingEntries.ActivatingCompound> getActivatingCompounds(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.ActivatingCompound.INSTANCE,
        BrendaSupportingEntries.ActivatingCompound.QUERY,
        ecNumber,
        literatureId,
        organism);
  }

  public List<BrendaSupportingEntries.KCatKMValue> getKCatKMValues(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.KCatKMValue.INSTANCE,
        BrendaSupportingEntries.KCatKMValue.QUERY,
        ecNumber,
        literatureId,
        organism);
  }

  public List<BrendaSupportingEntries.Expression> getExpression(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Expression.INSTANCE,
        BrendaSupportingEntries.Expression.QUERY,
        ecNumber,
        literatureId,
        organism);
  }

  public List<BrendaSupportingEntries.Subunits> getSubunits(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Subunits.INSTANCE,
        BrendaSupportingEntries.Subunits.QUERY,
        ecNumber,
        literatureId,
        organism);
  }

  public List<BrendaSupportingEntries.Localization> getLocalization(
      String ecNumber, String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.Localization.INSTANCE,
        BrendaSupportingEntries.Localization.QUERY,
        ecNumber,
        literatureId,
        organism);
  }
}
