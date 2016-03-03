package com.act.lcms.db.model;

import com.act.lcms.MS1;
import com.act.lcms.XZ;
import com.act.lcms.db.io.DB;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
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

  public static final String TABLE_NAME = "ms1_for_well_and_mass_charge" ;

  protected static final MS1ScanForWellAndMassCharge INSTANCE = new MS1ScanForWellAndMassCharge();

  public static MS1ScanForWellAndMassCharge getInstance() {
    return INSTANCE;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    PLATE_ID(2, 1, "plate_id"),
    PLATE_ROW(3, 2, "plate_row"),
    PLATE_COLUMN(4, 3, "plate_column"),
    USE_SNR(5, 4, "use_snr"),
    SCAN_FILE(6, 5, "scan_file"),
    CHEMICAL_NAME(7, 6, "chemical_name"),
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
      Double maxYAxis = resultSet.getDouble(DB_FIELD.MAX_Y_AXIS.getOffset());
      Boolean useSNR = resultSet.getBoolean(DB_FIELD.USE_SNR.getOffset());
      String lcmsScanFilePath = resultSet.getString(DB_FIELD.SCAN_FILE.getOffset());
      String chemicalName = resultSet.getString(DB_FIELD.CHEMICAL_NAME.getOffset());
      List<String> metlinIons = MS1ScanForWellAndMassCharge.deserializeMetlinIons(
          resultSet.getString(DB_FIELD.METLIN_IONS.getOffset()));
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

      results.add(new MS1ScanForWellAndMassCharge(id, plateId, plateColumn, plateRow, useSNR, lcmsScanFilePath, chemicalName,
          metlinIons, ionsToSpectra, ionsToIntegral, ionsToMax, ionsToLogSNR, ionsToAvgSignal, ionsToAvgAmbient,
          individualMaxIntensities, maxYAxis));
    }

    return results;
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt, Integer plateId, Integer plateRow, Integer plateColumn,
      Boolean useSNR, String lcmsScanFileDir, String chemicalName, List<String> metlinIons, Map<String,List<XZ>> ionsToSpectra,
      Map<String, Double> ionsToIntegral, Map<String, Double> ionsToMax, Map<String, Double> ionsToLogSNR,
      Map<String, Double> ionsToAvgSignal, Map<String, Double> ionsToAvgAmbient,
      Map<String, Double> individualMaxIntensities, Double maxYAxis) throws SQLException, IOException {
    stmt.setInt(DB_FIELD.PLATE_ID.getInsertUpdateOffset(), plateId);
    stmt.setInt(DB_FIELD.PLATE_ROW.getInsertUpdateOffset(), plateRow);
    stmt.setInt(DB_FIELD.PLATE_COLUMN.getInsertUpdateOffset(), plateColumn);
    stmt.setBoolean(DB_FIELD.USE_SNR.getInsertUpdateOffset(), useSNR);
    stmt.setString(DB_FIELD.SCAN_FILE.getInsertUpdateOffset(), lcmsScanFileDir);
    stmt.setString(DB_FIELD.CHEMICAL_NAME.getInsertUpdateOffset(), chemicalName);
    stmt.setString(DB_FIELD.METLIN_IONS.getInsertUpdateOffset(), OBJECT_MAPPER.writeValueAsString(metlinIons));
    stmt.setBytes(DB_FIELD.IONS_TO_SPECTRA.getInsertUpdateOffset(), serialize(ionsToSpectra));
    stmt.setBytes(DB_FIELD.IONS_TO_INTEGRAL.getInsertUpdateOffset(), serialize(ionsToIntegral));
    stmt.setBytes(DB_FIELD.IONS_TO_LOG_SNR.getInsertUpdateOffset(), serialize(ionsToLogSNR));
    stmt.setBytes(DB_FIELD.IONS_TO_AVG_AMBIENT.getInsertUpdateOffset(), serialize(ionsToAvgAmbient));
    stmt.setBytes(DB_FIELD.IONS_TO_AVG_SIGNAL.getInsertUpdateOffset(), serialize(ionsToAvgSignal));
    stmt.setBytes(DB_FIELD.INDIVIDUAL_MAX_INTENSITIES.getInsertUpdateOffset(), serialize(individualMaxIntensities));
    stmt.setBytes(DB_FIELD.IONS_TO_MAX.getInsertUpdateOffset(), serialize(ionsToMax));
    stmt.setDouble(DB_FIELD.MAX_Y_AXIS.getInsertUpdateOffset(), maxYAxis);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, MS1ScanForWellAndMassCharge ms1Result)
      throws SQLException, IOException {
    bindInsertOrUpdateParameters(
        stmt, ms1Result.getPlateId(), ms1Result.getPlateRow(), ms1Result.getPlateColumn(), ms1Result.getUseSNR(),
        ms1Result.getScanFilePath(), ms1Result.getChemicalName(), ms1Result.getMetlinIons(), ms1Result.getIonsToSpectra(),
        ms1Result.getIonsToIntegral(), ms1Result.getIonsToMax(), ms1Result.getIonsToLogSNR(), ms1Result.getIonsToAvgSignal(),
        ms1Result.getIonsToAvgAmbient(), ms1Result.getIndividualMaxIntensities(), ms1Result.getMaxYAxis());
  }

  private Integer id;
  private Integer plateId;
  private Integer plateRow;
  private Integer plateColumn;
  private Boolean useSNR;
  private String lcmsScanFileDir;
  private String chemicalName;
  private List<String> metlinIons = new ArrayList<>();
  private Map<String, List<XZ>> ionsToSpectra = new HashMap<>();
  private Map<String, Double> ionsToIntegral = new HashMap<>();
  private Map<String, Double> ionsToMax = new HashMap<>();
  private Map<String, Double> ionsToLogSNR = new HashMap<>();
  private Map<String, Double> ionsToAvgSignal = new HashMap<>();
  private Map<String, Double> ionsToAvgAmbient = new HashMap<>();
  private Map<String, Double> individualMaxIntensities = new HashMap<>();
  private Double maxYAxis = 0.0d; // default to 0

  public MS1ScanForWellAndMassCharge() {}

  public MS1ScanForWellAndMassCharge(Integer id, Integer plateId, Integer plateColumn, Integer plateRow,
                                     Boolean useSNR, String lcmsScanFileDir, String chemicalName,
                                     List<String> metlinIons, Map<String, List<XZ>> ionsToSpectra,
                                     Map<String, Double> ionsToIntegral, Map<String, Double> ionsToMax,
                                     Map<String, Double> ionsToAvgSignal, Map<String, Double> ionsToAvgAmbient,
                                     Map<String, Double> ionsToLogSNR, Map<String, Double> individualMaxIntensities,
                                     Double maxYAxis) {
    this.id = id;
    this.useSNR = useSNR;
    this.lcmsScanFileDir = lcmsScanFileDir;
    this.chemicalName = chemicalName;
    this.ionsToSpectra = ionsToSpectra;
    this.ionsToAvgAmbient = ionsToAvgAmbient;
    this.ionsToAvgSignal = ionsToAvgSignal;
    this.ionsToIntegral = ionsToIntegral;
    this.individualMaxIntensities = individualMaxIntensities;
    this.maxYAxis = maxYAxis;
    this.ionsToMax = ionsToMax;
    this.plateRow = plateColumn;
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.ionsToLogSNR = ionsToLogSNR;
    this.metlinIons = metlinIons;
  }

  /**
   * Serialize an object to an array of Serialized, gzip'd bytes.
   *
   * Note that this returns a byte stream (a) to be symmetrical with deserialize, and (b) because we anticipate
   * manifesting the entire byte array at some point so there's no advantage to streaming the results.  If that changes
   * and performance suffers from allocating the entire byte array, we can use byte streams instead (and we'll probably
   * have bigger performance problems to deal with anyway).
   *
   * @param object The object to serialize
   * @param <T> The type of the object (unbound to allow serialization of Maps, which sadly don't explicitly implement
   *            Serializable).
   * @return A byte array representing a compressed object stream for the specified object.
   * @throws IOException
   */
  private static <T> byte[] serialize(T object) throws IOException {
    ByteArrayOutputStream postGzipOutputStream = new ByteArrayOutputStream();

    try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(postGzipOutputStream))) {
      out.writeObject(object);
    }

    return postGzipOutputStream.toByteArray();
  }

  private static <T> T deserialize(byte[] object) throws IOException, ClassNotFoundException {
    T map = null;

    try (ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(object)))) {
      // TODO: consider checking this cast?  Though we'd just throw an exception anyway, so...
      map = (T) ois.readObject();
    }

    return map;
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> typeRefForMetlinIons = new TypeReference<List<String>>() {};

  private static List<String> deserializeMetlinIons(String serializedMetlinIons) throws IOException {
    return OBJECT_MAPPER.readValue(serializedMetlinIons, typeRefForMetlinIons);
  }

  public MS1ScanForWellAndMassCharge insert(
      DB db, MS1ScanForWellAndMassCharge ms1Result)
      throws SQLException, IOException {

    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(MS1ScanForWellAndMassCharge.getInstance().getInsertQuery(),
        Statement.RETURN_GENERATED_KEYS)) {

      bindInsertOrUpdateParameters(stmt, ms1Result.getPlateId(), ms1Result.getPlateRow(),
          ms1Result.getPlateColumn(), ms1Result.getUseSNR(), ms1Result.getScanFilePath(), ms1Result.getChemicalName(),
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
  public static final String GET_BY_PLATE_ID_AND_PLATE_ROW_AND_PLATE_COL_AND_USE_SNR_AND_SCAN_FILE_PATH_AND_CHEMICAL_AND_METLIN_IONS =
      StringUtils.join(new String[]{
          "SELECT", StringUtils.join(MS1ScanForWellAndMassCharge.getInstance().getAllFields(), ','),
          "from", MS1ScanForWellAndMassCharge.getInstance().getTableName(),
          "where plate_id = ?",
          "  and plate_row = ?",
          "  and plate_column = ?",
          "  and use_snr = ?",
          "  and scan_file = ?",
          "  and chemical_name = ?",
          "  and metlin_ions = ?",
      }, " ");
  public MS1ScanForWellAndMassCharge getByPlateIdPlateRowPlateColUseSnrScanFileChemical(
      DB db, Plate plate, PlateWell well, Boolean useSnr, ScanFile scanFile, String chemicalName,
      Map<String, Double> metlinIons, File lcmsFile) throws Exception {

    // Pre-process the list of metlin ions
    List<String> ions = new ArrayList<>();
    ions.addAll(metlinIons.keySet());
    Collections.sort(ions);

    MS1ScanForWellAndMassCharge result =
        this.getByPlateIdPlateRowPlateColUseSnrScanFileChemicalMetlinIonsFromDb(
            db, plate, well, useSnr, scanFile.getFilename(), chemicalName, ions);

    if (result == null) {
      // couldn't find entry in the cache
      MS1ScanForWellAndMassCharge construct = getMS1(lcmsFile.getAbsolutePath(), metlinIons);
      construct.setPlateCoordinates(plate.getId(), well.getPlateRow(), well.getPlateColumn());
      construct.setScanFilePath(scanFile.getFilename());
      construct.setUseSnr(useSnr);
      construct.setMetlinIons(ions);
      construct.setChemicalName(chemicalName);
      return insert(db, construct);
    } else {
      return result;
    }
  }

  private MS1ScanForWellAndMassCharge getByPlateIdPlateRowPlateColUseSnrScanFileChemicalMetlinIonsFromDb(
      DB db, Plate plate, PlateWell well, Boolean useSnr,
      String scanFile, String chemicalName, List<String> metlinIons) throws Exception {
    try (PreparedStatement stmt =
             db.getConn().prepareStatement(
                 GET_BY_PLATE_ID_AND_PLATE_ROW_AND_PLATE_COL_AND_USE_SNR_AND_SCAN_FILE_PATH_AND_CHEMICAL_AND_METLIN_IONS)) {
      stmt.setInt(1, plate.getId());
      stmt.setInt(2, well.getPlateRow());
      stmt.setInt(3, well.getPlateColumn());
      stmt.setBoolean(4, useSnr);
      stmt.setString(5, scanFile);
      stmt.setString(6, chemicalName);
      stmt.setString(7, OBJECT_MAPPER.writeValueAsString(metlinIons));

      try (ResultSet resultSet = stmt.executeQuery()) {
        MS1ScanForWellAndMassCharge result = expectOneResult(resultSet,
            String.format("plate_id = %d, plate_row = %d, plate_column = %d, use_snr = %s, scan_file = %s",
                plate.getId(), well.getPlateRow(), well.getPlateColumn(), useSnr, scanFile));
        return result;
      }
    }
  }

  private MS1ScanForWellAndMassCharge getMS1(String ms1File, Map<String, Double> metlinIons)
      throws Exception {
    MS1 ms1 = new MS1();
    return ms1.getMS1(metlinIons, ms1File);
  }

  public Integer getPlateId() { return plateId; }

  public Integer getPlateRow() { return plateRow; }

  public Integer getPlateColumn() { return plateColumn; }

  public String getScanFilePath() { return lcmsScanFileDir; }

  public Boolean getUseSNR() {
    return useSNR;
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

  public List<String> getMetlinIons() {
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

  public String getChemicalName() {
    return chemicalName;
  }

  public void setLogSNRForIon(String ion, Double logsnr) {
    this.ionsToLogSNR.put(ion, logsnr);
  }

  public void setMetlinIons(List<String> metlinIons) {
    this.metlinIons = metlinIons;
  }

  public void setAvgIntensityForIon(String ion, Double avgSignal, Double avgAmbient) {
    this.ionsToAvgSignal.put(ion, avgSignal);
    this.ionsToAvgAmbient.put(ion, avgAmbient);
  }

  // This function is set to private since it is only needed by this class to set
  // the plate coordinates.
  private void setPlateCoordinates(Integer plateId, Integer plateRow, Integer plateColumn) {
    this.plateId = plateId;
    this.plateRow = plateRow;
    this.plateColumn = plateColumn;
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

  public void setChemicalName(String name) { this.chemicalName = name; }
}