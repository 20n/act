package com.act.lcms.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConstructMapEntry {
  public static final String TABLE_NAME = "constructs";

  public static String getTableName() {
    return TABLE_NAME;
  }

  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(
      "id", // 1
      "construct_id", // 2
      "target", // 3
      "host" // 4
      // proteins and ko_locus are ignored for now.
  ));
  // id is auto-generated on insertion.
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<ConstructMapEntry> fromResultSet(ResultSet resultSet) throws SQLException {
    List<ConstructMapEntry> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(1);
      String constructId = resultSet.getString(2);
      String target = resultSet.getString(3);
      String host = resultSet.getString(4);

      results.add(new ConstructMapEntry(id, constructId, target, host));
    }

    return results;
  }

  protected static ConstructMapEntry expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<ConstructMapEntry> results = fromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }

  // Select
  public static final String QUERY_GET_COMPOSITION_MAP_ENTRY_BY_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where id = ?",
  }, " ");
  public static ConstructMapEntry getCompositionMapEntryById(DB db, Integer id) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_COMPOSITION_MAP_ENTRY_BY_ID)) {
      stmt.setInt(1, id);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("id = %d", id));
      }
    }
  }

  public static final String QUERY_GET_COMPOSITION_MAP_ENTRY_BY_COMPOSITION_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where construct_id = ?",
  }, " ");
  public static ConstructMapEntry getCompositionMapEntryByCompositionId(DB db, String constructId)
      throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_COMPOSITION_MAP_ENTRY_BY_COMPOSITION_ID)) {
      stmt.setString(1, constructId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("construct_id = %s", constructId));
      }
    }
  }

  public static final String QUERY_GET_COMPOSITION_MAP_ENTRY_BY_TARGET = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where target = ?",
  }, " ");
  public static ConstructMapEntry getCompositionMapEntryByTarget(DB db, String target) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_COMPOSITION_MAP_ENTRY_BY_TARGET)) {
      stmt.setString(1, target);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("target = %s", target));
      }
    }
  }

  // Insert/Update
  public static final String QUERY_INSERT_COMPOSITION_MAP_ENTRY = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = constructId
      "?,", // 2 = target
      "?", // 3 = host
      ")"
  }, " ");

  protected static void bindInsertOrUpdateParameters(
      PreparedStatement stmt, String constructId, String target, String host) throws SQLException {
    stmt.setString(1, constructId);
    stmt.setString(2, target);
    stmt.setString(3, host);
  }

  // TODO: this could return the number of parameters it bound to make it easier to set additional params.
  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, ConstructMapEntry c)
      throws SQLException {
    bindInsertOrUpdateParameters(stmt, c.getCompositionId(), c.getTarget(), c.getHost());
  }

  public static ConstructMapEntry insertCompositionMapEntry(
      DB db, String constructId, String target, String host) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt =
             conn.prepareStatement(QUERY_INSERT_COMPOSITION_MAP_ENTRY, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, constructId, target, host);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new ConstructMapEntry(id, constructId, target, host);
        } else {
          System.err.format("ERROR: could not retrieve autogenerated key for construct map entry %s\n",
              constructId);
          return null;
        }
      }
    }
  }

  protected static final List<String> UPDATE_STATEMENT_FIELDS_AND_BINDINGS;

  static {
    List<String> fields = new ArrayList<>(INSERT_UPDATE_FIELDS.size());
    for (String field : INSERT_UPDATE_FIELDS) {
      fields.add(String.format("%s = ?", field));
    }
    UPDATE_STATEMENT_FIELDS_AND_BINDINGS = Collections.unmodifiableList(fields);
  }
  public static final String QUERY_UPDATE_COMPOSITION_MAP_ENTRY_BY_ID = StringUtils.join(new String[] {
      "UPDATE ", TABLE_NAME, "SET",
      StringUtils.join(UPDATE_STATEMENT_FIELDS_AND_BINDINGS.iterator(), ", "),
      "WHERE",
      "id = ?", // 4
  }, " ");
  public static boolean updateCompositionMapEntry(DB db, ConstructMapEntry chem) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_COMPOSITION_MAP_ENTRY_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, chem);
      stmt.setInt(4, chem.getId());
      return stmt.executeUpdate() > 0;
    }
  }

  public static List<Pair<Integer, DB.OPERATION_PERFORMED>> insertOrUpdateCompositionMapEntrysFromTSV(
      DB db, TSVParser parser) throws SQLException{
    List<Map<String, String>> entries = parser.getResults();
    List<Pair<Integer, DB.OPERATION_PERFORMED>> operationsPerformed = new ArrayList<>(entries.size());
    for (Map<String, String> entry : entries) {
      String constructId = entry.get("id");
      String target = entry.get("target");
      String host = entry.get("host");
      if (constructId== null || constructId.isEmpty() ||
          target == null || target.isEmpty()) {
        System.err.format("WARNING: missing required field for construct '%s', skipping.\n", constructId);
        continue;
      }

      ConstructMapEntry construct = getCompositionMapEntryByCompositionId(db, constructId);
      DB.OPERATION_PERFORMED op = null;
      if (construct == null) {
        construct = insertCompositionMapEntry(db, constructId, target, host);
        op = DB.OPERATION_PERFORMED.CREATE;
      } else {
        construct.setCompositionId(constructId);
        construct.setTarget(target);
        construct.setHost(host);
        updateCompositionMapEntry(db, construct);
        op = DB.OPERATION_PERFORMED.UPDATE;
      }

      // Chem should only be null if we couldn't insert the row into the DB.
      if (construct == null) {
        operationsPerformed.add(Pair.of((Integer)null, DB.OPERATION_PERFORMED.ERROR));
      } else {
        operationsPerformed.add(Pair.of(construct.getId(), op));
      }
    }
    return operationsPerformed;
  }

  private Integer id;
  private String constructId;
  private String target;
  private String host;

  public ConstructMapEntry(Integer id, String constructId, String target, String host) {
    this.id = id;
    this.constructId = constructId;
    this.target = target;
    this.host = host;
  }

  public Integer getId() {
    return id;
  }

  public String getCompositionId() {
    return constructId;
  }

  public void setCompositionId(String constructId) {
    this.constructId = constructId;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

}
