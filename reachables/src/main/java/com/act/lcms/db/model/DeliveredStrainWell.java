package com.act.lcms.db.model;

import com.act.lcms.db.analysis.Utils;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.parser.PlateCompositionParser;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeliveredStrainWell extends PlateWell<DeliveredStrainWell> {
  public static final String TABLE_NAME = "wells_delivered_strain";
  protected static final DeliveredStrainWell INSTANCE = new DeliveredStrainWell();

  public static DeliveredStrainWell getInstance() {
    return INSTANCE;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    PLATE_ID(2, 1, "plate_id"),
    PLATE_ROW(3, 2, "plate_row"),
    PLATE_COLUMN(4, 3, "plate_column"),
    WELL(5, 4, "well"),
    MSID(6, 5, "msid"),
    COMPOSITION(7, 6, "composition"),
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
  protected List<DeliveredStrainWell> fromResultSet(ResultSet resultSet) throws SQLException {
    List<DeliveredStrainWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      Integer plateId = resultSet.getInt(DB_FIELD.PLATE_ID.getOffset());
      Integer plateRow = resultSet.getInt(DB_FIELD.PLATE_ROW.getOffset());
      Integer plateColumn = resultSet.getInt(DB_FIELD.PLATE_COLUMN.getOffset());
      String well = resultSet.getString(DB_FIELD.WELL.getOffset());
      String msid = resultSet.getString(DB_FIELD.MSID.getOffset());
      String composition = resultSet.getString(DB_FIELD.COMPOSITION.getOffset());

      results.add(new DeliveredStrainWell(id, plateId, plateRow, plateColumn, well, msid, composition));
    }
    return results;
  }


  // Insert/Update
  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String well, String msid, String composition) throws SQLException {
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setString(DB_FIELD.WELL.getInsertUpdateOffset(), well);
    stmt.setString(DB_FIELD.MSID.getInsertUpdateOffset(), msid);
    stmt.setString(DB_FIELD.COMPOSITION.getInsertUpdateOffset(), composition);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, DeliveredStrainWell dsw)
      throws SQLException {
    bindInsertOrUpdateParameters(stmt, dsw.getPlateId(), dsw.getPlateRow(), dsw.getPlateColumn(),
        dsw.getWell(), dsw.getMsid(), dsw.getComposition());
  }

  public DeliveredStrainWell insert(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String well, String msid, String composition) throws SQLException {
    return INSTANCE.insert(db, new DeliveredStrainWell(null, plateId, plateRow, plateColumn, well, msid, composition));
  }

  // Parsing/loading

  public List<DeliveredStrainWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
      throws SQLException {
    List<DeliveredStrainWell> results = new ArrayList<>();

    Map<Pair<String, String>, String> featuresTable = parser.getCompositionTables().get("well");
    /* The composition tables are usually constructed with well coordinates as the X and Y axes.  Amyris strain plates,
     * however, have the combined well coordinates in the first column and the msid/composition in related columns.
     * We'll collect the features in each row by traversing the per-cell entries in the parser's table and merging on
     * the first coordinate component. */
    Map<Pair<Integer, Integer>, Map<String, String>> wellToFeaturesMap = new HashMap<>();
    for (Map.Entry<Pair<String, String>, String> entry : featuresTable.entrySet()) {
      String wellCoordinates = entry.getKey().getLeft();
      String featureName = entry.getKey().getRight();
      String featureValue = entry.getValue();

      Pair<Integer, Integer> coordinates = Utils.parsePlateCoordinates(wellCoordinates);

      Map<String, String> featuresForWell = wellToFeaturesMap.get(coordinates);
      if (featuresForWell == null){
        featuresForWell = new HashMap<>();
        wellToFeaturesMap.put(coordinates, featuresForWell);
      }
      if (featuresForWell.containsKey(featureName)) {
        throw new RuntimeException(
            String.format("Found duplicate feature %s for well %s", wellCoordinates, featureName));
      }
      featuresForWell.put(featureName, featureValue);
      // Also save the original well coordinates string.
      featuresForWell.put("well", wellCoordinates);
    }

    List<Map.Entry<Pair<Integer, Integer>, Map<String, String>>> sortedEntries =
        new ArrayList<>(wellToFeaturesMap.entrySet());
    Collections.sort(sortedEntries, new Comparator<Map.Entry<Pair<Integer, Integer>, Map<String, String>>>() {
      @Override
      public int compare(Map.Entry<Pair<Integer, Integer>, Map<String, String>> o1,
                         Map.Entry<Pair<Integer, Integer>, Map<String, String>> o2) {
        return o1.getKey().compareTo(o2.getKey());
      }
    });

    for (Map.Entry<Pair<Integer, Integer>, Map<String, String>> entry : sortedEntries) {
      Pair<Integer, Integer> coords = entry.getKey();
      Map<String, String> attrs = entry.getValue();
      DeliveredStrainWell s = INSTANCE.insert(db, p.getId(), coords.getLeft(), coords.getRight(),
          attrs.get("well"), attrs.get("msid"), attrs.get("composition"));
      results.add(s);
    }

    return results;
  }


  private String well;
  private String msid;
  private String composition;

  private DeliveredStrainWell() { }

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
