package com.act.lcms.db;

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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SampleWell {
  public static final String TABLE_NAME = "wells_samples";

  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(
      "id", // 1
      "plate_id", // 2
      "plate_row", // 3
      "plate_column", // 4
      "msid", // 5
      "composition", // 6
      "chemical", // 7
      "note" // 8
  ));

  // id is auto-generated on insertion.
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<SampleWell> sampleWellsFromResultSet(ResultSet resultSet) throws SQLException {
    List<SampleWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(1);
      Integer plateId = resultSet.getInt(2);
      Integer plateRow = resultSet.getInt(3);
      Integer plateColumn = resultSet.getInt(4);
      String msid = resultSet.getString(5);
      String composition = resultSet.getString(6);
      String chemical = resultSet.getString(7);
      String note = resultSet.getString(8);

      results.add(new SampleWell(id, plateId, plateRow, plateColumn, msid, composition, chemical, note));
    }
    return results;
  }

  protected static SampleWell expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<SampleWell> results = sampleWellsFromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }

  // Select
  public static final String QUERY_GET_SAMPLE_WELL_BY_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where id = ?",
  }, " ");

  public static SampleWell getSampleWellById(DB db, Integer id) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_SAMPLE_WELL_BY_ID)) {
      stmt.setInt(1, id);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("id = %d", id));
      }
    }
  }

  public static final String QUERY_GET_SAMPLE_WELL_BY_PLATE_ID = StringUtils.join(new String[] {
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where plate_id = ?",
  }, " ");

  public static List<SampleWell> getSampleWellsByPlateId(DB db, Integer plateId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_SAMPLE_WELL_BY_PLATE_ID)) {
      stmt.setInt(1, plateId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return sampleWellsFromResultSet(resultSet);
      }
    }
  }

  // TODO: add more access patterns.

  // Insert/Update
  public static final String QUERY_INSERT_SAMPLE_WELL = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = plateId
      "?,", // 2 = plateRow
      "?,", // 3 = plateColumn
      "?,", // 4 = msid
      "?,", // 5 = composition
      "?,", // 6 = chemical
      "?",  // 7 = note
      ")"
  }, " ");

  protected static void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String msid, String composition, String chemical, String note) throws SQLException {
    stmt.setInt(1, plateId);
    stmt.setInt(2, plateRow);
    stmt.setInt(3, plateColumn);
    stmt.setString(4, msid);
    stmt.setString(5, composition);
    stmt.setString(6, chemical);
    stmt.setString(7, note);
  }

  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, SampleWell sw) throws SQLException {
    bindInsertOrUpdateParameters(stmt, sw.getPlateId(), sw.getPlateRow(), sw.getPlateColumn(),
        sw.getMsid(), sw.getComposition(), sw.getChemical(), sw.getNote());
  }

  public static SampleWell insertSampleWell(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String msid, String composition, String chemical, String note) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_INSERT_SAMPLE_WELL, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, plateId, plateRow, plateColumn, msid, composition, chemical, note);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new SampleWell(id, plateId, plateRow, plateColumn, msid, composition, chemical, note);
        } else {
          // TODO: log error here.
          System.err.format("ERROR: could not retrieve autogenerated key for well at %d @ %d x %d\n",
              plateId, plateRow, plateColumn);
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
  public static final String QUERY_UPDATE_PLATE_BY_ID = StringUtils.join(new String[] {
      "UPDATE ", TABLE_NAME, "SET",
      StringUtils.join(UPDATE_STATEMENT_FIELDS_AND_BINDINGS.iterator(), ", "),
      "WHERE",
      "id = ?", // 8
  }, " ");

  public static boolean updateSampleWell(DB db, SampleWell sw) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_PLATE_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, sw);
      stmt.setInt(8, sw.getId());
      return stmt.executeUpdate() > 0;
    }
  }

  public static List<SampleWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
      throws SQLException {
    Map<Pair<String, String>, String> msids = parser.getCompositionTables().get("msid");
    List<Pair<String, String>> sortedCoordinates = new ArrayList<>(msids.keySet());
    Collections.sort(sortedCoordinates, new Comparator<Pair<String, String>>() {
      // TODO: parse the values of these pairs as we read them so we don't need this silly comparator.
      @Override
      public int compare(Pair<String, String> o1, Pair<String, String> o2) {
        if (o1.getKey().equals(o2.getKey())) {
          return Integer.valueOf(Integer.parseInt(o1.getValue())).compareTo(Integer.parseInt(o2.getValue()));
        }
        return o1.getKey().compareTo(o2.getKey());
      }
    });

    List<SampleWell> results = new ArrayList<>();
    for (Pair<String, String> coords : sortedCoordinates) {
      String msid = msids.get(coords);
      if (msid == null || msid.isEmpty()) {
        continue;
      }
      String composition = parser.getCompositionTables().get("composition").get(coords);
      String chemical = parser.getCompositionTables().get("chemical").get(coords);
      String note = parser.getCompositionTables().get("note").get(coords);
      Pair<Integer, Integer> index = parser.getCoordinatesToIndices().get(coords);
      SampleWell s = SampleWell.insertSampleWell(db, p.getId(), index.getLeft(), index.getRight(),
          msid, composition, chemical, note);

      results.add(s);
    }

    return results;
  }


  Integer id;
  Integer plateId;
  Integer plateRow;
  Integer plateColumn;
  String msid;
  String composition;
  String chemical;

  String note;

  protected SampleWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn, String msid,
                    String composition, String chemical, String note) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.msid = msid;
    this.composition = composition;
    this.chemical = chemical;
    this.note = note;
  }

  public Integer getId() {
    return id;
  }

  public Integer getPlateId() {
    return plateId;
  }

  public void setPlateId(Integer plateId) {
    this.plateId = plateId;
  }

  public Integer getPlateRow() {
    return plateRow;
  }

  public void setPlateRow(Integer plateRow) {
    this.plateRow = plateRow;
  }

  public Integer getPlateColumn() {
    return plateColumn;
  }

  public void setPlateColumn(Integer plateColumn) {
    this.plateColumn = plateColumn;
  }

  public String getMsid() {
    return msid;
  }

  public void setMsid(String msid) {
    this.msid = msid;
  }

  public String getComposition() {
    return composition;
  }

  public void setComposition(String composition) {
    this.composition = composition;
  }

  public String getChemical() {
    return chemical;
  }

  public void setChemical(String chemical) {
    this.chemical = chemical;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }
}
