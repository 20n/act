package act.installer.brenda;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface FromBrendaDB<T> {
  T fromResultSet(ResultSet resultSet) throws SQLException;
  int getLiteratureField();
  // ResultSet fields for query that fetches all rows.
  String getAllQuery();
  int getLiteratureFieldForAllQuery();
  int getECNumberFieldForAllQuery();
  int getOrganismFieldForAllQuery();
  String getColumnFamilyName();
}
