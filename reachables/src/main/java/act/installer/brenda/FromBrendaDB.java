package act.installer.brenda;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A class representing rows in some table in the BRENDA MySQL DB.  Adds a constraint that T must implement Serializable
 * so that implementing classes can be written into an on-disk index.
 * @param <T> The class extending this interface.  Used to "tie a knot" in the type signatures of methods exposed
 *           by this interface so that instances of implementing classes can be returned in a polymorphism-friendly way.
 */
public interface FromBrendaDB<T extends Serializable> {
  /**
   * Access the query used to fetch specific rows from this BRENDA DB table.  Parameters must be (in order) EC number,
   * literature reference, and organism name.
   * TODO: this might be better in an abstract class w/ parameter binding done by this method.
   * @return The SQL query used to fetch specific rows of this type from the BRENDA MySQL DB.
   */
  String getQuery();

  /**
   * Produce an instance of this object from the current row available via the resultSet parameter, assuming that
   * result set was constructed from the QUERY or ALL_QUERY fields (i.e. the sting returned by getQuery() or
   * getAllQuery()).
   * @param resultSet The result set from which to extract an instance of type T.
   * @return An instance of type T produced from the result set.
   * @throws SQLException
   */
  T fromResultSet(ResultSet resultSet) throws SQLException;

  /**
   * Returns the (one-indexed) index of the Literature field in the class's QUERY.  This should only be used for
   * filtering nested literature reference lists.
   * @return The one-indexed offset of the Literature field for a result set produced using this class's QUERY.
   */
  int getLiteratureField();
  // ResultSet fields for query that fetches all rows.

  /**
   * Returns the ALL_QUERY field for this class, which is a parameter-free query that will iterate over all rows in this
   * class's DB table.  ResultSet fields returned must include at leastany fields exposed by this object via getters
   * plus EC number, literature reference, and organism name.
   * @return A query string that will fetch all rows representing instances of this type.
   */
  String getAllQuery();

  /**
   * Used for key generation during on-disk index construction.
   * @return the one-indexed offset of the literature field in the result set produced by this class's ALL_QUERY.
   */
  int getLiteratureFieldForAllQuery();

  /**
   * Used for key generation during on-disk index construction.
   * @return the one-indexed offset of the ec number field in the result set produced by this class's ALL_QUERY.
   */
  int getECNumberFieldForAllQuery();

  /**
   * Used for key generation during on-disk index construction.
   * @return the one-indexed offset of the organism name field in the result set produced by this class's ALL_QUERY.
   */
  int getOrganismFieldForAllQuery();

  /**
   * Returns a string that should be used as the `column family` (name space) name for this class during on-disk index
   * generation.  This is a concession to the RocksDB API.
   * @return a string representing the `column family` (name space) for this class in an on-disk index.
   */
  String getColumnFamilyName();
}
