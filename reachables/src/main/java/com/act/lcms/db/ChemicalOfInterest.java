package com.act.lcms.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChemicalOfInterest extends TabularData<ChemicalOfInterest> {
  public static final String TABLE_NAME = "chemicals_of_interest";
  protected static final ChemicalOfInterest INSTANCE = new ChemicalOfInterest();

  public static ChemicalOfInterest getInstance() {
    return INSTANCE;
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
  protected List<ChemicalOfInterest> fromResultSet(ResultSet resultSet) throws SQLException {
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

  public static final String QUERY_GET_CURATED_CHEMICAL_BY_NAME = INSTANCE.makeGetQueryForSelectField("name");
  public List<ChemicalOfInterest> getChemicalOfInterestByName(DB db, String name) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_CURATED_CHEMICAL_BY_NAME)) {
      stmt.setString(1, name);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  public static final String QUERY_GET_CURATED_CHEMICAL_BY_INCHI = INSTANCE.makeGetQueryForSelectField("inchi");
  public List<ChemicalOfInterest> getChemicalOfInterestByInChI(DB db, String inchi) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(QUERY_GET_CURATED_CHEMICAL_BY_INCHI)) {
      stmt.setString(1, inchi);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  public static final String QUERY_GET_CURATED_CHEMICAL_BY_NAME_INCHI_AND_DESCRIPTOR = StringUtils.join(new String[]{
      "SELECT", StringUtils.join(INSTANCE.getAllFields(), ','),
      "from", INSTANCE.getTableName(),
      "where name = ?",
      "  and inchi = ?",
      "  and descriptor = ?",
  }, " ");
  public ChemicalOfInterest getChemicalOfInterestByNameInChIAndDescriptor(
      DB db, String name, String inchi, String descriptor) throws SQLException {
    try (PreparedStatement stmt =
             db.getConn().prepareStatement(QUERY_GET_CURATED_CHEMICAL_BY_NAME_INCHI_AND_DESCRIPTOR)) {
      stmt.setString(1, name);
      stmt.setString(2, inchi);
      stmt.setString(3, descriptor);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet,
            String.format("name = %s, inchi = %s, descriptor = %s", name, inchi, descriptor));
      }
    }
  }

  // Insert/Update
  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, String name, String inchi, String descriptor) throws SQLException {
    stmt.setString(DB_FIELD.NAME.getInsertUpdateOffset(), name);
    stmt.setString(DB_FIELD.INCHI.getInsertUpdateOffset(), inchi);
    stmt.setString(DB_FIELD.DESCRIPTOR.getInsertUpdateOffset(), descriptor);
  }

  // TODO: this could return the number of parameters it bound to make it easier to set additional params.
  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, ChemicalOfInterest c) throws SQLException {
    bindInsertOrUpdateParameters(stmt, c.getName(), c.getInchi(), c.getDescriptor());
  }

  public ChemicalOfInterest insert(DB db, String name, String inchi, String descriptor) throws SQLException {
    return INSTANCE.insert(db, new ChemicalOfInterest(null, name, inchi, descriptor));
  }

  // Parsing/Loading
  public static List<Pair<Integer, DB.OPERATION_PERFORMED>> insertOrUpdateChemicalOfInterestsFromTSV(
      DB db, TSVParser parser) throws SQLException{
    List<Map<String, String>> entries = parser.getResults();
    List<Pair<Integer, DB.OPERATION_PERFORMED>> operationsPerformed = new ArrayList<>(entries.size());
    for (Map<String, String> entry : entries) {
      String name = entry.get("name");
      String inchi = entry.get("inchi");
      String descriptor = entry.get("descriptor");

      // TODO: should we fall back to searching by name if we can't find the InChI?
      ChemicalOfInterest chem = INSTANCE.getChemicalOfInterestByNameInChIAndDescriptor(db, name, inchi, descriptor);
      DB.OPERATION_PERFORMED op = null;
      if (chem == null) {
        chem = INSTANCE.insert(db, name, inchi, descriptor);
        op = DB.OPERATION_PERFORMED.CREATE;
      } else {
        chem.setName(name);
        chem.setInchi(inchi);
        chem.setDescriptor(descriptor);
        INSTANCE.update(db, chem);
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


  private String name;
  private String inchi;
  private String descriptor;

  private ChemicalOfInterest() { }

  protected ChemicalOfInterest(Integer id, String name, String inchi, String descriptor) {
    this.id = id;
    this.name = name;
    this.inchi = inchi;
    this.descriptor = descriptor;
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
