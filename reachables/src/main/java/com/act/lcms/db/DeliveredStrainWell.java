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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeliveredStrainWell {
  public static final String TABLE_NAME = "wells_delivered_strains";

  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(
      "id", // 1
      "plate_id", // 2
      "plate_row", // 3
      "plate_column", // 4
      "well", // 5
      "msid", // 6
      "composition" // 7
  ));

  // id is auto-generated on insertion.
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<DeliveredStrainWell> deliveredStrainWellsFromResultSet(ResultSet resultSet) throws SQLException {
    List<DeliveredStrainWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(1);
      Integer plateId = resultSet.getInt(2);
      Integer plateRow = resultSet.getInt(3);
      Integer plateColumn = resultSet.getInt(4);
      String well = resultSet.getString(5);
      String msid = resultSet.getString(6);
      String composition = resultSet.getString(7);

      results.add(new DeliveredStrainWell(id, plateId, plateRow, plateColumn, well, msid, composition));
    }
    return results;
  }

  protected static DeliveredStrainWell expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<DeliveredStrainWell> results = deliveredStrainWellsFromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }


  // Select
  public static final String QUERY_GET_DELIVERED_STRAIN_WELL_BY_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where id = ?",
  }, " ");

  public static DeliveredStrainWell getDeliveredStrainWellById(DB db, Integer id) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_DELIVERED_STRAIN_WELL_BY_ID)) {
      stmt.setInt(1, id);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("id = %d", id));
      }
    }
  }

  public static final String QUERY_GET_DELIVERED_STRAIN_WELL_BY_PLATE_ID = StringUtils.join(new String[] {
      "SELECT", StringUtils.join(ALL_FIELDS, ','),
      "from", TABLE_NAME,
      "where plate_id = ?",
  }, " ");

  public static List<DeliveredStrainWell> getDeliveredStrainWellsByPlateId(DB db, Integer plateId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_DELIVERED_STRAIN_WELL_BY_PLATE_ID)) {
      stmt.setInt(1, plateId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return deliveredStrainWellsFromResultSet(resultSet);
      }
    }
  }

  // TODO: add more access patterns.

  // Insert/Update
  public static final String QUERY_INSERT_DELIVERED_STRAIN_WELL = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = plateId
      "?,", // 2 = plateRow
      "?,", // 3 = plateColumn
      "?,", // 4 = well
      "?,", // 5 = msid
      "?",  // 6 = composition
      ")"
  }, " ");

  protected static void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String well, String msid, String composition) throws SQLException {
    stmt.setInt(1, plateId);
    stmt.setInt(2, plateRow);
    stmt.setInt(3, plateColumn);
    stmt.setString(4, well);
    stmt.setString(5, msid);
    stmt.setString(6, composition);
  }

  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, DeliveredStrainWell dsw)
      throws SQLException {
    bindInsertOrUpdateParameters(stmt, dsw.getPlateId(), dsw.getPlateRow(), dsw.getPlateColumn(),
        dsw.getWell(), dsw.getMsid(), dsw.getComposition());
  }

  public static DeliveredStrainWell insertDeliveredStrainWell(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String well, String msid, String composition) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_INSERT_DELIVERED_STRAIN_WELL, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, plateId, plateRow, plateColumn, well, msid, composition);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new DeliveredStrainWell(id, plateId, plateRow, plateColumn, well, msid, composition);
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

  public static boolean updateDeliveredStrainWell(DB db, DeliveredStrainWell sw) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_PLATE_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, sw);
      stmt.setInt(UPDATE_STATEMENT_FIELDS_AND_BINDINGS.size(), sw.getId());
      return stmt.executeUpdate() > 0;
    }
  }

  public static final Pattern wellCoordinatesPattern = Pattern.compile("^([A-Z]+)(\\d+)$");
  public static Pair<String, Integer> splitWellCoordinates(String wellCoordinates) {
    Matcher matcher = wellCoordinatesPattern.matcher(wellCoordinates);
    if (!matcher.matches()) {
      throw new RuntimeException(
          String.format("Well coordinates '%s' are invalid and cannot be split", wellCoordinates));
    }
    return Pair.of(matcher.group(1), Integer.parseInt(matcher.group(2)));
  }

  /* Rather than trying to compute the offset of well coordinates on the fly, we pre-compute and choke if we can't find
   * the well. */
  private static final Map<String, Integer> WELL_ROW_TO_INDEX;
  static {
    Map<String, Integer> m = new HashMap<>();
    int i = 0;
    for (char c = 'A'; c < 'Z'; c++, i++) {
      m.put(String.valueOf(c), i);
    }
    WELL_ROW_TO_INDEX = Collections.unmodifiableMap(m);
  }

  public static List<DeliveredStrainWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
      throws SQLException {
    List<DeliveredStrainWell> results = new ArrayList<>();

    Map<Pair<String, String>, String> featuresTable = parser.getCompositionTables().get("well");
    /* The composition tables are usually constructed with well coordinates as the X and Y axes.  Amyris strain plates,
     * however, have the combined well coordinates in the first column and the msid/composition in related columns.
     * We'll collect the features in each row by traversing the per-cell entries in the parser's table and merging on
     * the first coordinate component. */
    Map<Pair<String, Integer>, Map<String, String>> wellToFeaturesMap = new HashMap<>();
    for (Map.Entry<Pair<String, String>, String> entry : featuresTable.entrySet()) {
      String wellCoordinates = entry.getKey().getLeft();
      String featureName = entry.getKey().getRight();
      String featureValue = entry.getValue();

      Pair<String, Integer> splitCoordinates = splitWellCoordinates(wellCoordinates);

      Map<String, String> featuresForWell = wellToFeaturesMap.get(splitCoordinates);
      if (featuresForWell == null){
        featuresForWell = new HashMap<>();
        wellToFeaturesMap.put(splitCoordinates, featuresForWell);
      }
      if (featuresForWell.containsKey(featureName)) {
        throw new RuntimeException(
            String.format("Found duplicate feature %s for well %s", wellCoordinates, featureName));
      }
      featuresForWell.put(featureName, featureValue);
      // Also save the original well coordinates string.
      featuresForWell.put("well", wellCoordinates);
    }

    List<Map.Entry<Pair<String, Integer>, Map<String, String>>> sortedEntries =
        new ArrayList<>(wellToFeaturesMap.entrySet());
    Collections.sort(sortedEntries, new Comparator<Map.Entry<Pair<String, Integer>, Map<String, String>>>() {
      @Override
      public int compare(Map.Entry<Pair<String, Integer>, Map<String, String>> o1,
                         Map.Entry<Pair<String, Integer>, Map<String, String>> o2) {
        return o1.getKey().compareTo(o2.getKey());
      }
    });

    for (Map.Entry<Pair<String, Integer>, Map<String, String>> entry : sortedEntries) {
      Pair<String, Integer> coords = entry.getKey();
      Map<String, String> attrs = entry.getValue();
      Integer wellRow = WELL_ROW_TO_INDEX.get(coords.getLeft());
      if (wellRow == null) {
        throw new RuntimeException(String.format("Can't handle well row %s", coords.getLeft()));
      }
      Pair<Integer, Integer> index = Pair.of(wellRow, coords.getRight());
      DeliveredStrainWell s =
          DeliveredStrainWell.insertDeliveredStrainWell(db, p.getId(), index.getLeft(), index.getRight(),
              attrs.get("well"), attrs.get("msid"), attrs.get("composition"));
      results.add(s);
    }

    return results;
  }

  private Integer id;
  private Integer plateId;
  private Integer plateRow;
  private Integer plateColumn;
  private String well;
  private String msid;
  private String composition;

  protected DeliveredStrainWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn,
                               String well, String msid, String composition) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.well = well;
    this.msid = msid;
    this.composition = composition;
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

  public String getWell() {
    return well;
  }

  public void setWell(String well) {
    this.well = well;
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
}
