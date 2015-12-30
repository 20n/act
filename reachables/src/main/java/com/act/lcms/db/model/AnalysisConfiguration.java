package com.act.lcms.db.model;

import com.act.lcms.MS1;
import com.act.lcms.db.analysis.IonSelector;
import com.act.lcms.db.analysis.Utils;
import com.act.lcms.db.io.DB;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalysisConfiguration {
  // TODO: replace this with a real class and maybe wire it up using dependency injection or similar.
  public static final IonSelector ION_SELECTOR = new IonSelector() {
    @Override
    public Pair<String, Double> findBestIonForChemical(DB db, String chemicalName) throws SQLException, IOException {
      Map<String, Double> ionMasses = computeIonMassesForChemical(db, chemicalName);
      // Fake automatic ion selection by always returning M+H (for now).
      return Pair.of("M+H", ionMasses.get("M+H"));
    }
  };

  public static Map<String, Double> computeIonMassesForChemical(DB db, String chemicalName)
      throws SQLException, IOException {
    Pair<String, Double> nameMassPair = Utils.extractMassFromString(db, chemicalName);
    return new MS1().getIonMasses(nameMassPair.getRight(), MS1.IonMode.POS);
  }

  @JsonProperty("title")
  private String title;
  @JsonProperty("use_fine_grained_mz")
  private boolean useFineGrainedMZ;

  @JsonProperty("components")
  private List<Component> components;

  public static class Component {
    public enum KIND {
      SEPARATOR,
      STANDARD,
      SAMPLE_POS,
      SAMPLE_NEG,
    }

    @JsonProperty("kind")
    KIND kind;

    @JsonIgnore
    StandardWell standardWell;
    @JsonIgnore
    LCMSWell lcmsWell;

    @JsonProperty("label")
    String label;
    @JsonProperty("analysis_chemical")
    String analysisChemical;
    @JsonProperty("analysis_ion")
    String analysisIon;
    @JsonProperty("analysis_mz")
    Double analysisMZ;
    @JsonProperty("intensity_range_group")
    Integer intensityRangeGroup; // For normalizing the Y-axes of groups of scans.

    // Note: we use strings rather than ScanFile objects here to avoid de/serialization having to interact with the DB.
    @JsonProperty("preferred_scan_file_name")
    String preferredScanFileName;
    @JsonProperty("available_scan_file_names")
    List<String> availableScanFileNames;

    @JsonProperty("plate_barcode")
    String plateBarcode;
    @JsonProperty("plate_coordinates")
    String plateCoordinates;
    @JsonProperty("construct")
    String construct;
    @JsonProperty("msid")
    String msid;

    // TODO: add a builder for this class.

    public KIND getKind() {
      return kind;
    }

    public StandardWell getStandardWell() {
      // Treat this class like a tagged union.
      if (this.kind != KIND.STANDARD) {
        return null;
      }
      return standardWell;
    }

    public LCMSWell getLcmsWell() {
      if (!(this.kind == KIND.SAMPLE_POS || this.kind == KIND.SAMPLE_NEG)) {
        return null;
      }
      return lcmsWell;
    }

    public String getLabel() {
      return label;
    }

    public String getAnalysisChemical() {
      return analysisChemical;
    }

    public String getAnalysisIon() {
      return analysisIon;
    }

    public Double getAnalysisMZ() {
      return analysisMZ;
    }

    public Integer getIntensityRangeGroup() {
      return intensityRangeGroup;
    }

    public String getPreferredScanFileName() {
      return preferredScanFileName;
    }

    public List<String> getAvailableScanFileNames() {
      return availableScanFileNames;
    }

    public String getPlateBarcode() {
      return plateBarcode;
    }

    public String getPlateCoordinates() {
      return plateCoordinates;
    }

    public String getConstruct() {
      return construct;
    }

    public String getMsid() {
      return msid;
    }

    public static Component fromLCMSWell(DB db, LCMSWell well, boolean negControl, Plate plate, String label,
                                         String analysisChemical, String analysisIon, Integer intensityRangeGroup)
        throws SQLException {
      if (plate == null) {
        plate = Plate.getPlateById(db, well.getPlateId());
      }

      Component component = new Component();
      component.kind = negControl ? KIND.SAMPLE_NEG : KIND.SAMPLE_POS;
      component.lcmsWell = well;

      component.analysisChemical = analysisChemical;
      component.analysisIon = analysisIon;
      Pair<String, Double> labelMass = Utils.extractMassFromString(db, analysisChemical);
      component.analysisMZ = labelMass.getRight();

      component.intensityRangeGroup = intensityRangeGroup;

      component.plateBarcode = plate.getBarcode();
      component.plateCoordinates = well.getCoordinatesString();

      component.construct = well.getComposition();
      component.msid = well.getMsid();

      if (label != null) {
        component.label = label;
      } else {
        component.label = String.format("%s (%s fed %s) @ %s %s",
            well.getComposition(), well.getMsid(),
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            component.getPlateBarcode(),
            component.getPlateCoordinates()
        );
      }

      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, plate.getId(), well.getPlateRow(), well.getPlateColumn());
      // Just take the first from the DB for now.  TODO: do better.
      component.preferredScanFileName = scanFiles.get(0).getFilename();
      List<String> filenames = new ArrayList<>(scanFiles.size());
      for (ScanFile file : scanFiles) {
        filenames.add(file.getFilename());
      }
      component.availableScanFileNames = filenames;

      return component;
    }

    public static Component fromStandardWell(DB db, StandardWell well, Plate plate, String label,
                                             String analysisChemical, String analysisIon, Integer intensityRangeGroup)
      throws SQLException {
      if (plate == null) {
        plate = Plate.getPlateById(db, well.getPlateId());
      }

      Component component = new Component();
      component.kind = KIND.STANDARD;
      component.standardWell = well;

      component.analysisChemical = analysisChemical;
      component.analysisIon = analysisIon;
      Pair<String, Double> labelMass = Utils.extractMassFromString(db, analysisChemical);
      component.analysisMZ = labelMass.getRight();

      component.intensityRangeGroup = intensityRangeGroup;

      component.plateBarcode = plate.getBarcode();
      component.plateCoordinates = well.getCoordinatesString();

      component.construct = null;
      component.msid = null;

      if (label != null) {
        component.label = label;
      } else {
        component.label  = String.format("Standard %s @ %s %s",
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            component.getPlateBarcode(),
            component.getPlateCoordinates()
        );
      }

      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, plate.getId(), well.getPlateRow(), well.getPlateColumn());
      // Just take the first from the DB for now.  TODO: do better.
      component.preferredScanFileName = scanFiles.get(0).getFilename();
      List<String> filenames = new ArrayList<>(scanFiles.size());
      for (ScanFile file : scanFiles) {
        filenames.add(file.getFilename());
      }
      component.availableScanFileNames = filenames;

      return component;
    }

    public static Component fromSeparator(String label) {
      Component component = new Component();
      component.kind = KIND.SEPARATOR;
      component.label = label;
      return component;
    }
  }

}
