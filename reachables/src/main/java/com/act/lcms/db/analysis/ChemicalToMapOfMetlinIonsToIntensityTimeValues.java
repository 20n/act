package com.act.lcms.db.analysis;

import com.act.lcms.XZ;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.StandardWell;
import com.act.lcms.plotter.WriteAndPlotMS1Results;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChemicalToMapOfMetlinIonsToIntensityTimeValues {
  private static String fmt = "pdf";

  private Map<String, Map<String, List<XZ>>> peakData;

  public Map<String, Map<String, List<XZ>>> getPeakData() {
    return peakData;
  }

  protected ChemicalToMapOfMetlinIonsToIntensityTimeValues() {
    this.peakData = new HashMap<>();
  }

  public Set<String> getIonList() {
    return this.peakData.keySet();
  }

  public Map<String, List<XZ>> getMetlinIonsOfChemical(String chemical) {
    return this.peakData.get(chemical);
  }

  public void addIonIntensityTimeValueToChemical(String chemical, String ion, List<XZ> intensityAndTimeValues) {
    Map<String, List<XZ>> val = this.peakData.get(chemical);
    if (val == null) {
      val = new HashMap<>();
    }
    val.put(ion, intensityAndTimeValues);
    this.peakData.put(chemical, val);
  }

  private static Double findMaxIntensity(List<XZ> intensityTimeValues) {
    Double maxIntensity = 0.0d;
    for (XZ val : intensityTimeValues) {
      maxIntensity = Math.max(maxIntensity, val.getIntensity());
    }
    return maxIntensity;
  }

  /**
   * This function plots the positive ion and negative control ions for a given metlin ion mass per plot.
   * @param searchMz - The mz value which is used for finding spectra.
   * @param plottingDirectory - The directory where the plots will live.
   * @param positiveChemical - The positive chemical is used to make sure it is placed at the top of the spectra plot.
   * @return This function returns a map of ion to absolute paths where the plot lives.
   * @throws IOException
   */
  public Map<String, String> plotPositiveAndNegativeControlsForEachMetlinIon(
      Pair<String, Double> searchMz,
      String plottingDirectory,
      String positiveChemical,
      List<StandardWell> standardWells)
      throws IOException {
    Map<String, String> ionToPlottingFilePath = new HashMap<>();
    Map<String, Double> individualMaxIntensities = new HashMap<>();
    WriteAndPlotMS1Results plottingUtil = new WriteAndPlotMS1Results();

    //rearrange the order of plotting
    ArrayList<String> orderedPlotChemicalTitles = new ArrayList<>(this.peakData.keySet().size());
    for (String chemical : peakData.keySet()) {
      if (chemical.equals(positiveChemical)) {
        orderedPlotChemicalTitles.add(0, chemical);
      } else {
        orderedPlotChemicalTitles.add(chemical);
      }
    }

    // This variable is used as a part of the file path dir to uniquely identify the pos/neg wells for the chemical.
    StringBuilder indexedPath = new StringBuilder();
    for (StandardWell well : standardWells) {
      indexedPath.append(Integer.toString(well.getId()) + "-");
    }

    for (String ion : this.peakData.get(searchMz.getLeft()).keySet()) {
      LinkedHashMap<String, List<XZ>> ms1s = new LinkedHashMap<>();
      Map<String, Double> metlinMasses = new HashMap<>();
      Double maxIntensity = 0.0d;

      for (String chemical : orderedPlotChemicalTitles) {
        List<XZ> ionValues = this.peakData.get(chemical).get(ion);
        ms1s.put(chemical, ionValues);
        Double localMaxIntensity = this.findMaxIntensity(ionValues);
        maxIntensity = Math.max(maxIntensity, localMaxIntensity);
        individualMaxIntensities.put(chemical, localMaxIntensity);
        metlinMasses.put(chemical, searchMz.getValue());
      }

      String relativePath = searchMz.getLeft() + "_" + indexedPath.toString() + "_" + ion;

      File absolutePathFileWithoutExtension = new File(plottingDirectory, relativePath);
      String absolutePathWithoutExtension = absolutePathFileWithoutExtension.getAbsolutePath();

      plottingUtil.plotSpectra(
          ms1s, maxIntensity, individualMaxIntensities, metlinMasses, absolutePathWithoutExtension, this.fmt, false, false);
      ionToPlottingFilePath.put(ion, relativePath + "." + this.fmt);
    }

    return ionToPlottingFilePath;
  }

  public static <T extends PlateWell<T>> Map<String, String> plotPositiveAndNegativeControlsForEachMetlinIon3(
      Pair<String, Double> searchMz, List<T> wells, Map<String, Map<String, List<XZ>>> peakDataPos, List<Map<String, Map<String, List<XZ>>>> peakDataNegs,
      String plottingDirectory, String positiveChemical) throws IOException {

    List<Map<String, Map<String, List<XZ>>>> both = new ArrayList<>();
    both.add(peakDataPos);
    both.addAll(peakDataNegs);

    Map<String, String> ionToPlottingFilePath = new HashMap<>();
    Map<String, Double> individualMaxIntensities = new HashMap<>();
    WriteAndPlotMS1Results plottingUtil = new WriteAndPlotMS1Results();

    // This variable is used as a part of the file path dir to uniquely identify the pos/neg wells for the chemical.
    StringBuilder indexedPath = new StringBuilder();
    for (T well : wells) {
      indexedPath.append(Integer.toString(well.getId()) + "-");
    }

    LinkedHashMap<String, List<XZ>> ms1s = new LinkedHashMap<>();
    Map<String, Double> metlinMasses = new HashMap<>();
    Double maxIntensity = 0.0d;

    Integer counter = 0;
    for (Map<String, Map<String, List<XZ>>> peakData : both) {
      String name = "";
      if (counter == 0) {
        name = "_pos";
      } else {
        name = "_neg" + counter.toString();
      }

      List<XZ> ionValues = peakData.get(positiveChemical).get("M+H");
      ms1s.put(positiveChemical + name, ionValues);
      Double localMaxIntensity = findMaxIntensity(ionValues);
      maxIntensity = Math.max(maxIntensity, localMaxIntensity);
      individualMaxIntensities.put(positiveChemical + name, localMaxIntensity);
      metlinMasses.put(positiveChemical + name, searchMz.getValue());

      counter++;
    }

    String relativePath = positiveChemical + "_" + indexedPath.toString() + "_" + "M+H";

    File absolutePathFileWithoutExtension = new File(plottingDirectory, relativePath);
    String absolutePathWithoutExtension = absolutePathFileWithoutExtension.getAbsolutePath();

    plottingUtil.plotSpectra(
        ms1s, maxIntensity, individualMaxIntensities, metlinMasses, absolutePathWithoutExtension, "pdf", false, false);
    ionToPlottingFilePath.put("M+H", relativePath + "." + "pdf");

    return ionToPlottingFilePath;
  }

  public <T extends PlateWell<T>> Map<String, String> plotPositiveAndNegativeControlsForEachMetlinIon2(
      Pair<String, Double> searchMz,
      String plottingDirectory,
      String positiveChemical,
      List<T> wells, Map<String, Map<String, List<XZ>>> peakDataPos, Map<String, Map<String, List<XZ>>> peakDataNeg
      )
      throws IOException {
    Map<String, String> ionToPlottingFilePath = new HashMap<>();
    Map<String, Double> individualMaxIntensities = new HashMap<>();
    WriteAndPlotMS1Results plottingUtil = new WriteAndPlotMS1Results();

    //rearrange the order of plotting
    ArrayList<String> orderedPlotChemicalTitles = new ArrayList<>(this.peakData.keySet().size());
    for (String chemical : peakData.keySet()) {
      if (chemical.equals(positiveChemical)) {
        orderedPlotChemicalTitles.add(0, chemical);
      } else {
        orderedPlotChemicalTitles.add(chemical);
      }
    }

    // This variable is used as a part of the file path dir to uniquely identify the pos/neg wells for the chemical.
    StringBuilder indexedPath = new StringBuilder();
    for (T well : wells) {
      indexedPath.append(Integer.toString(well.getId()) + "-");
    }

    LinkedHashMap<String, List<XZ>> ms1s = new LinkedHashMap<>();
    Map<String, Double> metlinMasses = new HashMap<>();
    Double maxIntensity = 0.0d;

    for (String chemical : orderedPlotChemicalTitles) {
      List<XZ> ionValues = this.peakData.get(chemical).get("M+H");
      ms1s.put(chemical, ionValues);
      Double localMaxIntensity = this.findMaxIntensity(ionValues);
      maxIntensity = Math.max(maxIntensity, localMaxIntensity);
      individualMaxIntensities.put(chemical, localMaxIntensity);
      metlinMasses.put(chemical, searchMz.getValue());
    }

    String relativePath = positiveChemical + "_" + indexedPath.toString() + "_" + "M+H";

    File absolutePathFileWithoutExtension = new File(plottingDirectory, relativePath);
    String absolutePathWithoutExtension = absolutePathFileWithoutExtension.getAbsolutePath();

    plottingUtil.plotSpectra(
        ms1s, maxIntensity, individualMaxIntensities, metlinMasses, absolutePathWithoutExtension, this.fmt, false, false);
    ionToPlottingFilePath.put("M+H", relativePath + "." + this.fmt);

    return ionToPlottingFilePath;
  }

}
