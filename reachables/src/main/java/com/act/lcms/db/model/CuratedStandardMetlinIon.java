package com.act.lcms.db.model;

import com.act.lcms.db.io.DB;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CuratedStandardMetlinIon extends BaseDBModel<CuratedStandardMetlinIon> {
  public static final String TABLE_NAME = "curated_standard_metlin_ion";

  protected static final CuratedStandardMetlinIon INSTANCE = new CuratedStandardMetlinIon();

  public static CuratedStandardMetlinIon getInstance() {
    return INSTANCE;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    CREATED_AT(2, 1, "created_at"),
    AUTHOR(3, 2, "author"),
    BEST_METLIN_ION(4, 3, "best_metlin_ion"),
    COMMENTS(5, 4, "comments"),
    STANDARD_ION_RESULT_ID(6, 5, "standard_ion_result_id");

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

  protected static final String GET_BY_ID_QUERY = CuratedStandardMetlinIon.getInstance().makeGetByIDQuery();
  @Override
  protected String getGetByIDQuery() {
    return GET_BY_ID_QUERY;
  }

  protected static final String INSERT_QUERY = CuratedStandardMetlinIon.getInstance().makeInsertQuery();
  @Override
  public String getInsertQuery() {
    return INSERT_QUERY;
  }

  protected static final String UPDATE_QUERY = CuratedStandardMetlinIon.getInstance().makeUpdateQuery();
  @Override
  public String getUpdateQuery() {
    return UPDATE_QUERY;
  }

  @Override
  protected List<CuratedStandardMetlinIon> fromResultSet(ResultSet resultSet)
      throws SQLException, IOException, ClassNotFoundException {
    List<CuratedStandardMetlinIon> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      String comments = resultSet.getString(DB_FIELD.COMMENTS.getOffset());
      Date createdAtDate = resultSet.getTimestamp(DB_FIELD.CREATED_AT.getOffset(), Calendar.getInstance());
      String bestMetlinIon = resultSet.getString(DB_FIELD.BEST_METLIN_ION.getOffset());
      Integer standardIonResultId = resultSet.getInt(DB_FIELD.STANDARD_ION_RESULT_ID.getOffset());
      String author = resultSet.getString(DB_FIELD.AUTHOR.getOffset());

      results.add(
          new CuratedStandardMetlinIon(id, comments, createdAtDate, bestMetlinIon, standardIonResultId, author));
    }

    return results;
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt,
      String comments,
      Date createdAtDate,
      String bestMetlinIon,
      Integer standardIonResultId,
      String author
  ) throws SQLException, IOException {
    stmt.setString(DB_FIELD.COMMENTS.getInsertUpdateOffset(), comments);
    stmt.setTimestamp(DB_FIELD.CREATED_AT.getInsertUpdateOffset(), new Timestamp(createdAtDate.getTime()));
    stmt.setString(DB_FIELD.BEST_METLIN_ION.getInsertUpdateOffset(), bestMetlinIon);
    stmt.setString(DB_FIELD.AUTHOR.getInsertUpdateOffset(), author);
    stmt.setInt(DB_FIELD.STANDARD_ION_RESULT_ID.getInsertUpdateOffset(), standardIonResultId);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, CuratedStandardMetlinIon curatedResult)
      throws SQLException, IOException {
    bindInsertOrUpdateParameters(
        stmt, curatedResult.getComments(), curatedResult.getCreatedAtDate(), curatedResult.getBestMetlinIon(),
        curatedResult.getStandardIonResultId(), curatedResult.getAuthor());
  }

  public static String getBestMetlinIon(DB db, Integer id) throws IOException, ClassNotFoundException, SQLException {
    return CuratedStandardMetlinIon.getInstance().getById(db, id).getBestMetlinIon();
  }

  private Integer id;
  private String comments;
  private Date createdAtDate;
  private String bestMetlinIon;
  private Integer standardIonResultId;
  private String author;

  public CuratedStandardMetlinIon() {}

  public CuratedStandardMetlinIon(
      Integer id,
      String comments,
      Date createdAtDate,
      String bestMetlinIon,
      Integer standardIonResultId,
      String author) {
    this.id = id;
    this.comments = comments;
    this.createdAtDate = createdAtDate;
    this.bestMetlinIon = bestMetlinIon;
    this.standardIonResultId = standardIonResultId;
    this.author = author;
  }

  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public Date getCreatedAtDate() {
    return createdAtDate;
  }

  public void setCreatedAtDate(Date date) {
    this.createdAtDate = date;
  }

  public String getBestMetlinIon() {
    return bestMetlinIon;
  }

  public void setBestMetlinIon(String bestMetlinIon) {
    this.bestMetlinIon = bestMetlinIon;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public Integer getStandardIonResultId() {
    return standardIonResultId;
  }

  public void setStandardIonResultId(Integer id) {
    this.standardIonResultId = id;
  }
}
