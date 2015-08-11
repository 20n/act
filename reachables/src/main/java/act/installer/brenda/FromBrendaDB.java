package act.installer.brenda;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface FromBrendaDB<T> {
    T fromResultSet(ResultSet resultSet) throws SQLException;
    int getLiteratureField();

}
