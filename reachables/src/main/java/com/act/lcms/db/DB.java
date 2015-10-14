package com.act.lcms.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DB implements AutoCloseable {
  public enum OPERATION_PERFORMED {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    ERROR,
  }

  Connection conn;

  public DB connectToDB(String connStr) throws ClassNotFoundException, SQLException {
    /* Explicitly load the PostgreSQL driver class before trying to connect.
     * See https://jdbc.postgresql.org/documentation/94/load.html. */
    Class.forName("org.postgresql.Driver");
    this.conn = DriverManager.getConnection(connStr);
    return this;
  }

  public Connection getConn() {
    return conn;
  }

  @Override
  public void close() throws SQLException {
    if (conn != null && !conn.isClosed()) {
      conn.close();
    }
  }
}
