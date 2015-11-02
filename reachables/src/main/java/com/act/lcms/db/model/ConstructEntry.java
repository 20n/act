package com.act.lcms.db.model;

import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.parser.TSVParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

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

public class ConstructEntry {
  public static final String TABLE_NAME = "constructs";

  public static String getTableName() {
    return TABLE_NAME;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    CONSTRUCT_ID(2, 1, "construct_id"),
    TARGET(3, 2, "target"),
    HOST(4, 3, "host"),
    // proteins and ko_locus are ignored for now.
    ;

    private final int offset;
    private final int insertUpdateOffset;
    private final String fieldName;

    DB_FIELD(int offset, int insertUpdateOffset, String fieldName) {
      this.offset = offset;
      this.insertUpdateOffset = insertUpdateOffset;
      this.fieldName = fieldName;
    }

    @Override
    public int getOffset() {
      return offset;
    }

    @Override
    public int getInsertUpdateOffset() {
      return insertUpdateOffset;
    }

    @Override
    public String getFieldName() {
      return fieldName;
    }

    @Override
    public String toString() {
      return this.fieldName;
    }

    public static String[] names() {
      DB_FIELD[] values = DB_FIELD.values();
      String[] names = new String[values.length];
      for (int i = 0; i < values.length; i++) {
        names[i] = values[i].getFieldName();
      }
      return names;
    }
  }

  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(DB_FIELD.names()));
  // id is auto-generated on insertion.
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<ConstructEntry> fromResultSet(ResultSet resultSet) throws SQLException {
    List<ConstructEntry> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      String constructId = resultSet.getString(DB_FIELD.CONSTRUCT_ID.getOffset());
      String target = resultSet.getString(DB_FIELD.TARGET.getOffset());
      String host = resultSet.getString(DB_FIELD.HOST.getOffset());

      results.add(new ConstructEntry(id, constructId, target, host));
    }

    return results;
  }

  protected static ConstructEntry expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<ConstructEntry> results = fromResultSet(resultSet);
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
  public static ConstructEntry getCompositionMapEntryById(DB db, Integer id) throws SQLException {
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
  public static ConstructEntry getCompositionMapEntryByCompositionId(DB db, String constructId)
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
  public static ConstructEntry getCompositionMapEntryByTarget(DB db, String target) throws SQLException {
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
    stmt.setString(DB_FIELD.CONSTRUCT_ID.getInsertUpdateOffset(), constructId);
    stmt.setString(DB_FIELD.TARGET.getInsertUpdateOffset(), target);
    stmt.setString(DB_FIELD.HOST.getInsertUpdateOffset(), host);
  }

  // TODO: this could return the number of parameters it bound to make it easier to set additional params.
  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, ConstructEntry c)
      throws SQLException {
    bindInsertOrUpdateParameters(stmt, c.getCompositionId(), c.getTarget(), c.getHost());
  }

  public static ConstructEntry insertCompositionMapEntry(
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
          return new ConstructEntry(id, constructId, target, host);
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
      "UPDATE", TABLE_NAME, "SET",
      StringUtils.join(UPDATE_STATEMENT_FIELDS_AND_BINDINGS.iterator(), ", "),
      "WHERE",
      "id = ?",
  }, " ");
  public static boolean updateCompositionMapEntry(DB db, ConstructEntry chem) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_COMPOSITION_MAP_ENTRY_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, chem);
      stmt.setInt(INSERT_UPDATE_FIELDS.size() + 1, chem.getId());
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

      ConstructEntry construct = getCompositionMapEntryByCompositionId(db, constructId);
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

  public ConstructEntry(Integer id, String constructId, String target, String host) {
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
