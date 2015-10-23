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

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    PLATE_ID(2, 1, "plate_id"),
    PLATE_ROW(3, 2, "plate_row"),
    PLATE_COLUMN(4, 3, "plate_column"),
    MSID(5, 4, "msid"),
    COMPOSITION(6, 5, "composition"),
    CHEMICAL(7, 6, "chemical"),
    NOTE(8, 7, "note"),
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
  protected List<LCMSWell> fromResultSet(ResultSet resultSet) throws SQLException {
    List<LCMSWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      Integer plateId = resultSet.getInt(DB_FIELD.PLATE_ID.getOffset());
      Integer plateRow = resultSet.getInt(DB_FIELD.PLATE_ROW.getOffset());
      Integer plateColumn = resultSet.getInt(DB_FIELD.PLATE_COLUMN.getOffset());
      String msid = resultSet.getString(DB_FIELD.MSID.getOffset());
      String composition = resultSet.getString(DB_FIELD.COMPOSITION.getOffset());
      String chemical = resultSet.getString(DB_FIELD.CHEMICAL.getOffset());
      String note = resultSet.getString(DB_FIELD.NOTE.getOffset());

      results.add(new LCMSWell(id, plateId, plateRow, plateColumn, msid, composition, chemical, note));
    }
    return results;
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String msid, String composition, String chemical, String note) throws SQLException {
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setString(DB_FIELD.MSID.getInsertUpdateOffset(), msid);
    stmt.setString(DB_FIELD.COMPOSITION.getInsertUpdateOffset(), composition);
    stmt.setString(DB_FIELD.CHEMICAL.getInsertUpdateOffset(), chemical);
    stmt.setString(DB_FIELD.NOTE.getInsertUpdateOffset(), note);
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
      String note = null;
      if (parser.getCompositionTables().get("note") != null) {
        note = parser.getCompositionTables().get("note").get(coords);
      }
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
