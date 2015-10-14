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

public class StandardWell {
  public static final String TABLE_NAME = "wells_standards";

  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(
      "id", // 1
      "plate_id", // 2
      "plate_row", // 3
      "plate_column", // 4
      "chemical", // 5
      "media", // 6
      "note" // 7
  ));

  // id is auto-generated on insertion.
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<StandardWell> standardWellsFromResultSet(ResultSet resultSet) throws SQLException {
    List<StandardWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(1);
      Integer plateId = resultSet.getInt(2);
      Integer plateRow = resultSet.getInt(3);
      Integer plateColumn = resultSet.getInt(4);
      String chemical = resultSet.getString(5);
      String media = resultSet.getString(6);
      String note = resultSet.getString(7);

      results.add(new StandardWell(id, plateId, plateRow, plateColumn, chemical, media, note));
    }
    return results;
  }

  protected static StandardWell expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<StandardWell> results = standardWellsFromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }

  // Select
  public static final String QUERY_GET_STANDARD_WELL_BY_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where id = ?",
  }, " ");

  public static StandardWell getStandardWellById(DB db, Integer id) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_STANDARD_WELL_BY_ID)) {
      stmt.setInt(1, id);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("id = %d", id));
      }
    }
  }

  public static final String QUERY_GET_STANDARD_WELL_BY_PLATE_ID = StringUtils.join(new String[] {
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where plate_id = ?",
  }, " ");

  public static List<StandardWell> getStandardWellsByPlateId(DB db, Integer plateId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_STANDARD_WELL_BY_PLATE_ID)) {
      stmt.setInt(1, plateId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return standardWellsFromResultSet(resultSet);
      }
    }
  }

  // TODO: add more access patterns.

  // Insert/Update
  public static final String QUERY_INSERT_STANDARD_WELL = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = plateId
      "?,", // 2 = plateRow
      "?,", // 3 = plateColumn
      "?,", // 4 = chemical
      "?,", // 5 = media
      "?",  // 6 = note
      ")"
  }, " ");

  protected static void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String chemical, String media, String note) throws SQLException {
    stmt.setInt(1, plateId);
    stmt.setInt(2, plateRow);
    stmt.setInt(3, plateColumn);
    stmt.setString(4, chemical);
    stmt.setString(5, media);
    stmt.setString(6, note);
  }

  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, StandardWell sw) throws SQLException {
    bindInsertOrUpdateParameters(stmt, sw.getPlateId(), sw.getPlateRow(), sw.getPlateColumn(),
        sw.getChemical(), sw.getMedia(), sw.getNote());
  }

  public static StandardWell insertStandardWell(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String chemical, String media, String note) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_INSERT_STANDARD_WELL, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, plateId, plateRow, plateColumn, chemical, media, note);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new StandardWell(id, plateId, plateRow, plateColumn, chemical, media, note);
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
      "id = ?", // 7
  }, " ");

  public static boolean updateStandardWell(DB db, StandardWell sw) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_PLATE_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, sw);
      stmt.setInt(7, sw.getId());
      return stmt.executeUpdate() > 0;
    }
  }

  public static List<StandardWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
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

    List<StandardWell> results = new ArrayList<>();
    for (Pair<String, String> coords : sortedCoordinates) {
      String chemical = parser.getCompositionTables().get("chemical").get(coords);
      if (chemical == null || chemical.isEmpty()) {
        continue;
      }
      String media = parser.getCompositionTables().get("media").get(coords);
      String note = parser.getCompositionTables().get("note").get(coords);
      Pair<Integer, Integer> index = parser.getCoordinatesToIndices().get(coords);
      StandardWell s = StandardWell.insertStandardWell(db, p.getId(), index.getLeft(), index.getRight(),
          chemical, media, note);

      results.add(s);
    }

    return results;
  }


  Integer id;
  Integer plateId;
  Integer plateRow;
  Integer plateColumn;
  String chemical;
  String media;
  String note;

  public StandardWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn,
                      String chemical, String media, String note) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.chemical = chemical;
    this.media = media;
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

  public String getChemical() {
    return chemical;
  }

  public void setChemical(String chemical) {
    this.chemical = chemical;
  }

  public String getMedia() {
    return media;
  }

  public void setMedia(String media) {
    this.media = media;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }
}
