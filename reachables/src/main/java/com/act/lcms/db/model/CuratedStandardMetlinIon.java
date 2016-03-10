package com.act.lcms.db.model;

import com.act.lcms.db.io.DB;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class CuratedStandardMetlinIon extends BaseDBModel<CuratedStandardMetlinIon> {
  public static final String TABLE_NAME = "curated_standard_metlin_ion";

  protected static final CuratedStandardMetlinIon INSTANCE = new CuratedStandardMetlinIon();

  public static CuratedStandardMetlinIon getInstance() {
    return INSTANCE;
  }

  // Set the human readable time we persist in UTC format.
  public static final DateTimeZone utcDateTimeZone = DateTimeZone.forID("UTC");
  private static final Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    CREATED_AT(2, 1, "created_at"),
    // The author field contains names of the people who made the change, so for example, "Vijay Ramakrishnan"
    AUTHOR(3, 2, "author"),
    BEST_METLIN_ION(4, 3, "best_metlin_ion"),
    NOTE(5, 4, "note"),
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
      String note = resultSet.getString(DB_FIELD.NOTE.getOffset());
      LocalDateTime createdAtDate = new LocalDateTime(resultSet.getTimestamp(
          DB_FIELD.CREATED_AT.getOffset(), utcCalendar).getTime(), utcDateTimeZone);
      String bestMetlinIon = resultSet.getString(DB_FIELD.BEST_METLIN_ION.getOffset());
      Integer standardIonResultId = resultSet.getInt(DB_FIELD.STANDARD_ION_RESULT_ID.getOffset());
      String author = resultSet.getString(DB_FIELD.AUTHOR.getOffset());

      results.add(
          new CuratedStandardMetlinIon(id, note, createdAtDate, bestMetlinIon, standardIonResultId, author));
    }

    return results;
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt,
      String note,
      LocalDateTime createdAtDate,
      String bestMetlinIon,
      Integer standardIonResultId,
      String author
  ) throws SQLException, IOException {
    stmt.setString(DB_FIELD.NOTE.getInsertUpdateOffset(), note);
    stmt.setTimestamp(DB_FIELD.CREATED_AT.getInsertUpdateOffset(),
        new Timestamp(createdAtDate.toDateTime(utcDateTimeZone).getMillis()));
    stmt.setString(DB_FIELD.BEST_METLIN_ION.getInsertUpdateOffset(), bestMetlinIon);
    stmt.setString(DB_FIELD.AUTHOR.getInsertUpdateOffset(), author);
    stmt.setInt(DB_FIELD.STANDARD_ION_RESULT_ID.getInsertUpdateOffset(), standardIonResultId);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, CuratedStandardMetlinIon curatedResult)
      throws SQLException, IOException {
    bindInsertOrUpdateParameters(
        stmt, curatedResult.getNote(), curatedResult.getCreatedAtDate(), curatedResult.getBestMetlinIon(),
        curatedResult.getStandardIonResultId(), curatedResult.getAuthor());
  }

  // Insert/Update
  public static final String QUERY_INSERT_CURATED_METLIN_ION = StringUtils.join(new String[] {
      "INSERT INTO", TABLE_NAME, "(", StringUtils.join(INSERT_UPDATE_FIELDS, ", "), ") VALUES (",
      "?,", // 1 = created_at
      "?,", // 2 = author
      "?,", // 3 = best_metlin_ion
      "?,", // 4 = note
      "?", // 5 = standard_ion_result_id
      ")"
  }, " ");

  private CuratedStandardMetlinIon insertCuratedStandardMetlinIon(DB db, LocalDateTime createdAtDate, String author, String bestMetlinIon,
                                              String note, Integer standardIonResultId) throws SQLException, IOException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(QUERY_INSERT_CURATED_METLIN_ION, Statement.RETURN_GENERATED_KEYS)) {
      bindInsertOrUpdateParameters(stmt, note, createdAtDate, bestMetlinIon, standardIonResultId, author);
      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          return new CuratedStandardMetlinIon(id, note, createdAtDate, bestMetlinIon, standardIonResultId, author);
        } else {
          System.err.format("ERROR: could not retrieve autogenerated key for curated metlin ion\n");
          return null;
        }
      }
    }
  }

  public static CuratedStandardMetlinIon insertCuratedStandardMetlinIonIntoDB(
      DB db, LocalDateTime createdAtDate, String author, String bestMetlinIon, String note, Integer standardIonResultId)
      throws SQLException, IOException {
    return CuratedStandardMetlinIon.getInstance().insertCuratedStandardMetlinIon(
        db, createdAtDate, author, bestMetlinIon, note, standardIonResultId);
  }

  public static String getBestMetlinIon(DB db, Integer id) throws IOException, ClassNotFoundException, SQLException {
    return CuratedStandardMetlinIon.getInstance().getById(db, id).getBestMetlinIon();
  }

  private Integer id;
  private String note;
  private LocalDateTime createdAtDate;
  private String bestMetlinIon;
  private Integer standardIonResultId;
  private String author;

  public CuratedStandardMetlinIon() {}

  public CuratedStandardMetlinIon(
      Integer id,
      String note,
      LocalDateTime createdAtDate,
      String bestMetlinIon,
      Integer standardIonResultId,
      String author) {
    this.id = id;
    this.note = note;
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

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public LocalDateTime getCreatedAtDate() {
    return createdAtDate;
  }

  public void setCreatedAtDate(LocalDateTime date) {
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
