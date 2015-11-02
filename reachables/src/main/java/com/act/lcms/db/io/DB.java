package com.act.lcms.db.io;

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

  public static final String DEFAULT_HOST = "localhost";
  public static final Integer DEFAULT_PORT = 5432;
  public static final String DEFAULT_DB_NAME = "lcms";

  Connection conn;

  public DB connectToDB(String connStr) throws ClassNotFoundException, SQLException {
    /* Explicitly load the PostgreSQL driver class before trying to connect.
     * See https://jdbc.postgresql.org/documentation/94/load.html. */
    Class.forName("org.postgresql.Driver");
    this.conn = DriverManager.getConnection(connStr);
    return this;
  }

  public DB connectToDB(String host, Integer port, String databaseName, String user, String password)
      throws ClassNotFoundException, SQLException {
    Class.forName("org.postgresql.Driver");
    String url = String.format("jdbc:postgresql://%s:%d/%s",
        host == null ? DEFAULT_HOST : host,
        port == null ? DEFAULT_PORT : port,
        databaseName == null ? DEFAULT_DB_NAME : databaseName);
    this.conn = DriverManager.getConnection(url, user, password);
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
