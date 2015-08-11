package act.installer.brenda;

import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

  private Iterator<BrendaRxnEntry> runSPQuery(final boolean isNatural) throws SQLException {
    String query = isNatural ? QUERY_NATURAL_SUBSTRATES_PRODUCTS : QUERY_SUBSTRATES_PRODUCTS;
    PreparedStatement stmt = brendaConn.prepareStatement(query);
    final ResultSet results = stmt.executeQuery();
    return new Iterator<BrendaRxnEntry>() {
      @Override
      public boolean hasNext() {
        try {
          // TODO: is there a better way to do this?
          if (results.isAfterLast()) {
            results.close(); // Tidy up if we find we're at the end.
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

  // Helpers for reaction-associated data sets.
  private <T extends FromBrendaDB<T>> List<T> getRSValues(T instance, String query,
                                                          String literatureId, String organism)
          throws SQLException {
    PreparedStatement st = brendaConn.prepareStatement(query);
    st.setString(1, "%" + literatureId + "%");
    st.setString(2, organism);
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

  public List<BrendaSupportingEntries.KMValue> getKMValue(String literatureId, String organism) throws SQLException {
    return getRSValues(BrendaSupportingEntries.KMValue.INSTANCE, BrendaSupportingEntries.KMValue.QUERY,
            literatureId, organism);
  }
  public List<BrendaSupportingEntries.SpecificActivity> getSpecificActivity(String literatureId, String organism)
          throws SQLException {
    return getRSValues(BrendaSupportingEntries.SpecificActivity.INSTANCE,
            BrendaSupportingEntries.SpecificActivity.QUERY,
            literatureId,
            organism);
  }
  public List<BrendaSupportingEntries.OrganismCommentary> getOrganismCommentary(String literatureId, String organism)
          throws SQLException {
    return getRSValues(BrendaSupportingEntries.OrganismCommentary.INSTANCE,
            BrendaSupportingEntries.OrganismCommentary.QUERY,
            literatureId,
            organism);
  }

  public List<BrendaSupportingEntries.GeneralInformation> getGeneralInformation(String literatureId, String organism)
          throws SQLException {
    return getRSValues(BrendaSupportingEntries.GeneralInformation.INSTANCE,
            BrendaSupportingEntries.GeneralInformation.QUERY,
            literatureId,
            organism);
  }

  public List<BrendaSupportingEntries.Cofactor> getCofactors(String literatureId, String organism)
          throws SQLException {
    return getRSValues(BrendaSupportingEntries.Cofactor.INSTANCE,
            BrendaSupportingEntries.Cofactor.QUERY,
            literatureId,
            organism);
  }
  public List<BrendaSupportingEntries.Inhibitors> getInhibitors(String literatureId, String organism)
          throws SQLException {
    return getRSValues(BrendaSupportingEntries.Inhibitors.INSTANCE,
            BrendaSupportingEntries.Inhibitors.QUERY,
            literatureId,
            organism);
  }

  public List<BrendaSupportingEntries.ActivatingCompound> getActivatingCompounds(String literatureId, String organism)
          throws SQLException {
    return getRSValues(BrendaSupportingEntries.ActivatingCompound.INSTANCE,
            BrendaSupportingEntries.ActivatingCompound.QUERY,
            literatureId,
            organism);
  }

}
