package com.act.lcms.db.model;

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

      results.add(new StandardWell(id, plateId, plateRow, plateColumn, chemical, media, note));
    }
    return results;
  }


  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      String chemical, String media, String note) throws SQLException {
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setString(DB_FIELD.CHEMICAL.getInsertUpdateOffset(), chemical);
    stmt.setString(DB_FIELD.MEDIA.getInsertUpdateOffset(), media);
    stmt.setString(DB_FIELD.NOTE.getInsertUpdateOffset(), note);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, StandardWell sw) throws SQLException {
    bindInsertOrUpdateParameters(stmt, sw.getPlateId(), sw.getPlateRow(), sw.getPlateColumn(),
        sw.getChemical(), sw.getMedia(), sw.getNote());
  }

  public StandardWell insert(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn,
      String chemical, String media, String note) throws SQLException {
    return INSTANCE.insert(db, new StandardWell(null, plateId, plateRow, plateColumn, chemical, media, note));
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
      String media = mediaMap != null ? mediaMap.get(coords) : null;
      Map<Pair<String, String>, String> notesMap = parser.getCompositionTables().get("note");
      String note = notesMap != null ? notesMap.get(coords) : null;
      Pair<Integer, Integer> index = parser.getCoordinatesToIndices().get(coords);
      StandardWell s = INSTANCE.insert(db, p.getId(), index.getLeft(), index.getRight(),
          chemical, media, note);

      results.add(s);
    }

    return results;
  }


  private String chemical;
  private String media;
  private String note;

  private StandardWell() { }

  protected StandardWell(Integer id, Integer plateId, Integer plateRow, Integer plateColumn,
                      String chemical, String media, String note) {
    this.id = id;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
    this.chemical = chemical;
    this.media = media;
    this.note = note;
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
