package com.act.lcms.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InductionWell {
  public static final String TABLE_NAME = "wells_inductions";

  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(
      "id", // 1
      "plate_id", // 2
      "plate_row", // 3
      "plate_column", // 4
      "msid", // 5
      "chemical_source", // 6
      "composition", // 7
      "chemical", // 8
      "strain_source", // 9
      "note", // 10
      "growth" // 11
  ));

  // id is auto-generated on insertion.
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<InductionWell> inductionWellsFromResultSet(ResultSet resultSet) throws SQLException {
    List<InductionWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(1);
      Integer plateId = resultSet.getInt(2);
      Integer plateRow = resultSet.getInt(3);
      Integer plateColumn = resultSet.getInt(4);
      String msid = resultSet.getString(5);
      String chemicalSource = resultSet.getString(6);
      String composition = resultSet.getString(7);
      String chemical = resultSet.getString(8);
      String strainSource = resultSet.getString(9);
      String note = resultSet.getString(10);
      Integer growth = resultSet.getInt(11);
      if (resultSet.wasNull()) {
        growth = null;
      }

      results.add(new InductionWell(id, plateId, plateRow, plateColumn, msid, chemicalSource, composition,
          chemical, strainSource, note, growth));
    }
    return results;
  }

  protected static InductionWell expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<InductionWell> results = inductionWellsFromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }

  // Select
  public static final String QUERY_GET_INDUCTION_WELL_BY_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where id = ?",
  }, " ");

  public static InductionWell getInductionWellById(DB db, Integer id) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_INDUCTION_WELL_BY_ID)) {
      stmt.setInt(1, id);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("id = %d", id));
      }
    }
  }

  public static final String QUERY_GET_INDUCTION_WELL_BY_PLATE_ID = StringUtils.join(new String[] {
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where plate_id = ?",
  }, " ");

  public static List<InductionWell> getInductionWellsByPlateId(DB db, Integer plateId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_INDUCTION_WELL_BY_PLATE_ID)) {
      stmt.setInt(1, plateId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return inductionWellsFromResultSet(resultSet);
      }
    }
  }

  // TODO: add more access patterns.

  // Insert/Update
  public static final String QUERY_INSERT_INDUCTION_WELL = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = plateId
      "?,", // 2 = plateRow
      "?,", // 3 = plateColumn
      "?,", // 4 = msid
      "?,", // 5 = chemical_source
      "?,", // 6 = composition
      "?,", // 7 = chemical
      "?,", // 8 strain_source
      "?,", // 9 = note
      "?",  // 10 = growth
      ")"
  }, " ");

  protected static void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String msid, String chemicalSource, String composition, String chemical, String strainSource,
      String note, Integer growth) throws SQLException {
    stmt.setInt(1, plateId);
    stmt.setInt(2, plateRow);
    stmt.setInt(3, plateColumn);
    stmt.setString(4, msid);
    stmt.setString(5, chemicalSource);
    stmt.setString(6, composition);
    stmt.setString(7, chemical);
    stmt.setString(8, strainSource);
    stmt.setString(9, note);
    if (growth == null) {
      stmt.setNull(10, Types.INTEGER);
    } else {
      stmt.setInt(10, growth);
    }
  }

  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, InductionWell sw) throws SQLException {
    bindInsertOrUpdateParameters(stmt, sw.getPlateId(), sw.getPlateRow(), sw.getPlateColumn(),
        sw.getMsid(), sw.getChemicalSource(), sw.getComposition(), sw.getChemical(), sw.getStrainSource(),
        sw.getNote(), sw.getGrowth());
  }

  public static InductionWell insertInductionWell(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String msid, String chemicalSource, String composition, String chemical, String strainSource,
      String note, Integer growth) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_INSERT_INDUCTION_WELL, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, plateId, plateRow, plateColumn, msid, chemicalSource, composition,
          chemical, strainSource, note, growth);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new InductionWell(id, plateId, plateRow, plateColumn, msid, chemicalSource, composition,
              chemical, strainSource, note, growth);
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

  public static boolean updateInductionWell(DB db, InductionWell sw) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_PLATE_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, sw);
      stmt.setInt(UPDATE_STATEMENT_FIELDS_AND_BINDINGS.size() + 1, sw.getId());
      return stmt.executeUpdate() > 0;
    }
  }

  // TODO: remove this once this growth specification is no longer used.
  private static final Map<String, Integer> PLUS_MINUS_GROWTH_TO_INT = Collections.unmodifiableMap(
      new HashMap<String, Integer>() {{
        put("---", 0);
        put("--", 1);
        put("-", 2);
        put("+", 3);
        put("++", 4);
        put("+++", 5);
      }});

  public static List<InductionWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
      throws SQLException {
    Map<String, String> plateAttributes = parser.getPlateProperties();
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

    List<InductionWell> results = new ArrayList<>();
    for (Pair<String, String> coords : sortedCoordinates) {
      String msid = msids.get(coords);
      if (msid == null || msid.isEmpty()) {
        continue;
      }
      String chemicalSource = parser.getCompositionTables().get("chemical_source").get(coords);
      String composition = parser.getCompositionTables().get("composition").get(coords);
      String chemical = parser.getCompositionTables().get("chemical").get(coords);
      String strainSource = parser.getCompositionTables().get("strain_source").get(coords);
      String note = null;
      if (parser.getCompositionTables().get("note") != null) {
        note = parser.getCompositionTables().get("note").get(coords);
      }

      // TODO: ditch this when we start using floating point numbers for growth values.
      Integer growth = null;
      Map<Pair<String, String>, String> growthTable = parser.getCompositionTables().get("growth");
      if (growthTable == null || growthTable.get(coords) == null) {
        String plateGrowth = plateAttributes.get("growth");
        if (plateGrowth != null) {
          growth = Integer.parseInt(plateGrowth.trim());
        }
      } else {
        String growthStr = growthTable.get(coords);
        if (PLUS_MINUS_GROWTH_TO_INT.containsKey(growthStr)) {
          growth = PLUS_MINUS_GROWTH_TO_INT.get(growthStr);
        } else {
          growth = Integer.parseInt(growthStr); // If it's not using +/- format, it should be an integer from 1-5.
        }
      }

      Pair<Integer, Integer> index = parser.getCoordinatesToIndices().get(coords);
      InductionWell s = InductionWell.insertInductionWell(db, p.getId(), index.getLeft(), index.getRight(),
          msid, chemicalSource, composition, chemical, strainSource, note, growth);

      results.add(s);
    }

    return results;
  }


  private Integer id;
  private Integer plateId;
  private Integer plateRow;
  private Integer plateColumn;
  private String msid;
  private String chemicalSource;
  private String composition;
  private String chemical;
  private String strainSource;
  private String note;
  private Integer growth;

  protected InductionWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn, String msid,
                          String chemicalSource, String composition, String chemical, String strainSource,
                          String note, Integer growth) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.msid = msid;
    this.chemicalSource = chemicalSource;
    this.composition = composition;
    this.chemical = chemical;
    this.strainSource = strainSource;
    this.note = note;
    this.growth = growth;
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

  public String getChemicalSource() {
    return chemicalSource;
  }

  public void setChemicalSource(String chemicalSource) {
    this.chemicalSource = chemicalSource;
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

  public String getStrainSource() {
    return strainSource;
  }

  public void setStrainSource(String strainSource) {
    this.strainSource = strainSource;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public Integer getGrowth() {
    return growth;
  }

  public void setGrowth(Integer growth) {
    this.growth = growth;
  }
}
