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

public class ConstructEntry extends BaseDBModel<ConstructEntry> {
  public static final String TABLE_NAME = "constructs";
  protected static final ConstructEntry INSTANCE = new ConstructEntry();

  public static ConstructEntry getInstance() {
    return INSTANCE;
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
  protected static final List<String> INSERT_UPDATE_FIELDS = INSTANCE.makeInsertUpdateFields();

  @Override
  public String getTableName() {
    return TABLE_NAME;
  }

  @Override
  public List<String> getAllFields() {
    return ALL_FIELDS;
  }

  @Override
  public List<String> getInsertUpdateFields() {
    return INSERT_UPDATE_FIELDS;
  }

  @Override
  protected List<ConstructEntry> fromResultSet(ResultSet resultSet) throws SQLException {
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

  @Override
  protected ConstructEntry expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<ConstructEntry> results = fromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }

  protected static final String GET_BY_ID_QUERY = INSTANCE.makeGetByIDQuery();
  @Override
  protected String getGetByIDQuery() {
    return GET_BY_ID_QUERY;
  }

  // Select
  public static final String QUERY_GET_COMPOSITION_MAP_ENTRY_BY_COMPOSITION_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where construct_id = ?",
  }, " ");
  public ConstructEntry getCompositionMapEntryByCompositionId(DB db, String constructId)
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
  public ConstructEntry getCompositionMapEntryByTarget(DB db, String target) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_COMPOSITION_MAP_ENTRY_BY_TARGET)) {
      stmt.setString(1, target);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("target = %s", target));
      }
    }
  }

  // Insert/Update
  protected static final String INSERT_QUERY = INSTANCE.makeInsertQuery();
  @Override
  public String getInsertQuery() {
    return INSERT_QUERY;
  }

  protected static final String UPDATE_QUERY = INSTANCE.makeUpdateQuery();
  @Override
  public String getUpdateQuery() {
    return UPDATE_QUERY;
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, String constructId, String target, String host) throws SQLException {
    stmt.setString(DB_FIELD.CONSTRUCT_ID.getInsertUpdateOffset(), constructId);
    stmt.setString(DB_FIELD.TARGET.getInsertUpdateOffset(), target);
    stmt.setString(DB_FIELD.HOST.getInsertUpdateOffset(), host);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, ConstructEntry c)
      throws SQLException {
    bindInsertOrUpdateParameters(stmt, c.getCompositionId(), c.getTarget(), c.getHost());
  }

  public static List<Pair<Integer, DB.OPERATION_PERFORMED>> insertOrUpdateCompositionMapEntriesFromTSV(
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

      ConstructEntry construct = INSTANCE.getCompositionMapEntryByCompositionId(db, constructId);
      DB.OPERATION_PERFORMED op = null;
      if (construct == null) {
        construct = INSTANCE.insert(db, new ConstructEntry(null, constructId, target, host));
        op = DB.OPERATION_PERFORMED.CREATE;
      } else {
        construct.setCompositionId(constructId);
        construct.setTarget(target);
        construct.setHost(host);
        INSTANCE.update(db, construct);
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

  private ConstructEntry() { }

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
