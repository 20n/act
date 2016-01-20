package com.act.lcms.db.model;

import com.act.lcms.db.io.DB;
import com.act.lcms.db.io.parser.PlateCompositionParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

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

public class StandardWell extends PlateWell<StandardWell> {
  protected static final StandardWell INSTANCE = new StandardWell();

  public static StandardWell getInstance() {
    return INSTANCE;
  }

  public static final String TABLE_NAME = "wells_standard";

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    PLATE_ID(2, 1, "plate_id"),
    PLATE_ROW(3, 2, "plate_row"),
    PLATE_COLUMN(4, 3, "plate_column"),
    CHEMICAL(5, 4, "chemical"),
    MEDIA(6, 5, "media"),
    NOTE(7, 6, "note"),
    CONCENTRATION(8, 7, "concentration"),
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
  protected List<StandardWell> fromResultSet(ResultSet resultSet) throws SQLException {
    List<StandardWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      Integer plateId = resultSet.getInt(DB_FIELD.PLATE_ID.getOffset());
      Integer plateRow = resultSet.getInt(DB_FIELD.PLATE_ROW.getOffset());
      Integer plateColumn = resultSet.getInt(DB_FIELD.PLATE_COLUMN.getOffset());
      String chemical = resultSet.getString(DB_FIELD.CHEMICAL.getOffset());
      String media = resultSet.getString(DB_FIELD.MEDIA.getOffset());
      String note = resultSet.getString(DB_FIELD.NOTE.getOffset());
      Double concentration = resultSet.getDouble(DB_FIELD.CONCENTRATION.getOffset());
      if (resultSet.wasNull()) {
        concentration = null;
      }

      results.add(new StandardWell(id, plateId, plateRow, plateColumn, chemical, media, note, concentration));
    }
    return results;
  }

  public static final String QUERY_GET_STANDARD_WELL_BY_PLATE_ID_AND_CHEMICAL =
      StringUtils.join(new String[]{
          "SELECT", StringUtils.join(INSTANCE.getAllFields(), ','),
          "from", INSTANCE.getTableName(),
          "where plate_id = ?",
          "  and chemical = ?",
          "order by plate_row, plate_column"
      }, " ");
  public List<StandardWell> getStandardWellsByPlateIdAndChemical(DB db, Integer plateId, String chemical)
      throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_STANDARD_WELL_BY_PLATE_ID_AND_CHEMICAL)) {
      stmt.setInt(1, plateId);
      stmt.setString(2, chemical);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  public static final String QUERY_GET_STANDARD_WELL_BY_PLATE_ID_AND_COORDINATES =
      StringUtils.join(new String[]{
          "SELECT", StringUtils.join(INSTANCE.getAllFields(), ','),
          "from", INSTANCE.getTableName(),
          "where plate_id = ?",
          "  and plate_row = ?",
          "  and plate_column = ?",
      }, " ");
  public StandardWell getStandardWellsByPlateIdAndCoordinates(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn)throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_STANDARD_WELL_BY_PLATE_ID_AND_COORDINATES)) {
      stmt.setInt(1, plateId);
      stmt.setInt(2, plateRow);
      stmt.setInt(3, plateColumn);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet,
            String.format("plate_id = %d, plate_row = %d, plate_column = %d", plateId, plateRow, plateColumn));
      }
    }
  }

  public static final String QUERY_GET_STANDARD_WELLS_BY_CHEMICAL = StringUtils.join(new String[] {
      "SELECT", StringUtils.join(INSTANCE.getAllFields(), ','),
      "from", INSTANCE.getTableName(),
      "where chemical = ?",
      "order by plate_id, plate_row, plate_column"
  }, " ");

  public static final String QUERY_GET_STANDARD_WELLS_BY_CHEMICAL_AND_PLATE_ID = StringUtils.join(new String[] {
      "SELECT", StringUtils.join(INSTANCE.getAllFields(), ','),
      "from", INSTANCE.getTableName(),
      "where chemical = ?",
      "  and plate_id = ?",
      "order by plate_id, plate_row, plate_column"
  }, " ");

  public List<StandardWell> getStandardWellsByChemical(DB db, String chemical) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_STANDARD_WELLS_BY_CHEMICAL)) {
      stmt.setString(1, chemical);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  public List<StandardWell> getStandardWellsByChemicalAndPlateId(DB db, String chemical, Integer plateId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_STANDARD_WELLS_BY_CHEMICAL_AND_PLATE_ID)) {
      stmt.setString(1, chemical);
      stmt.setString(2, plateId.toString());
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  // Insert/update
  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String chemical, String media, String note, Double concentration) throws SQLException {
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setString(DB_FIELD.CHEMICAL.getInsertUpdateOffset(), chemical);
    stmt.setString(DB_FIELD.MEDIA.getInsertUpdateOffset(), media);
    stmt.setString(DB_FIELD.NOTE.getInsertUpdateOffset(), note);
    if (concentration != null) {
      stmt.setDouble(DB_FIELD.CONCENTRATION.getInsertUpdateOffset(), concentration);
    } else {
      stmt.setNull(DB_FIELD.CONCENTRATION.getInsertUpdateOffset(), Types.DOUBLE);
    }
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, StandardWell sw) throws SQLException {
    bindInsertOrUpdateParameters(stmt, sw.getPlateId(), sw.getPlateRow(), sw.getPlateColumn(),
        sw.getChemical(), sw.getMedia(), sw.getNote(), sw.getConcentration());
  }

  public StandardWell insert(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String chemical, String media, String note, Double concentration) throws SQLException {
    return INSTANCE.insert(db,
        new StandardWell(null, plateId, plateRow, plateColumn, chemical, media, note, concentration));
  }

  // Parsing/loading
  public List<StandardWell> insertFromPlateComposition(DB db, PlateCompositionParser parser, Plate p)
      throws SQLException {
    Map<Pair<String, String>, String> msids = parser.getCompositionTables().get("chemical");
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
      Map<Pair<String, String>, String> mediaMap = parser.getCompositionTables().get("media");
      if (mediaMap == null) {
        mediaMap = parser.getCompositionTables().get("solvent");
      }
      String media = mediaMap != null ? mediaMap.get(coords) : null;
      Map<Pair<String, String>, String> notesMap = parser.getCompositionTables().get("note");
      String note = notesMap != null ? notesMap.get(coords) : null;
      Pair<Integer, Integer> index = parser.getCoordinatesToIndices().get(coords);
      Map<Pair<String, String>, String> concentrationsMap = parser.getCompositionTables().get("concentration");
      Double concentration = concentrationsMap != null ? Double.parseDouble(concentrationsMap.get(coords)) : null;
      StandardWell s = INSTANCE.insert(db, p.getId(), index.getLeft(), index.getRight(),
          chemical, media, note, concentration);

      results.add(s);
    }

    return results;
  }


  private String chemical;
  private String media;
  private String note;
  private Double concentration;

  private StandardWell() { }

  protected StandardWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn,
                         String chemical, String media, String note, Double concentration) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.chemical = chemical;
    this.media = media;
    this.note = note;
    this.concentration = concentration;
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

  public Double getConcentration() {
    return concentration;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StandardWell that = (StandardWell) o;

    // TODO: what to do about INSTANCE?

    // TODO: move the BaseDBModel and PlateWell comparisons to the appropriate superclasses.
    // Note that we could probably just restrict this to use plate id, row, and column since those should be unique.
    if (this.id != null && that.id != null && !this.id.equals(that.id)) return false;
    if (!this.plateId.equals(that.plateId)) return false;
    if (!this.plateRow.equals(that.plateRow)) return false;
    if (!this.plateColumn.equals(that.plateColumn)) return false;

    if (!chemical.equals(that.chemical)) return false;
    if (media != null ? !media.equals(that.media) : that.media != null) return false;
    if (note != null ? !note.equals(that.note) : that.note != null) return false;
    return !(concentration != null ? !concentration.equals(that.concentration) : that.concentration != null);

  }

  @Override
  public int hashCode() {
    int result = id == null ? 0 : id.hashCode();
    result = 31 * result + plateId.hashCode();
    result = 31 * result + plateRow.hashCode();
    result = 31 * result + plateColumn.hashCode();
    result = 31 * result + chemical.hashCode();
    result = 31 * result + (media != null ? media.hashCode() : 0);
    result = 31 * result + (note != null ? note.hashCode() : 0);
    result = 31 * result + (concentration != null ? concentration.hashCode() : 0);
    return result;
  }
}
