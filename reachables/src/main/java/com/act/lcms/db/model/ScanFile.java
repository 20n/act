package com.act.lcms.db.model;

import com.act.lcms.db.io.DB;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanFile {
  public static final String TABLE_NAME = "scan_files";

  public enum SCAN_MODE {
    POS,
    NEG,
  }

  public enum SCAN_FILE_TYPE {
    NC,
    MZML,
    RAW,
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    FILENAME(2, 1, "filename"),
    MODE(3, 2, "mode"),
    FILE_TYPE(4, 3, "file_type"),
    PLATE_ID(5, 4, "plate_id"),
    PLATE_ROW(6, 5, "plate_row"),
    PLATE_COLUMN(7, 6, "plate_column"),
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
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<ScanFile> fromResultSet(ResultSet resultSet) throws SQLException {
    List<ScanFile> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      String filename = resultSet.getString(DB_FIELD.FILENAME.getOffset());
      SCAN_MODE scanMode = SCAN_MODE.valueOf(resultSet.getString(DB_FIELD.MODE.getOffset()));
      SCAN_FILE_TYPE fileType = SCAN_FILE_TYPE.valueOf(resultSet.getString(DB_FIELD.FILE_TYPE.getOffset()));
      Integer plateId = resultSet.getInt(DB_FIELD.PLATE_ID.getOffset());
      if (resultSet.wasNull()) {
        plateId = null;
      }
      Integer plateRow = resultSet.getInt(DB_FIELD.PLATE_ROW.getOffset());
      if (resultSet.wasNull()) {
        plateRow = null;
      }
      Integer plateColumn = resultSet.getInt(DB_FIELD.PLATE_COLUMN.getOffset());
      if (resultSet.wasNull()) {
        plateColumn = null;
      }
      results.add(new ScanFile(id, filename, scanMode, fileType, plateId, plateRow, plateColumn));
    }

    return results;
  }

  // TODO: hoist this into some DBModel base class, or at least define it via an interface.
  protected static ScanFile expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<ScanFile> results = fromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }

  // Select
  public static final String QUERY_GET_SCAN_FILE_BY_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where id = ?",
  }, " ");
  public static ScanFile getScanFileByID(DB db, Integer id) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_SCAN_FILE_BY_ID)) {
      stmt.setInt(1, id);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("id = %d", id));
      }
    }
  }

  public static final String QUERY_GET_SCAN_FILE_BY_FILENAME = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where filename = ?",
  }, " ");
  public static ScanFile getScanFileByFilename(DB db, String filename) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_SCAN_FILE_BY_FILENAME)) {
      stmt.setString(1, filename);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("filename = %s", filename));
      }
    }
  }

  public static final String QUERY_GET_SCAN_FILE_BY_PLATE_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where plate_id = ?",
  }, " ");
  public static List<ScanFile> getScanFileByPlateID(DB db, Integer plateId) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_SCAN_FILE_BY_PLATE_ID)) {
      stmt.setInt(1, plateId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  public static final String QUERY_GET_SCAN_FILE_BY_PLATE_ID_ROW_AND_COLUMN = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where plate_id = ?",
      "and plate_row = ?",
      "and plate_column = ?",
      "order by filename"
  }, " ");
  public static List<ScanFile> getScanFileByPlateIDRowAndColumn(
      DB db, Integer plateId, Integer plateRow, Integer plateColumn) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_SCAN_FILE_BY_PLATE_ID_ROW_AND_COLUMN)) {
      stmt.setInt(1, plateId);
      stmt.setInt(2, plateRow);
      stmt.setInt(3, plateColumn);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  // Insert/Update
  public static final String QUERY_INSERT_SCAN_FILE = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = filename
      "?,", // 2 = mode
      "?,", // 3 = file_type
      "?,", // 4 = plate_id
      "?,", // 5 = plate_row
      "?", // 6 = plate_column
      ")"
  }, " ");

  protected static void bindInsertOrUpdateParameters(
      PreparedStatement stmt, String filename, SCAN_MODE mode, SCAN_FILE_TYPE fileType,
      Integer plateId, Integer plateRow, Integer plateColumn) throws SQLException {
    stmt.setString(DB_FIELD.FILENAME.getInsertUpdateOffset(), filename);
    stmt.setString(DB_FIELD.MODE.getInsertUpdateOffset(), mode.name());
    stmt.setString(DB_FIELD.FILE_TYPE.getInsertUpdateOffset(), fileType.name());
    if (plateId != null) {
      stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    } else {
      stmt.setNull(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), Types.INTEGER);
    }
    if (plateRow != null) {
      stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    } else {
      stmt.setNull(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), Types.INTEGER);
    }
    if (plateColumn != null) {
      stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    } else {
      stmt.setNull(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), Types.INTEGER);
    }
  }

  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, ScanFile sf) throws SQLException {
    bindInsertOrUpdateParameters(stmt, sf.getFilename(), sf.getMode(), sf.getFileType(),
        sf.getPlateId(), sf.getPlateRow(), sf.getPlateColumn());
  }

  public static ScanFile insertScanFile(
      DB db,
      String filename, SCAN_MODE mode, SCAN_FILE_TYPE fileType,
      Integer plateId, Integer plateRow, Integer plateColumn) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt =
             conn.prepareStatement(QUERY_INSERT_SCAN_FILE, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, filename, mode, fileType, plateId, plateRow, plateColumn);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new ScanFile(id, filename, mode, fileType, plateId, plateRow, plateColumn);
        } else {
          System.err.format("ERROR: could not retrieve autogenerated key for scan file %s\n", filename);
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
  public static final String QUERY_UPDATE_SCAN_FILE_BY_ID = StringUtils.join(new String[] {
      "UPDATE ", TABLE_NAME, "SET",
      StringUtils.join(UPDATE_STATEMENT_FIELDS_AND_BINDINGS.iterator(), ", "),
      "WHERE",
      "id = ?", // 7
  }, " ");
  public static boolean updateScanFile(DB db, ScanFile sf) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_SCAN_FILE_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, sf);
      stmt.setInt(INSERT_UPDATE_FIELDS.size() + 1, sf.getId());
      return stmt.executeUpdate() > 0;
    }
  }

  // Parsing/loading
  protected enum SCAN_NAME_COMPONENT {
    PLATE_NAME,
    PLATE_BARCODE,
    ROW,
    COLUMN,
    DATE,
    MODE,
    SCAN_PART,
    FILE_TYPE,
  }
  protected static final List<Pair<Pattern, Map<SCAN_NAME_COMPONENT, Integer>>> NAME_EXTRACTION_PATTERNS =
      new ArrayList<Pair<Pattern, Map<SCAN_NAME_COMPONENT, Integer>>> () {{
        add(Pair.of(
            Pattern.compile("^Plate(\\d+)_([A-Z]+)(\\d+)_(\\d{8})(?:_(Pos|Neg))?(\\d{2})?.(nc|mzML|raw)$"),
            new HashMap<SCAN_NAME_COMPONENT, Integer>() {{
              put(SCAN_NAME_COMPONENT.PLATE_BARCODE, 1);
              put(SCAN_NAME_COMPONENT.ROW, 2);
              put(SCAN_NAME_COMPONENT.COLUMN, 3);
              put(SCAN_NAME_COMPONENT.DATE, 4);
              put(SCAN_NAME_COMPONENT.MODE, 5);
              put(SCAN_NAME_COMPONENT.SCAN_PART, 6);
              put(SCAN_NAME_COMPONENT.FILE_TYPE, 7);
            }}
        ));
        add(Pair.of(
            Pattern.compile("^Plate(\\d+)_([A-Z]+)(\\d+)_(\\d+)_Full(\\d{2})?.(nc|mzML|raw)$"),
            new HashMap<SCAN_NAME_COMPONENT, Integer>() {{
              put(SCAN_NAME_COMPONENT.PLATE_BARCODE, 1);
              put(SCAN_NAME_COMPONENT.ROW, 2);
              put(SCAN_NAME_COMPONENT.COLUMN, 3);
              put(SCAN_NAME_COMPONENT.DATE, 4);
              put(SCAN_NAME_COMPONENT.SCAN_PART, 5);
              put(SCAN_NAME_COMPONENT.FILE_TYPE, 6);
            }}
        ));
        add(Pair.of(
            Pattern.compile("^(\\w+)_([A-Z]+)(\\d+)_(\\d{8})(?:_(Pos|Neg))?(\\d{2})?.(nc|mzML|raw)"),
            new HashMap<SCAN_NAME_COMPONENT, Integer>() {{
              put(SCAN_NAME_COMPONENT.PLATE_NAME, 1);
              put(SCAN_NAME_COMPONENT.ROW, 2);
              put(SCAN_NAME_COMPONENT.COLUMN, 3);
              put(SCAN_NAME_COMPONENT.DATE, 4);
              put(SCAN_NAME_COMPONENT.MODE, 5);
              put(SCAN_NAME_COMPONENT.SCAN_PART, 6);
              put(SCAN_NAME_COMPONENT.FILE_TYPE, 7);
            }}
        ));

      }};
  public static final Integer LCMS_MAIN_SCAN_PART = 1;
  public static List<Pair<Integer, DB.OPERATION_PERFORMED>> insertOrUpdateScanFilesInDirectory(DB db, File directory)
      throws SQLException, IOException {
    if (directory == null || !directory.isDirectory()) {
      throw new RuntimeException(
          String.format("Scan files directory at %s is not a directory",
              directory == null ? null : directory.getAbsolutePath()));
    }

    List<Pair<Integer, DB.OPERATION_PERFORMED>> results = new ArrayList<>();

    File[] contentsArr = directory.listFiles();

    if (contentsArr == null || contentsArr.length == 0) {
      System.err.format("WARNING: no files found in directory %s", directory.getAbsolutePath());
      return null;
    }
    List<File> contents = Arrays.asList(contentsArr);
    Collections.sort(contents, new Comparator<File>() {
      @Override
      public int compare(File o1, File o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    for (File f : contents) {
      for (Pair<Pattern, Map<SCAN_NAME_COMPONENT, Integer>> scan : NAME_EXTRACTION_PATTERNS) {
        Pattern p = scan.getLeft();
        Map<SCAN_NAME_COMPONENT, Integer> groupMap = scan.getRight();
        Matcher m = p.matcher(f.getName());
        if (m.matches()) {
          if (groupMap.containsKey(SCAN_NAME_COMPONENT.SCAN_PART)) {
            String scanPartStr = m.group(groupMap.get(SCAN_NAME_COMPONENT.SCAN_PART));
            if (scanPartStr != null && !scanPartStr.isEmpty()) {
              Integer scanPart = Integer.parseInt(scanPartStr);
              if (!LCMS_MAIN_SCAN_PART.equals(scanPart)) {
                break;
              }
            }
          }

          Plate plate;
          Integer plateId = null;

          if (f.getName().startsWith("STD_MEOH")) {
            // The toffeStandard plate doesn't follow the usual naming convention, so we fake it here.
            plate = Plate.getPlateByBarcode(db, "toffeeStandards");
          } else if (groupMap.containsKey(SCAN_NAME_COMPONENT.PLATE_BARCODE)) {
            plate = Plate.getPlateByBarcode(db, m.group((groupMap.get(SCAN_NAME_COMPONENT.PLATE_BARCODE))));
          } else if (groupMap.containsKey(SCAN_NAME_COMPONENT.PLATE_NAME)) {
            plate = Plate.getPlateByName(db, m.group((groupMap.get(SCAN_NAME_COMPONENT.PLATE_NAME))));
          } else {
            // The occurrence of this exception represents a developer oversight.
            throw new RuntimeException(String.format("No plate identifier available for pattern %s", p));
          }
          if (plate == null) {
            System.err.format("WARNING: unable to find plate for scan file %s\n", f.getName());
          } else {
            plateId = plate.getId();
          }

          Integer plateRow = null, plateColumn = null;
          if (groupMap.containsKey(SCAN_NAME_COMPONENT.ROW)) {
            String plateRowStr = m.group(groupMap.get(SCAN_NAME_COMPONENT.ROW));
            if (plateRowStr != null && !plateRowStr.isEmpty()) {
              if (plateRowStr.length() > 1) {
                // TODO: handle larger plates?
                throw new RuntimeException(String.format("Unable to handle multi-character plate row %s for scan %s",
                    plateRowStr, f.getName()));
              }
              plateRow = plateRowStr.charAt(0) - 'A';
            }
          }

          if (groupMap.containsKey(SCAN_NAME_COMPONENT.COLUMN)) {
            String plateColumnStr = m.group(groupMap.get(SCAN_NAME_COMPONENT.COLUMN));
            if (plateColumnStr != null && !plateColumnStr.isEmpty()) {
              plateColumn = Integer.parseInt(plateColumnStr) - 1; // Wells are one-indexed.
            }
          }

          SCAN_MODE scanMode = SCAN_MODE.POS; // Assume positive scans by default.
          if (groupMap.containsKey(SCAN_NAME_COMPONENT.MODE)) {
            String scanModeStr = m.group(groupMap.get(SCAN_NAME_COMPONENT.MODE));
            if (scanModeStr != null && !scanModeStr.isEmpty()) {
              scanMode = SCAN_MODE.valueOf(scanModeStr.toUpperCase());
            }
          }

          SCAN_FILE_TYPE fileType = null;
          if (groupMap.containsKey(SCAN_NAME_COMPONENT.FILE_TYPE)) {
            String fileTypeStr = m.group(groupMap.get(SCAN_NAME_COMPONENT.FILE_TYPE));
            if (fileTypeStr != null && !fileTypeStr.isEmpty()) {
              fileType = SCAN_FILE_TYPE.valueOf(fileTypeStr.toUpperCase());
            }
          }

          ScanFile scanFile = getScanFileByFilename(db, f.getName());
          DB.OPERATION_PERFORMED op;
          if (scanFile == null) {
            scanFile = insertScanFile(db, f.getName(), scanMode, fileType, plateId, plateRow, plateColumn);
            op = DB.OPERATION_PERFORMED.CREATE;
          } else {
            scanFile.setFilename(f.getName());
            scanFile.setMode(scanMode);
            scanFile.setFileType(fileType);
            scanFile.setPlateId(plateId);
            scanFile.setPlateRow(plateRow);
            scanFile.setPlateColumn(plateColumn);
            updateScanFile(db, scanFile);
            op = DB.OPERATION_PERFORMED.UPDATE;
          }

          // Should only be null if we can't insert the scanFile into the DB for some reason.
          if (scanFile == null) {
            results.add(Pair.of((Integer)null, op));
          } else {
            results.add(Pair.of(scanFile.getId(), op));
          }
          break;
        }
      }
    }
    return results;
  }

  private Integer id;
  private String filename;
  private SCAN_MODE mode;
  private SCAN_FILE_TYPE fileType;
  private Integer plateId;
  private Integer plateRow;
  private Integer plateColumn;

  private ScanFile() { }

  protected ScanFile(Integer id, String filename, SCAN_MODE mode, SCAN_FILE_TYPE fileType,
                     Integer plateId, Integer plateRow, Integer plateColumn) {
    this.id = id;
    this.filename = filename;
    this.mode = mode;
    this.fileType = fileType;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
  }

  public Integer getId() {
    return id;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public SCAN_MODE getMode() {
    return mode;
  }

  public void setMode(SCAN_MODE mode) {
    this.mode = mode;
  }

  public SCAN_FILE_TYPE getFileType() {
    return fileType;
  }

  public void setFileType(SCAN_FILE_TYPE fileType) {
    this.fileType = fileType;
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
}
