package com.act.lcms.db;

import org.apache.commons.lang3.tuple.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PregrowthWell extends PlateWell<PregrowthWell> {
  public static final String TABLE_NAME = "wells_pregrowth";
  protected static final PregrowthWell INSTANCE = new PregrowthWell();

  public static PregrowthWell getInstance() {
    return INSTANCE;
  }

  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(
      "id", // 1
      "plate_id", // 2
      "plate_row", // 3
      "plate_column", // 4
      "source_plate", // 5
      "source_well", // 6
      "msid", // 7
      "composition", // 8
      "note", // 9
      "growth" // 10
  ));

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

  protected static final String GET_BY_ID_QUERY = INSTANCE.makeGetByIDQuery();
  @Override
  protected String getGetByIDQuery() {
    return GET_BY_ID_QUERY;
  }

  protected static final String GET_BY_PLATE_ID_QUERY = INSTANCE.makeGetByPlateIDQuery();
  @Override
  protected String getGetByPlateIDQuery() {
    return GET_BY_PLATE_ID_QUERY;
  }

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

  @Override
  protected List<PregrowthWell> fromResultSet(ResultSet resultSet) throws SQLException {
    List<PregrowthWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(1);
      Integer plateId = resultSet.getInt(2);
      Integer plateRow = resultSet.getInt(3);
      Integer plateColumn = resultSet.getInt(4);
      String sourcePlate = resultSet.getString(5);
      String sourceWell = resultSet.getString(6);
      String msid = resultSet.getString(7);
      String composition = resultSet.getString(8);
      String note = resultSet.getString(9);
      Integer growth = resultSet.getInt(10);
      if (resultSet.wasNull()) {
        growth = null;
      }

      results.add(new PregrowthWell(id, plateId, plateRow, plateColumn, sourcePlate, sourceWell, msid, composition,
          note, growth));
    }
    return results;
  }
  // Insert/Update
  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String sourcePlate, String sourceWell, String msid, String composition,
      String note, Integer growth) throws SQLException {
    stmt.setInt(1, plateId);
    stmt.setInt(2, plateRow);
    stmt.setInt(3, plateColumn);
    stmt.setString(4, sourcePlate);
    stmt.setString(5, sourceWell);
    stmt.setString(6, msid);
    stmt.setString(7, composition);
    stmt.setString(8, note);
    if (growth == null) {
      stmt.setNull(9, Types.INTEGER);
    } else {
      stmt.setInt(9, growth);
    }
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, PregrowthWell pw) throws SQLException {
    bindInsertOrUpdateParameters(stmt, pw.getPlateId(), pw.getPlateRow(), pw.getPlateColumn(),
        pw.getSourcePlate(), pw.getSourceWell(), pw.getMsid(), pw.getComposition(),
        pw.getNote(), pw.getGrowth());
  }

  public PregrowthWell insert(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn, String sourcePlate, String sourceWell,
      String msid, String composition, String note, Integer growth) throws SQLException {
    return INSTANCE.insert(db, new PregrowthWell(null, plateId, plateRow, plateColumn, sourcePlate, sourceWell,
        msid, composition, note, growth));
  }

  // Parsing/loading
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

  public List<PregrowthWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
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

    List<PregrowthWell> results = new ArrayList<>();
    for (Pair<String, String> coords : sortedCoordinates) {
      String msid = msids.get(coords);
      if (msid == null || msid.isEmpty()) {
        continue;
      }
      String sourcePlate = parser.getCompositionTables().get("source_plate").get(coords);
      String sourceWell = parser.getCompositionTables().get("source_well").get(coords);
      String composition = parser.getCompositionTables().get("composition").get(coords);
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
      PregrowthWell s = INSTANCE.insert(db, p.getId(), index.getLeft(), index.getRight(),
          sourcePlate, sourceWell, msid, composition, note, growth);

      results.add(s);
    }

    return results;
  }


  private String sourcePlate;
  private String sourceWell;
  private String msid;
  private String composition;
  private String note;
  private Integer growth;

  private PregrowthWell() { }

  protected PregrowthWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn, String sourcePlate,
                          String sourceWell, String msid, String composition, String note, Integer growth) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.sourcePlate = sourcePlate;
    this.sourceWell = sourceWell;
    this.msid = msid;
    this.composition = composition;
    this.note = note;
    this.growth = growth;
  }

  public String getSourcePlate() {
    return sourcePlate;
  }

  public void setSourcePlate(String sourcePlate) {
    this.sourcePlate = sourcePlate;
  }

  public String getSourceWell() {
    return sourceWell;
  }

  public void setSourceWell(String sourceWell) {
    this.sourceWell = sourceWell;
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
