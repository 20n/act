package com.act.lcms.db.model;

import com.act.lcms.db.DB;
import com.act.lcms.db.PlateCompositionParser;
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
  protected static final PregrowthWell INSTANCE = new PregrowthWell();

  public static PregrowthWell getInstance() {
    return INSTANCE;
  }

  public static final String TABLE_NAME = "wells_pregrowth";

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    PLATE_ID(2, 1, "plate_id"),
    PLATE_ROW(3, 2, "plate_row"),
    PLATE_COLUMN(4, 3, "plate_column"),
    SOURCE_PLATE(5, 4, "source_plate"),
    SOURCE_WELL(6, 5, "source_well"),
    MSID(7, 6, "msid"),
    COMPOSITION(8, 7, "composition"),
    NOTE(9, 8, "note"),
    GROWTH(10, 9, "growth"),
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
  protected List<PregrowthWell> fromResultSet(ResultSet resultSet) throws SQLException {
    List<PregrowthWell> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      Integer plateId = resultSet.getInt(DB_FIELD.PLATE_ID.getOffset());
      Integer plateRow = resultSet.getInt(DB_FIELD.PLATE_ROW.getOffset());
      Integer plateColumn = resultSet.getInt(DB_FIELD.PLATE_COLUMN.getOffset());
      String sourcePlate = resultSet.getString(DB_FIELD.SOURCE_PLATE.getOffset());
      String sourceWell = resultSet.getString(DB_FIELD.SOURCE_WELL.getOffset());
      String msid = resultSet.getString(DB_FIELD.MSID.getOffset());
      String composition = resultSet.getString(DB_FIELD.COMPOSITION.getOffset());
      String note = resultSet.getString(DB_FIELD.NOTE.getOffset());
      Integer growth = resultSet.getInt(DB_FIELD.GROWTH.getOffset());
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
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setString(DB_FIELD.SOURCE_PLATE.getInsertUpdateOffset(), sourcePlate);
    stmt.setString(DB_FIELD.SOURCE_WELL.getInsertUpdateOffset(), sourceWell);
    stmt.setString(DB_FIELD.MSID.getInsertUpdateOffset(), msid);
    stmt.setString(DB_FIELD.COMPOSITION.getInsertUpdateOffset(), composition);
    stmt.setString(DB_FIELD.NOTE.getInsertUpdateOffset(), note);
    if (growth == null) {
      stmt.setNull(DB_FIELD.GROWTH.getInsertUpdateOffset(), Types.INTEGER);
    } else {
      stmt.setInt(DB_FIELD.GROWTH.getInsertUpdateOffset(), growth);
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
          growth = Integer.parseInt(trimAndComplain(plateGrowth));
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

  public static String trimAndComplain(String val) {
    String tval = val.trim();
    if (!val.equals(tval)) {
      System.err.format("WARNING: trimmed spurious whitespace from '%s'\n", val);
    }
    return tval;
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
