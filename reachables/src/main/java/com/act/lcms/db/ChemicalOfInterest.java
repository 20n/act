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
import java.util.List;
import java.util.Map;

public class ChemicalOfInterest {
  public static final String TABLE_NAME = "chemicals_of_interest";

  public static String getTableName() {
    return TABLE_NAME;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    NAME(2, 1, "name"),
    INCHI(3, 2, "inchi"),
    DESCRIPTOR(4, 3, "descriptor"),
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
  // TODO: it might be easier to use parts of Spring-Standalone to do named binding in these queries.
  protected static final List<String> ALL_FIELDS = Collections.unmodifiableList(Arrays.asList(DB_FIELD.names()));
  // id is auto-generated on insertion.
  protected static final List<String> INSERT_UPDATE_FIELDS =
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size()));

  protected static List<ChemicalOfInterest> fromResultSet(ResultSet resultSet) throws SQLException {
    List<ChemicalOfInterest> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      String name = resultSet.getString(DB_FIELD.NAME.getOffset());
      String inchi = resultSet.getString(DB_FIELD.INCHI.getOffset());
      String descriptor = resultSet.getString(DB_FIELD.DESCRIPTOR.getOffset());
      results.add(new ChemicalOfInterest(id, name, inchi, descriptor));
    }

    return results;
  }

  protected static ChemicalOfInterest expectOneResult(ResultSet resultSet, String queryErrStr) throws SQLException{
    List<ChemicalOfInterest> results = fromResultSet(resultSet);
    if (results.size() > 1) {
      throw new SQLException("Found multiple results where one or zero expected: %s", queryErrStr);
    }
    if (results.size() == 0) {
      return null;
    }
    return results.get(0);
  }

  // Select
  public static final String QUERY_GET_CURATED_CHEMICAL_BY_ID = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where id = ?",
  }, " ");
  public static ChemicalOfInterest getChemicalOfInterestById(DB db, Integer id) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_CURATED_CHEMICAL_BY_ID)) {
      stmt.setInt(1, id);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("id = %d", id));
      }
    }
  }

  public static final String QUERY_GET_CURATED_CHEMICAL_BY_NAME = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where name = ?",
  }, " ");
  public static ChemicalOfInterest getChemicalOfInterestByName(DB db, String name) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_CURATED_CHEMICAL_BY_NAME)) {
      stmt.setString(1, name);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("name = %s", name));
      }
    }
  }

  public static final String QUERY_GET_CURATED_CHEMICAL_BY_INCHI = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(ALL_FIELDS, ", "),
      "from", TABLE_NAME,
      "where inchi = ?",
  }, " ");
  public static ChemicalOfInterest getChemicalOfInterestByInChI(DB db, String inchi) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_CURATED_CHEMICAL_BY_INCHI)) {
      stmt.setString(1, inchi);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("inchi = %s", inchi));
      }
    }
  }

  // Insert/Update
  public static final String QUERY_INSERT_CURATED_CHEMICAL = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = name
      "?,", // 2 = inchi
      "?",  // 3 = descriptor
      ")"
  }, " ");

  protected static void bindInsertOrUpdateParameters(
      PreparedStatement stmt, String name, String inchi, String descriptor) throws SQLException {
    stmt.setString(DB_FIELD.NAME.getInsertUpdateOffset(), name);
    stmt.setString(DB_FIELD.INCHI.getInsertUpdateOffset(), inchi);
    stmt.setString(DB_FIELD.DESCRIPTOR.getInsertUpdateOffset(), descriptor);
  }

  // TODO: this could return the number of parameters it bound to make it easier to set additional params.
  protected static void bindInsertOrUpdateParameters(PreparedStatement stmt, ChemicalOfInterest c) throws SQLException {
    bindInsertOrUpdateParameters(stmt, c.getName(), c.getInchi(), c.getDescriptor());
  }

  public static ChemicalOfInterest insertChemicalOfInterest(
      DB db,
      String name, String inchi, String descriptor) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt =
             conn.prepareStatement(QUERY_INSERT_CURATED_CHEMICAL, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, name, inchi, descriptor);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new ChemicalOfInterest(id, name, inchi, descriptor);
        } else {
          System.err.format("ERROR: could not retrieve autogenerated key for chemical of interest %s\n", name);
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
  public static final String QUERY_UPDATE_CURATED_CHEMICAL_BY_ID = StringUtils.join(new String[]{
      "UPDATE", TABLE_NAME, "SET",
      StringUtils.join(UPDATE_STATEMENT_FIELDS_AND_BINDINGS.iterator(), ", "),
      "WHERE",
      "id = ?",
  }, " ");
  public static boolean updateChemicalOfInterest(DB db, ChemicalOfInterest chem) throws SQLException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_CURATED_CHEMICAL_BY_ID)) {
      bindInsertOrUpdateParameters(stmt, chem);
      stmt.setInt(INSERT_UPDATE_FIELDS.size() + 1, chem.getId());
      return stmt.executeUpdate() > 0;
    }
  }

  public static List<Pair<Integer, DB.OPERATION_PERFORMED>> insertOrUpdateChemicalOfInterestsFromTSV(
      DB db, TSVParser parser) throws SQLException{
    List<Map<String, String>> entries = parser.getResults();
    List<Pair<Integer, DB.OPERATION_PERFORMED>> operationsPerformed = new ArrayList<>(entries.size());
    for (Map<String, String> entry : entries) {
      String name = entry.get("name");
      String inchi = entry.get("inchi");
      String descriptor = entry.get("descriptor");

      // TODO: should we fall back to searching by name if we can't find the InChI?
      ChemicalOfInterest chem = getChemicalOfInterestByInChI(db, inchi);
      DB.OPERATION_PERFORMED op = null;
      if (chem == null) {
        chem = insertChemicalOfInterest(db, name, inchi, descriptor);
        op = DB.OPERATION_PERFORMED.CREATE;
      } else {
        chem.setName(name);
        chem.setInchi(inchi);
        chem.setDescriptor(descriptor);
        updateChemicalOfInterest(db, chem);
        op = DB.OPERATION_PERFORMED.UPDATE;
      }

      // Chem should only be null if we couldn't insert the row into the DB.
      if (chem == null) {
        operationsPerformed.add(Pair.of((Integer)null, DB.OPERATION_PERFORMED.ERROR));
      } else {
        operationsPerformed.add(Pair.of(chem.getId(), op));
      }
    }
    return operationsPerformed;
  }


  private Integer id;
  private String name;
  private String inchi;
  private String descriptor;

  protected ChemicalOfInterest(Integer id, String name, String inchi, String descriptor) {
    this.id = id;
    this.name = name;
    this.inchi = inchi;
    this.descriptor = descriptor;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getInchi() {
    return inchi;
  }

  public void setInchi(String inchi) {
    this.inchi = inchi;
  }

  public String getDescriptor() {
    return descriptor;
  }

  public void setDescriptor(String descriptor) {
    this.descriptor = descriptor;
  }
}
