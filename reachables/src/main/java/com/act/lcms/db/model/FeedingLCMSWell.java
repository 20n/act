package com.act.lcms.db.model;

import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.parser.PlateCompositionParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class FeedingLCMSWell extends PlateWell<FeedingLCMSWell> {
  public static final String TABLE_NAME = "wells_lcms_feeding";
  protected static final FeedingLCMSWell INSTANCE = new FeedingLCMSWell();

  public static FeedingLCMSWell getInstance() {
    return INSTANCE;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    PLATE_ID(2, 1, "plate_id"),
    PLATE_ROW(3, 2, "plate_row"),
    PLATE_COLUMN(4, 3, "plate_column"),
    MSID(5, 4, "msid"),
    COMPOSITION(6, 5, "composition"),
    EXTRACT(7, 6, "extract"),
    CHEMICAL(8, 7, "chemical"),
    CONCENTRATION(9, 8, "concentration"),
    NOTE(10, 9, "note"),
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
  protected List<FeedingLCMSWell> fromResultSet(ResultSet resultSet) throws SQLException {
    List<FeedingLCMSWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      Integer plateId = resultSet.getInt(DB_FIELD.PLATE_ID.getOffset());
      Integer plateRow = resultSet.getInt(DB_FIELD.PLATE_ROW.getOffset());
      Integer plateColumn = resultSet.getInt(DB_FIELD.PLATE_COLUMN.getOffset());
      String msid = resultSet.getString(DB_FIELD.MSID.getOffset());
      String composition = resultSet.getString(DB_FIELD.COMPOSITION.getOffset());
      String extract = resultSet.getString(DB_FIELD.EXTRACT.getOffset());
      String chemical = resultSet.getString(DB_FIELD.CHEMICAL.getOffset());
      Double concentration = resultSet.getDouble(DB_FIELD.CONCENTRATION.getOffset());
      if (resultSet.wasNull()) {
        concentration = null;
      }
      String note = resultSet.getString(DB_FIELD.NOTE.getOffset());

      results.add(new FeedingLCMSWell(
          id, plateId, plateRow, plateColumn, msid, composition, extract, chemical, concentration, note));
    }
    return results;
  }

  public static final String QUERY_GET_FEEDING_LCMS_WELL_BY_PLATE_ID = INSTANCE.makeGetQueryForSelectField("plate_id");
  public List<FeedingLCMSWell> getFeedingLCMSWellByPlateId(DB db, Integer plateId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_FEEDING_LCMS_WELL_BY_PLATE_ID)) {
      stmt.setInt(1, plateId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  // Insert/update
  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn, String msid,
      String composition, String extract, String chemical, Double concentration, String note) throws SQLException {
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setString(DB_FIELD.MSID.getInsertUpdateOffset(), msid);
    stmt.setString(DB_FIELD.COMPOSITION.getInsertUpdateOffset(), composition);
    stmt.setString(DB_FIELD.EXTRACT.getInsertUpdateOffset(), extract);
    stmt.setString(DB_FIELD.CHEMICAL.getInsertUpdateOffset(), chemical);
    if (concentration != null) {
      stmt.setDouble(DB_FIELD.CONCENTRATION.getInsertUpdateOffset(), concentration);
    } else {
      stmt.setNull(DB_FIELD.CONCENTRATION.getInsertUpdateOffset(), Types.DOUBLE);
    }
    stmt.setString(DB_FIELD.NOTE.getInsertUpdateOffset(), note);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, FeedingLCMSWell parameterSource)
      throws SQLException {
    bindInsertOrUpdateParameters(stmt, parameterSource.getPlateId(), parameterSource.getPlateRow(),
        parameterSource.getPlateColumn(), parameterSource.getMsid(), parameterSource.getComposition(),
        parameterSource.getExtract(), parameterSource.getChemical(), parameterSource.getConcentration(),
        parameterSource.getNote());
  }

  public static String trimAndComplain(String val) {
    String tval = val.trim();
    if (!val.equals(tval)) {
      System.err.format("WARNING: trimmed spurious whitespace from '%s'\n", val);
    }
    return tval;
  }

  // Parsing/loading
  public List<FeedingLCMSWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
      throws SQLException, IOException {
    Map<String, String> plateProperties = parser.getPlateProperties();
    String msid = null, composition = null;
    if (!plateProperties.containsKey("msid") && plateProperties.containsKey("composition")) {
      throw new RuntimeException("ERROR: assumed plate properties 'msid' and 'composition' do not exist");
    }
    msid = trimAndComplain(plateProperties.get("msid"));
    composition = trimAndComplain(plateProperties.get("composition"));

    // If a few well dones't have a concentration, it's not work keeping.
    Map<Pair<String, String>, String> concentrations = parser.getCompositionTables().get("concentration");
    List<Pair<String, String>> sortedCoordinates = new ArrayList<>(concentrations.keySet());
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

    List<FeedingLCMSWell> results = new ArrayList<>();
    for (Pair<String, String> coords : sortedCoordinates) {
      String concentraitonStr =  parser.getCompositionTables().get("concentration").get(coords);
      if (concentraitonStr == null || concentraitonStr.isEmpty()) {
        continue;
      }
      String extract = parser.getCompositionTables().get("extract").get(coords);
      String chemical = parser.getCompositionTables().get("chemical").get(coords);
      Double concentration = Double.parseDouble(concentraitonStr);
      String note = null;
      if (parser.getCompositionTables().get("note") != null) {
        note = parser.getCompositionTables().get("note").get(coords);
      }
      Pair<Integer, Integer> index = parser.getCoordinatesToIndices().get(coords);
      FeedingLCMSWell s = INSTANCE.insert(db, new FeedingLCMSWell(null, p.getId(), index.getLeft(), index.getRight(),
          msid, composition, extract, chemical, concentration, note));

      results.add(s);
    }

    return results;
  }

  private String msid;
  private String composition;
  private String extract;
  private String chemical;
  private Double concentration;
  private String note;

  private FeedingLCMSWell() { }

  public FeedingLCMSWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn,
                         String msid, String composition, String extract, String chemical,
                         Double concentration, String note) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.msid = msid;
    this.composition = composition;
    this.extract = extract;
    this.chemical = chemical;
    this.concentration = concentration;
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

  public String getExtract() {
    return extract;
  }

  public void setExtract(String extract) {
    this.extract = extract;
  }

  public String getChemical() {
    return chemical;
  }

  public void setChemical(String chemical) {
    this.chemical = chemical;
  }

  public Double getConcentration() {
    return concentration;
  }

  public void setConcentration(Double concentration) {
    this.concentration = concentration;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FeedingLCMSWell that = (FeedingLCMSWell) o;

    if (!msid.equals(that.msid)) return false;
    if (!composition.equals(that.composition)) return false;
    if (extract != null ? !extract.equals(that.extract) : that.extract != null) return false;
    if (chemical != null ? !chemical.equals(that.chemical) : that.chemical != null) return false;
    if (!concentration.equals(that.concentration)) return false;
    return !(note != null ? !note.equals(that.note) : that.note != null);

  }

  @Override
  public int hashCode() {
    int result = msid.hashCode();
    result = 31 * result + composition.hashCode();
    result = 31 * result + (extract != null ? extract.hashCode() : 0);
    result = 31 * result + (chemical != null ? chemical.hashCode() : 0);
    result = 31 * result + concentration.hashCode();
    result = 31 * result + (note != null ? note.hashCode() : 0);
    return result;
  }
}
