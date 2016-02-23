package com.act.lcms.db.model;


import com.act.lcms.XZ;
import com.act.lcms.db.analysis.StandardIonAnalysis;
import com.act.lcms.db.io.DB;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class StandardIonResult extends BaseDBModel<StandardIonResult> implements Serializable {

  public static final String TABLE_NAME = "standard_ion_result";

  protected static final StandardIonResult INSTANCE = new StandardIonResult();

  public static StandardIonResult getInstance() {
    return INSTANCE;
  }

  private enum DB_FIELD implements DBFieldEnumeration {
    ID(1, -1, "id"),
    CHEMICAL(2, 1, "chemical"),
    STANDARD_WELL_ID(3, 2, "standard_well_id"),
    NEGATIVE_WELL_IDS(4, 3, "negative_well_ids"),
    STANDARD_ION_RESULTS(5, 4, "standard_ion_results"),
    PLOTTING_RESULT_PATHS(6, 5, "plotting_result_paths"),
    BEST_METLIN_ION(7, 6, "best_metlin_ion");

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

  protected static final String GET_BY_ID_QUERY = StandardIonResult.getInstance().makeGetByIDQuery();
  @Override
  protected String getGetByIDQuery() {
    return GET_BY_ID_QUERY;
  }

  protected static final String INSERT_QUERY = StandardIonResult.getInstance().makeInsertQuery();
  @Override
  public String getInsertQuery() {
    return INSERT_QUERY;
  }

  protected static final String UPDATE_QUERY = StandardIonResult.getInstance().makeUpdateQuery();
  @Override
  public String getUpdateQuery() {
    return UPDATE_QUERY;
  }

  @Override
  protected List<StandardIonResult> fromResultSet(ResultSet resultSet)
      throws SQLException, IOException, ClassNotFoundException {
    List<StandardIonResult> results = new ArrayList<>();
    while (resultSet.next()) {
      Integer id = resultSet.getInt(DB_FIELD.ID.getOffset());
      String chemical = resultSet.getString(DB_FIELD.CHEMICAL.getOffset());
      Integer standardWellId = resultSet.getInt(DB_FIELD.STANDARD_WELL_ID.getOffset());
      Integer[] negativeWellIds = StandardIonResult.deserializeNegativeWellIds(
          resultSet.getString(DB_FIELD.NEGATIVE_WELL_IDS.getOffset()));
      LinkedHashMap<String, XZ> analysisResults =
          StandardIonResult.deserializeStandardIonAnalysisResult(
              resultSet.getString(DB_FIELD.STANDARD_ION_RESULTS.getOffset()));
      Map<String, String> plottingResultFilePaths =
          StandardIonResult.deserializePlottingPaths(
              resultSet.getString(DB_FIELD.PLOTTING_RESULT_PATHS.getOffset()));
      String bestMetlinIon = resultSet.getString(DB_FIELD.BEST_METLIN_ION.getOffset());

      results.add(
          new StandardIonResult(id, chemical, standardWellId, negativeWellIds, analysisResults,
              plottingResultFilePaths, bestMetlinIon));
    }

    return results;
  }

  protected void bindInsertOrUpdateParameters(
      PreparedStatement stmt,
      String chemical,
      Integer standardWellId,
      Integer[] negativeWellIds,
      LinkedHashMap<String, XZ> analysisResults,
      Map<String, String> plottingResultFileMapping,
      String bestMetlinIon) throws SQLException, IOException {

    stmt.setString(DB_FIELD.CHEMICAL.getInsertUpdateOffset(), chemical);
    stmt.setInt(DB_FIELD.STANDARD_WELL_ID.getInsertUpdateOffset(), standardWellId);
    stmt.setString(DB_FIELD.NEGATIVE_WELL_IDS.getInsertUpdateOffset(), Arrays.toString(negativeWellIds));
    stmt.setString(DB_FIELD.PLOTTING_RESULT_PATHS.getInsertUpdateOffset(), serializePlottingPaths(plottingResultFileMapping));
    stmt.setString(DB_FIELD.STANDARD_ION_RESULTS.getInsertUpdateOffset(),
        serializeStandardIonAnalysisResult(analysisResults));
    stmt.setString(DB_FIELD.BEST_METLIN_ION.getInsertUpdateOffset(), bestMetlinIon);
  }

  @Override
  protected void bindInsertOrUpdateParameters(PreparedStatement stmt, StandardIonResult ionResult)
      throws SQLException, IOException {
    bindInsertOrUpdateParameters(
        stmt, ionResult.getChemical(), ionResult.getStandardWellId(), ionResult.getNegativeWellIds(),
        ionResult.getAnalysisResults(), ionResult.getPlottingResultFilePaths(), ionResult.getBestMetlinIon());
  }

  private static Integer[] deserializeNegativeWellIds(String serializedNegativeIds) {

    // Since the resultant string is like ["12", "14", "1"], we have to remove the braces and concatenate the whitespace.
    String[] negativeIds = serializedNegativeIds.replaceAll(" ", "").replaceAll("\\[", "").replaceAll("\\]", "").split(",");
    Integer[] result = new Integer[negativeIds.length];

    for (int i = 0; i < negativeIds.length; i++) {
      result[i] = Integer.parseInt(negativeIds[i]);
    }

    return result;
  }

  private static LinkedHashMap<String, XZ> deserializeStandardIonAnalysisResult(
      String jsonEntry) throws IOException {

    //Since the resultant string is [{key:value}, {key2:value2}], we have to strip the braces.
    String validJsonEntry = jsonEntry.replaceAll("\\[", "").replaceAll("\\]", "");
    ObjectMapper mapper = new ObjectMapper();
    TypeReference<Map<String, XZ>> typeRef = new TypeReference<Map<String, XZ>>() {};
    TreeMap<Double, String> sortedIntensityToIon = new TreeMap<>(Collections.reverseOrder());

    // We have to re-sorted the deserialized results so that we meet the contract expected by the caller.
    Map<String, XZ> deserializedResult = mapper.readValue(validJsonEntry, typeRef);
    for (Map.Entry<String, XZ> val : deserializedResult.entrySet()) {
      sortedIntensityToIon.put(val.getValue().getIntensity(), val.getKey());
    }

    LinkedHashMap<String, XZ> sortedResult = new LinkedHashMap<>();
    for (Map.Entry<Double, String> val : sortedIntensityToIon.entrySet()) {
      String ion = val.getValue();
      sortedResult.put(ion, deserializedResult.get(ion));
    }

    return sortedResult;
  }

  private static String serializeStandardIonAnalysisResult(
      LinkedHashMap<String, XZ> analysis) throws IOException {
    return new ObjectMapper().writeValueAsString(analysis);
  }

  private static Map<String, String> deserializePlottingPaths(
      String jsonEntry) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, String> typeRef = new HashMap<>();

    Map<String, String> result =
        (Map<String, String>) mapper.readValue(jsonEntry, typeRef.getClass());
    return result;
  }

  private static String serializePlottingPaths(
      Map<String, String> analysis) throws IOException {
    return new ObjectMapper().writeValueAsString(analysis);
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  public void setChemical(String chemical) {
    this.chemical = chemical;
  }

  public void setStandardWellId(Integer standardWellId) {
    this.standardWellId = standardWellId;
  }

  public void setNegativeWellIds(Integer[] negativeWellIds) {
    this.negativeWellIds = negativeWellIds;
  }

  public void setAnalysisResults(LinkedHashMap<String, XZ> result) {
    this.analysisResults = result;
  }

  public void setPlottingResultFilePaths(Map<String, String> plottingResultFilePaths) {
    this.plottingResultFilePaths = plottingResultFilePaths;
  }

  public void setBestMetlinIon(String bestMetlinIon) {
    this.bestMetlinIon = bestMetlinIon;
  }

  private Integer id;
  private String chemical;
  private Integer standardWellId;
  private Integer[] negativeWellIds;
  private String bestMetlinIon;

  @Override
  public Integer getId() {
    return id;
  }

  public String getChemical() {
    return chemical;
  }

  public String getBestMetlinIon() {
    return bestMetlinIon;
  }

  public Integer getStandardWellId() {
    return standardWellId;
  }

  public Integer[] getNegativeWellIds() {
    return negativeWellIds;
  }

  public LinkedHashMap<String, XZ> getAnalysisResults() {
    return analysisResults;
  }

  public Map<String, String> getPlottingResultFilePaths() {
    return plottingResultFilePaths;
  }

  private LinkedHashMap<String, XZ> analysisResults;
  private Map<String, String> plottingResultFilePaths;

  public StandardIonResult() {}

  public StandardIonResult(Integer id,
                           String chemical,
                           Integer standardWellId,
                           Integer[] negativeWellIds,
                           LinkedHashMap<String, XZ> analysisResults,
                           Map<String, String> plottingResultFilePaths,
                           String bestMelinIon) {
    this.id = id;
    this.chemical = chemical;
    this.standardWellId = standardWellId;
    this.negativeWellIds = negativeWellIds;
    this.plottingResultFilePaths = plottingResultFilePaths;
    this.analysisResults = analysisResults;
    this.bestMetlinIon = bestMelinIon;
  };

  public StandardIonResult insert(DB db, StandardIonResult ionResult) throws SQLException, IOException {
    Connection conn = db.getConn();
    try (PreparedStatement stmt = conn.prepareStatement(StandardIonResult.getInstance().getInsertQuery(),
        Statement.RETURN_GENERATED_KEYS)) {

      bindInsertOrUpdateParameters(
          stmt,
          ionResult.getChemical(),
          ionResult.getStandardWellId(),
          ionResult.getNegativeWellIds(),
          ionResult.getAnalysisResults(),
          ionResult.getPlottingResultFilePaths(),
          ionResult.getBestMetlinIon());

      stmt.executeUpdate();
      try (ResultSet resultSet = stmt.getGeneratedKeys()) {
        if (resultSet.next()) {
          // Get auto-generated id.
          int id = resultSet.getInt(1);
          ionResult.setId(id);
          return ionResult;
        } else {
          System.err.format("ERROR: could not retrieve autogenerated key for ms1 scan result\n");
          return null;
        }
      }
    }
  }

  public StandardIonResult getByChemicalAndStandardWellAndNegativeWells(File lcmsDir,
                                                                        DB db,
                                                                        String chemical,
                                                                        StandardWell standardWell,
                                                                        List<StandardWell> negativeWells,
                                                                        String plottingDirectory)
      throws Exception {
    Integer[] negativeWellIds = new Integer[negativeWells.size()];
    for (int i = 0; i < negativeWells.size(); i++) {
      negativeWellIds[i] = negativeWells.get(i).getId();
    }

    StandardIonResult cachedResult = this.getByChemicalAndStandardWellAndNegativeWells(
        db, chemical, standardWell.getId(), negativeWellIds);

    if (cachedResult == null) {
      StandardIonResult computedResult =
          StandardIonAnalysis.getSnrResultsForStandardWellComparedToValidNegativesAndPlotDiagnostics(
              lcmsDir, db, standardWell, negativeWells, new HashMap<>(), chemical, plottingDirectory);
      computedResult.setNegativeWellIds(negativeWellIds);
      return insert(db, computedResult);
    } else {
      return cachedResult;
    }
  }

  // Extra access patterns.
  public static final String GET_BY_CHEMICAL_AND_STANDARD_WELL_AND_NEGATIVE_WELLS =
      StringUtils.join(new String[]{
          "SELECT", StringUtils.join(StandardIonResult.getInstance().getAllFields(), ','),
          "from", StandardIonResult.getInstance().getTableName(),
          "where chemical = ?",
          "  and standard_well_id = ?",
          "  and negative_well_ids = ?",
      }, " ");

  private StandardIonResult getByChemicalAndStandardWellAndNegativeWells(DB db, String chemical, Integer standardWellId,
                                                                   Integer[] negativeWellIds) throws Exception {
    try (PreparedStatement stmt = db.getConn().prepareStatement(GET_BY_CHEMICAL_AND_STANDARD_WELL_AND_NEGATIVE_WELLS)) {
      stmt.setString(1, chemical);
      stmt.setInt(2, standardWellId);
      stmt.setString(3, Arrays.toString(negativeWellIds));

      try (ResultSet resultSet = stmt.executeQuery()) {
        StandardIonResult result = expectOneResult(resultSet,
            String.format("chemical = %s, standard_well_id = %d, negative_well_ids = %s",
                chemical, standardWellId, Arrays.toString(negativeWellIds)));
        return result;
      }
    }
  }
}
