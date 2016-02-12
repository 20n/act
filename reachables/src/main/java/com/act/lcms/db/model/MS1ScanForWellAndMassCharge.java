package com.act.lcms.db.model;

import com.act.lcms.MS1;
import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MS1ScanForWellAndMassCharge extends BaseDBModel<MS1ScanForWellAndMassCharge> implements Serializable {

  private static final long serialVersionUID = 8606939292070032578L;

  public static final String TABLE_NAME = "ms1_for_well_and_mass_charge";

  protected static final MS1ScanForWellAndMassCharge INSTANCE = new MS1ScanForWellAndMassCharge();

  public static MS1ScanForWellAndMassCharge getInstance() {
    return INSTANCE;
  }

  private static final Integer NUM_OF_RESULT_COLUMNS = 8;

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    PLATE_ID(2, 1, "plate_id"),
    PLATE_ROW(3, 2, "plate_row"),
    PLATE_COLUMN(4, 3, "plate_column"),
    ION_MASS_CHARGE(5, 4, "ion_mass_charge"),
    USE_SNR(6, 5, "use_snr"),
    SCAN_FILE(7, 6, "scan_file"),
    METLIN_IONS(8, 7, "metlin_ions"),
    IONS_TO_SPECTRA(9, 8, "ions_to_spectra"),
    IONS_TO_INTEGRAL(10, 9, "ions_to_integral"),
    IONS_TO_MAX(11, 10, "ions_to_max"),
    IONS_TO_LOG_SNR(12, 11, "ions_to_log_snr"),
    IONS_TO_AVG_SIGNAL(13, 12, "ions_to_avg_signal"),
    IONS_TO_AVG_AMBIENT(14, 13, "ions_to_avg_ambient"),
    INDIVIDUAL_MAX_INTENSITIES(15, 14, "individual_max_intensities"),
    MAX_Y_AXIS(16, 15, "max_y_axis");

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
      Collections.unmodifiableList(ALL_FIELDS.subList(1, ALL_FIELDS.size() - NUM_OF_RESULT_COLUMNS));

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

  protected static final String GET_BY_ID_QUERY = MS1ScanForWellAndMassCharge.getInstance().makeGetByIDQuery();
  @Override
  protected String getGetByIDQuery() {
    return GET_BY_ID_QUERY;
  }

  protected static final String INSERT_QUERY = MS1ScanForWellAndMassCharge.getInstance().makeInsertQuery();
  @Override
  public String getInsertQuery() {
    return INSERT_QUERY;
  }

  protected static final String UPDATE_QUERY = MS1ScanForWellAndMassCharge.getInstance().makeUpdateQuery();
  @Override
  public String getUpdateQuery() {
    return UPDATE_QUERY;
  }

  @Override
  protected List<MS1ScanForWellAndMassCharge> fromResultSet(ResultSet resultSet)
      throws SQLException, IOException, ClassNotFoundException {
    List<MS1ScanForWellAndMassCharge> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      Integer plateId = resultSet.getInt(DB_FIELD.PLATE_ID.getOffset());
      Integer plateRow = resultSet.getInt(DB_FIELD.PLATE_ROW.getOffset());
      Integer plateColumn = resultSet.getInt(DB_FIELD.PLATE_COLUMN.getOffset());
      Double ionMz = resultSet.getDouble(DB_FIELD.ION_MASS_CHARGE.getOffset());
      Double maxYAxis = resultSet.getDouble(DB_FIELD.MAX_Y_AXIS.getOffset());
      Boolean useSNR = resultSet.getBoolean(DB_FIELD.USE_SNR.getOffset());
      String lcmsScanFilePath = resultSet.getString(DB_FIELD.SCAN_FILE.getOffset());

      Map<String, Double> metlinIons = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.METLIN_IONS.getOffset()));
      Map<String, List<XZ>> ionsToSpectra = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.IONS_TO_SPECTRA.getOffset()));
      Map<String, Double> ionsToIntegral = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.IONS_TO_INTEGRAL.getOffset()));
      Map<String, Double> ionsToMax = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.IONS_TO_MAX.getOffset()));
      Map<String, Double> ionsToLogSNR = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.IONS_TO_LOG_SNR.getOffset()));
      Map<String, Double> ionsToAvgSignal = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.IONS_TO_AVG_SIGNAL.getOffset()));
      Map<String, Double> ionsToAvgAmbient = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.IONS_TO_AVG_AMBIENT.getOffset()));
      Map<String, Double> individualMaxIntensities = MS1ScanForWellAndMassCharge.deserialize(
          resultSet.getBytes(DB_FIELD.INDIVIDUAL_MAX_INTENSITIES.getOffset()));

      results.add(new MS1ScanForWellAndMassCharge(id, plateId, plateColumn, plateRow, ionMz, useSNR, lcmsScanFilePath,
          metlinIons, ionsToSpectra, ionsToIntegral, ionsToMax, ionsToLogSNR, ionsToAvgSignal, ionsToAvgAmbient,
          individualMaxIntensities, maxYAxis));
    }

    return results;
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      Double ionMZ, Boolean useSNR, String lcmsScanFileDir, Map<String, Double> metlinIons, Map<String,List<XZ>> ionsToSpectra,
      Map<String, Double> ionsToIntegral, Map<String, Double> ionsToMax, Map<String, Double> ionsToLogSNR,
      Map<String, Double> ionsToAvgSignal, Map<String, Double> ionsToAvgAmbient,
      Map<String, Double> individualMaxIntensities, Double maxYAxis) throws SQLException, IOException {
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setDouble(DB_FIELD.ION_MASS_CHARGE.getInsertUpdateOffset(), ionMZ);
    stmt.setBoolean(DB_FIELD.USE_SNR.getInsertUpdateOffset(), useSNR);
    stmt.setString(DB_FIELD.SCAN_FILE.getInsertUpdateOffset(), lcmsScanFileDir);
    stmt.setBinaryStream(
        DB_FIELD.METLIN_IONS.getInsertUpdateOffset(), MS1ScanForWellAndMassCharge.serialize(metlinIons));
    stmt.setBinaryStream(
        DB_FIELD.IONS_TO_SPECTRA.getInsertUpdateOffset(), MS1ScanForWellAndMassCharge.serialize(ionsToSpectra));
    stmt.setBinaryStream(
        DB_FIELD.IONS_TO_INTEGRAL.getInsertUpdateOffset(), MS1ScanForWellAndMassCharge.serialize(ionsToIntegral));
    stmt.setBinaryStream(
        DB_FIELD.IONS_TO_LOG_SNR.getInsertUpdateOffset(), MS1ScanForWellAndMassCharge.serialize(ionsToLogSNR));
    stmt.setBinaryStream(
        DB_FIELD.IONS_TO_AVG_AMBIENT.getInsertUpdateOffset(), MS1ScanForWellAndMassCharge.serialize(ionsToAvgAmbient));
    stmt.setBinaryStream(
        DB_FIELD.IONS_TO_AVG_SIGNAL.getInsertUpdateOffset(), MS1ScanForWellAndMassCharge.serialize(ionsToAvgSignal));
    stmt.setBinaryStream(
        DB_FIELD.INDIVIDUAL_MAX_INTENSITIES.getInsertUpdateOffset(),
        MS1ScanForWellAndMassCharge.serialize(individualMaxIntensities));
    stmt.setBinaryStream(
        DB_FIELD.IONS_TO_MAX.getInsertUpdateOffset(), MS1ScanForWellAndMassCharge.serialize(ionsToMax));
    stmt.setDouble(DB_FIELD.MAX_Y_AXIS.getInsertUpdateOffset(), maxYAxis);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, MS1ScanForWellAndMassCharge ms1Result)
      throws SQLException, IOException {
    bindInsertOrUpdateParameters(
        stmt, ms1Result.getPlateId(), ms1Result.getPlateRow(), ms1Result.getPlateColumn(), ms1Result.getIonMZ(),
        ms1Result.getUseSNR(), ms1Result.getScanFilePath(), ms1Result.getMetlinIons(), ms1Result.getIonsToSpectra(),
        ms1Result.getIonsToIntegral(), ms1Result.getIonsToMax(), ms1Result.getIonsToLogSNR(), ms1Result.getIonsToAvgSignal(),
        ms1Result.getIonsToAvgAmbient(), ms1Result.getIndividualMaxIntensities(), ms1Result.getMaxYAxis());
  }

  private Integer id;
  private Integer plate_id;
  private Integer plate_row;
  private Integer plate_column;
  private Double ionMZ;
  private Boolean useSNR;
  private String lcmsScanFileDir;
  private Map<String, Double> metlinIons = new HashMap<>();
  private Map<String, List<XZ>> ionsToSpectra = new HashMap<>();
  private Map<String, Double> ionsToIntegral = new HashMap<>();
  private Map<String, Double> ionsToMax = new HashMap<>();
  private Map<String, Double> ionsToLogSNR = new HashMap<>();
  private Map<String, Double> ionsToAvgSignal = new HashMap<>();
  private Map<String, Double> ionsToAvgAmbient = new HashMap<>();
  private Map<String, Double> individualMaxIntensities = new HashMap<>();
  private Double maxYAxis = 0.0d; // default to 0

  public MS1ScanForWellAndMassCharge() {}

  public MS1ScanForWellAndMassCharge(Integer id, Integer plate_id, Integer plate_column, Integer plate_row,
                                     Double ionMZ, Boolean useSNR, String lcmsScanFileDir, Map<String, Double> metlinIons,
                                     Map<String, List<XZ>> ionsToSpectra, Map<String, Double> ionsToIntegral,
                                     Map<String, Double> ionsToMax, Map<String, Double> ionsToAvgSignal,
                                     Map<String, Double> ionsToAvgAmbient, Map<String, Double> ionsToLogSNR,
                                     Map<String, Double> individualMaxIntensities, Double maxYAxis) {
    this.id = id;
    this.useSNR = useSNR;
    this.lcmsScanFileDir = lcmsScanFileDir;
    this.ionsToSpectra = ionsToSpectra;
    this.ionsToAvgAmbient = ionsToAvgAmbient;
    this.ionsToAvgSignal = ionsToAvgSignal;
    this.ionsToIntegral = ionsToIntegral;
    this.individualMaxIntensities = individualMaxIntensities;
    this.maxYAxis = maxYAxis;
    this.ionsToMax = ionsToMax;
    this.plate_column = plate_column;
    this.plate_id = plate_id;
    this.plate_row = plate_row;
    this.ionMZ = ionMZ;
    this.ionsToLogSNR = ionsToLogSNR;
    this.metlinIons = metlinIons;
  }

  public static ByteArrayInputStream serialize(Object object) throws IOException {
    ByteArrayOutputStream preGzipOutputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream postGzipOutputStream = new ByteArrayOutputStream();

    ObjectOutputStream out = null;
    GZIPOutputStream gzipOut = null;
    try {
      out = new ObjectOutputStream(preGzipOutputStream);
      out.writeObject(object);
      gzipOut = new GZIPOutputStream(postGzipOutputStream);
      gzipOut.write(preGzipOutputStream.toByteArray());
    } finally {
      if (gzipOut != null) { gzipOut.close(); }
      if (out != null) { out.close(); }
      if (preGzipOutputStream != null) { preGzipOutputStream.close(); }
      if (postGzipOutputStream != null) { postGzipOutputStream.close(); }
    }

    return new ByteArrayInputStream(postGzipOutputStream.toByteArray());
  }

  public static <T> T deserialize(byte[] object) throws IOException, ClassNotFoundException {
    T map = null;

    try (ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(object)))) {
      map = (T) ois.readObject();
    }

    return map;
  }

  public MS1ScanForWellAndMassCharge insert(
      DB db, MS1ScanForWellAndMassCharge ms1Result)
      throws SQLException, IOException {

    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(MS1ScanForWellAndMassCharge.getInstance().getInsertQuery(),
        Statement.RETURN_GENERATED_KEYS)) {

      bindInsertOrUpdateParameters(stmt, ms1Result.getPlateId(), ms1Result.getPlateRow(),
          ms1Result.getPlateColumn(), ms1Result.getIonMZ(), ms1Result.getUseSNR(), ms1Result.getScanFilePath(),
          ms1Result.getMetlinIons(), ms1Result.getIonsToSpectra(), ms1Result.getIonsToIntegral(),
          ms1Result.getIonsToMax(), ms1Result.getIonsToAvgSignal(), ms1Result.getIonsToAvgAmbient(),
          ms1Result.getIonsToLogSNR(), ms1Result.getIndividualMaxIntensities(), ms1Result.getMaxYAxis());

      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          ms1Result.setId(id);
          return ms1Result;
        } else {
          System.err.format("ERROR: could not retrieve autogenerated key for ms1 scan result\n");
          return null;
        }
      }
    }
  }

  // There are no update queries since this model is being used a compute chache of the results of the
  // ms1 scan results, where the results are immutable given the same parameters.

  // Extra access patterns.
  public static final String GET_BY_PLATE_ID_AND_PLATE_ROW_AND_PLATE_COL_AND_IONMZ_AND_USE_SNR_AND_SCAN_FILE_PATH_AND_METLIN_IONS =
      StringUtils.join(new String[]{
          "SELECT", StringUtils.join(MS1ScanForWellAndMassCharge.getInstance().getAllFields(), ','),
          "from", MS1ScanForWellAndMassCharge.getInstance().getTableName(),
          "where plate_id = ?",
          "  and plate_row = ?",
          "  and plate_column = ?",
          "  and ion_mass_charge = ?",
          "  and use_snr = ?",
          "  and scan_file = ?",
          "  and metlin_ions = ?",
      }, " ");
  public MS1ScanForWellAndMassCharge getByPlateIdPlateRowPlateColIonMzUseSnrScanFile(
      DB db, Plate plate, PlateWell well, Double ionMZ, Boolean useSnr,
      String scanFile, Map<String, Double> metlinIons) throws Exception {

    MS1ScanForWellAndMassCharge result =
        this.getByPlateIdPlateRowPlateColIonMzUseSnrScanFileFromDb(db, plate, well, ionMZ, useSnr, scanFile, metlinIons);

    if (result == null) {
      // couldn't find entry in the cache
      MS1ScanForWellAndMassCharge construct = getMS1(scanFile, metlinIons);
      construct.setPlateCoordinates(plate.getId(), well.getPlateRow(), well.getPlateColumn());
      construct.setIonMZ(ionMZ);
      construct.setScanFilePath(scanFile);
      construct.setUseSnr(useSnr);
      construct.setMelinIons(metlinIons);
      return insert(db, construct);
    } else {
      return result;
    }
  }

  private MS1ScanForWellAndMassCharge getByPlateIdPlateRowPlateColIonMzUseSnrScanFileFromDb(
      DB db, Plate plate, PlateWell well, Double ionMZ, Boolean useSnr,
      String scanFile, Map<String, Double> metlinIons) throws Exception {
    try (PreparedStatement stmt =
             db.getConn().prepareStatement(
                 GET_BY_PLATE_ID_AND_PLATE_ROW_AND_PLATE_COL_AND_IONMZ_AND_USE_SNR_AND_SCAN_FILE_PATH_AND_METLIN_IONS)) {
      stmt.setInt(1, plate.getId());
      stmt.setInt(2, well.getPlateRow());
      stmt.setInt(3, well.getPlateColumn());
      stmt.setDouble(4, ionMZ);
      stmt.setBoolean(5, useSnr);
      stmt.setString(6, scanFile);
      stmt.setBinaryStream(7, serialize(metlinIons));

      try (ResultSet resultSet = stmt.executeQuery()) {
        MS1ScanForWellAndMassCharge result = expectOneResult(resultSet,
            String.format("plate_id = %d, plate_row = %d, plate_column = %d, ion_mz = %s, use_snr = %s, scan_file = %s",
                plate.getId(), well.getPlateRow(), well.getPlateColumn(), ionMZ, useSnr, scanFile));
        return result;
      }
    }
  }

  private MS1ScanForWellAndMassCharge getMS1(String ms1File, Map<String, Double> metlinIons)
      throws Exception {
    MS1 ms1 = new MS1();
    return ms1.getMS1(metlinIons, ms1File);
  }

  public Integer getPlateId() { return plate_id; }

  public Integer getPlateRow() { return plate_row; }

  public Integer getPlateColumn() { return plate_column; }

  public String getScanFilePath() { return lcmsScanFileDir; }

  public Boolean getUseSNR() {
    return useSNR;
  }

  public Double getIonMZ() {
    return ionMZ;
  }

  public Double getMaxYAxis() {
    return maxYAxis;
  }

  public Map<String, Double> getIndividualMaxYAxis() {
    return individualMaxIntensities;
  }

  public Double getMaxIntensityForIon(String ion) {
    return ionsToMax.get(ion);
  }

  public Map<String, Double> getMetlinIons() {
    return metlinIons;
  }

  public Map<String, List<XZ>> getIonsToSpectra() {
    return ionsToSpectra;
  }

  public Map<String, Double> getIonsToIntegral() {
    return ionsToIntegral;
  }

  public Map<String, Double> getIonsToAvgSignal() {
    return ionsToAvgSignal;
  }

  public Map<String, Double> getIonsToMax() {
    return ionsToMax;
  }

  public Map<String, Double> getIonsToLogSNR() {
    return ionsToLogSNR;
  }

  public Map<String, Double> getIonsToAvgAmbient() {
    return ionsToAvgAmbient;
  }

  public Map<String, Double> getIndividualMaxIntensities() {
    return individualMaxIntensities;
  }

  public void setMaxIntensityForIon(String ion, Double max) {
    this.ionsToMax.put(ion, max);
  }

  public Double getLogSNRForIon(String ion) {
    return ionsToLogSNR.get(ion);
  }

  public void setLogSNRForIon(String ion, Double logsnr) {
    this.ionsToLogSNR.put(ion, logsnr);
  }

  public void setMelinIons(Map<String, Double> metlinIons) {
    this.metlinIons = metlinIons;
  }

  public void setAvgIntensityForIon(String ion, Double avgSignal, Double avgAmbient) {
    this.ionsToAvgSignal.put(ion, avgSignal);
    this.ionsToAvgAmbient.put(ion, avgAmbient);
  }

  // This function is set to private since it is only needed by this class to set
  // the plate coordinates.
  private void setPlateCoordinates(Integer plateId, Integer plateRow, Integer plateColumn) {
    this.plate_id = plateId;
    this.plate_row = plateRow;
    this.plate_column = plateColumn;
  }

  public void setIonMZ(Double ionmZ) {
    this.ionMZ = ionmZ;
  }

  public void setUseSnr(Boolean useSNR) {
    this.useSNR = useSNR;
  }

  public void setScanFilePath(String scanFilePath) {
    this.lcmsScanFileDir = scanFilePath;
  }

  public Double getIntegralForIon(String ion) {
    return ionsToIntegral.get(ion);
  }

  public void setIntegralForIon(String ion, Double area) {
    this.ionsToIntegral.put(ion, area);
  }

  public void setMaxYAxis(Double maxYAxis) {
    this.maxYAxis = maxYAxis;
  }

  public void setIndividualMaxIntensities(Map<String, Double> individualMaxIntensities) {
    this.individualMaxIntensities = individualMaxIntensities;
  }
}