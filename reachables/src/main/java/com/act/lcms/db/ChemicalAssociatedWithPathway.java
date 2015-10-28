package com.act.lcms.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChemicalAssociatedWithPathway extends BaseDBModel<ChemicalAssociatedWithPathway> {
  public static final String TABLE_NAME = "chemicals_associated_with_pathway";
  protected static final ChemicalAssociatedWithPathway INSTANCE = new ChemicalAssociatedWithPathway();

  public static ChemicalAssociatedWithPathway getInstance() {
    return INSTANCE;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    CONSTRUCT_ID(2, 1, "construct_id"),
    CHEMICAL(3, 2, "chemical"),
    KIND(4, 3, "kind"),
    INDEX(5, 4, "index"),
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

  @Override
  protected List<ChemicalAssociatedWithPathway> fromResultSet(ResultSet resultSet) throws SQLException {
    List<ChemicalAssociatedWithPathway> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.INDEX.getOffset());
      String constructId = resultSet.getString(DB_FIELD.CONSTRUCT_ID.getOffset());
      String chemical = resultSet.getString(DB_FIELD.CHEMICAL.getOffset());
      String kind = resultSet.getString(DB_FIELD.KIND.getOffset());
      Integer index = resultSet.getInt(DB_FIELD.INDEX.getOffset());
      if (resultSet.wasNull()) {
        index = null;
      }
      results.add(new ChemicalAssociatedWithPathway(id, constructId, chemical, kind, index));
    }

    return results;

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

  protected static final String QUERY_GET_CHEMICALS_ASSOCIATED_WITH_PATHWAY_BY_CONSTRUCT_ID =
      StringUtils.join(new String[]{
          "SELECT", StringUtils.join(INSTANCE.getAllFields(), ','),
          "from", INSTANCE.getTableName(),
          "where construct_id = ?",
          "order by index desc",
      }, " ");
  public List<ChemicalAssociatedWithPathway> getChemicalsAssociatedWithPathwayByConstructId(DB db, String constructId)
      throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(
        QUERY_GET_CHEMICALS_ASSOCIATED_WITH_PATHWAY_BY_CONSTRUCT_ID)) {
      stmt.setString(1, constructId);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return fromResultSet(resultSet);
      }
    }
  }

  protected static final String QUERY_GET_CHEMICAL_ASSOCIATED_WITH_PATHWAY_BY_CONSTRUCT_ID_AND_INDEX =
      StringUtils.join(new String[]{
          "SELECT", StringUtils.join(INSTANCE.getAllFields(), ','),
          "from", INSTANCE.getTableName(),
          "where construct_id = ?",
          "  and index = ?",
      }, " ");
  public ChemicalAssociatedWithPathway getChemicalAssociatedWithPathwayByConstructIdAndIndex(
      DB db, String constructId, Integer index) throws SQLException {
    try (PreparedStatement stmt = db.getConn().prepareStatement(
        QUERY_GET_CHEMICAL_ASSOCIATED_WITH_PATHWAY_BY_CONSTRUCT_ID_AND_INDEX)) {
      stmt.setString(1, constructId);
      stmt.setInt(2, index);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return expectOneResult(resultSet, String.format("construct_id = %s, index = %d", constructId, index));
      }
    }
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, String constructId, String chemical, String kind, Integer index) throws SQLException {
    stmt.setString(DB_FIELD.CONSTRUCT_ID.getInsertUpdateOffset(), constructId);
    stmt.setString(DB_FIELD.CHEMICAL.getInsertUpdateOffset(), chemical);
    stmt.setString(DB_FIELD.KIND.getInsertUpdateOffset(), kind);
    if (index != null) {
      stmt.setInt(DB_FIELD.INDEX.getInsertUpdateOffset(), index);
    } else {
      stmt.setNull(DB_FIELD.INDEX.getInsertUpdateOffset(), Types.INTEGER);
    }
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, ChemicalAssociatedWithPathway parameterSource)
      throws SQLException {
    bindInsertOrUpdateParameters(stmt, parameterSource.getConstructId(), parameterSource.getChemical(),
        parameterSource.getKind(), parameterSource.getIndex());
  }

  // Parsing/Loading
  public static List<Pair<Integer, DB.OPERATION_PERFORMED>> insertOrUpdateChemicalsAssociatedWithPathwayFromParser(
      DB db, ConstructAnalysisFileParser parser) throws SQLException {
    List<Pair<Integer, DB.OPERATION_PERFORMED>> operationsPerformed = new ArrayList<>();
    List<Pair<String, List<ConstructAnalysisFileParser.ConstructAssociatedChemical>>> stepPairs =
        parser.getConstructProducts();
    for (Pair<String, List<ConstructAnalysisFileParser.ConstructAssociatedChemical>> stepPair : stepPairs) {
      String constructId = stepPair.getLeft();
      for (ConstructAnalysisFileParser.ConstructAssociatedChemical step : stepPair.getRight()) {
        System.out.format("Processing entry %s %s %s %d\n", constructId, step.getChemical(),
            step.getKind(), step.getIndex());
        ChemicalAssociatedWithPathway cp =
            INSTANCE.getChemicalAssociatedWithPathwayByConstructIdAndIndex(db, constructId, step.getIndex());

        DB.OPERATION_PERFORMED op = null;
        if (cp == null) {
          cp = INSTANCE.insert(db, new ChemicalAssociatedWithPathway(
              null, constructId, step.getChemical(), step.getKind(), step.getIndex()));
          op = DB.OPERATION_PERFORMED.CREATE;
        } else {
          cp.setConstructId(constructId);
          cp.setChemical(step.getChemical());
          cp.setKind(step.getKind());
          cp.setIndex(step.getIndex());
          INSTANCE.update(db, cp);
          op = DB.OPERATION_PERFORMED.UPDATE;
        }

        // Chem should only be null if we couldn't insert the row into the DB.
        if (cp == null) {
          operationsPerformed.add(Pair.of((Integer)null, DB.OPERATION_PERFORMED.ERROR));
        } else {
          operationsPerformed.add(Pair.of(cp.getId(), op));
        }
      }
    }
    return operationsPerformed;
  }

  private String constructId;
  private String chemical;
  private String kind;
  private Integer index;

  private ChemicalAssociatedWithPathway() { }

  protected ChemicalAssociatedWithPathway(Integer id, String constructId, String chemical, String kind, Integer index) {
    this.id = id;
    this.constructId = constructId;
    this.chemical = chemical;
    this.kind = kind;
    this.index = index;
  }

  public String getConstructId() {
    return constructId;
  }

  public void setConstructId(String constructId) {
    this.constructId = constructId;
  }

  public String getChemical() {
    return chemical;
  }

  public void setChemical(String chemical) {
    this.chemical = chemical;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }
}
