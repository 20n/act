package com.act.lcms.db;

import org.apache.commons.lang3.tuple.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class LCMSWell extends PlateWell<LCMSWell> {
  public static final String TABLE_NAME = "wells_lcms";
  protected static final LCMSWell INSTANCE = new LCMSWell();

  public static LCMSWell getInstance() {
    return INSTANCE;
  }

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
  protected List<LCMSWell> fromResultSet(ResultSet resultSet) throws SQLException {
    List<LCMSWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(1);
      Integer plateId = resultSet.getInt(2);
      Integer plateRow = resultSet.getInt(3);
      Integer plateColumn = resultSet.getInt(4);
      String msid = resultSet.getString(5);
      String composition = resultSet.getString(6);
      String chemical = resultSet.getString(7);
      String note = resultSet.getString(8);

      results.add(new LCMSWell(id, plateId, plateRow, plateColumn, msid, composition, chemical, note));
    }
    return results;
  }

  protected void bindInsertOrUpdateParameters(
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

  // Extra access patterns.
  public static final String GET_BY_CONSTRUCT_ID_QUERY = INSTANCE.makeGetQueryForSelectField("composition");
  public List<LCMSWell> getByConstructID(DB db, String construct) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(GET_BY_CONSTRUCT_ID_QUERY)) {
      stmt.setString(1, construct);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  public static final String GET_BY_STRAIN_QUERY = INSTANCE.makeGetQueryForSelectField("msid");
  public List<LCMSWell> getByStrain(DB db, String strainId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(GET_BY_STRAIN_QUERY)) {
      stmt.setString(1, strainId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }


  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, LCMSWell sw) throws SQLException {
    bindInsertOrUpdateParameters(stmt, sw.getPlateId(), sw.getPlateRow(), sw.getPlateColumn(),
        sw.getMsid(), sw.getComposition(), sw.getChemical(), sw.getNote());
  }

  public LCMSWell insert(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String msid, String composition, String chemical, String note) throws SQLException {
    return INSTANCE.insert(db, new LCMSWell(null, plateId, plateRow, plateColumn, msid, composition, chemical, note));
  }

  // Parsing/loading
  public List<LCMSWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
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

    List<LCMSWell> results = new ArrayList<>();
    for (Pair<String, String> coords : sortedCoordinates) {
      String msid = msids.get(coords);
      if (msid == null || msid.isEmpty()) {
        continue;
      }
      String composition = parser.getCompositionTables().get("composition").get(coords);
      String chemical = parser.getCompositionTables().get("chemical").get(coords);
      String note = parser.getCompositionTables().get("note").get(coords);
      Pair<Integer, Integer> index = parser.getCoordinatesToIndices().get(coords);
      LCMSWell s = INSTANCE.insert(db, p.getId(), index.getLeft(), index.getRight(),
          msid, composition, chemical, note);

      results.add(s);
    }

    return results;
  }


  private String msid;
  private String composition;
  private String chemical;
  private String note;

  private LCMSWell() { }

  protected LCMSWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn, String msid,
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
