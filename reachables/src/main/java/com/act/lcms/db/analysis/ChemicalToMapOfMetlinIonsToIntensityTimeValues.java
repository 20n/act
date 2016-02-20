package com.act.lcms.db.analysis;

import com.act.lcms.MS1;
import com.act.lcms.XZ;
import com.act.lcms.plotter.WriteAndPlotMS1Results;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChemicalToMapOfMetlinIonsToIntensityTimeValues {

  public MS1.IonMode mode;

  private Map<String, Map<String, List<XZ>>> peakData;

  protected ChemicalToMapOfMetlinIonsToIntensityTimeValues() {
    this.peakData = new HashMap<>();
  }

  public Set<String> getIonList() {
    return this.peakData.keySet();
  }

  public Map<String, List<XZ>> getMetlinIonsOfChemical(String chemical) {
    return this.peakData.get(chemical);
  }

  public void setMode(MS1.IonMode mode) {
    this.mode = mode;
  }

  public void addIonIntensityTimeValueToChemical(String chemical, String ion, List<XZ> intensityAndTimeValues) {
    Map<String, List<XZ>> val = this.peakData.get(chemical);
    if (val == null) {
      val = new HashMap<>();
    }
    val.put(ion, intensityAndTimeValues);
    this.peakData.put(chemical, val);
  }

  private Double findMaxIntesity(List<XZ> intensityTimeValues) {
    Double maxIntensity = 0.0d;
    for (XZ val : intensityTimeValues) {
      maxIntensity = Math.max(maxIntensity, val.getIntensity());
    }
    return maxIntensity;
  }

  public Map<String, String> plotPositiveAndNegativeControlsForEachMetlinIon(Pair<String, Double> searchMz,
                                                              String prefix) throws IOException {

    Map<String, String> ionToPlottingFilePath = new HashMap<>();

    Map<String, Double> individualMaxIntensities = new HashMap<>();
    WriteAndPlotMS1Results plottingUtil = new WriteAndPlotMS1Results();

    for (String ion : this.peakData.get(searchMz.getLeft()).keySet()) {
      Map<String, List<XZ>> ms1s = new HashMap<>();
      Double maxIntensity = 0.0d;
      for (String chemical : this.peakData.keySet()) {
        List<XZ> ionValues = this.peakData.get(chemical).get(ion);
        ms1s.put(chemical, ionValues);
        Double localMaxIntensity = findMaxIntesity(ionValues);
        maxIntensity = Math.max(maxIntensity, localMaxIntensity);
        individualMaxIntensities.put(chemical, localMaxIntensity);
      }

      MS1 c = new MS1();
      Map<String, Double> metlinMasses;
      if (this.mode == null) {
        metlinMasses = c.getIonMasses(searchMz.getRight(), MS1.IonMode.POS);
      } else {
        metlinMasses = c.getIonMasses(searchMz.getRight(), this.mode);
      }
      String absolutePath = prefix + "/" + searchMz.getLeft() + "_" + ion;
      plottingUtil.plotSpectra(ms1s, maxIntensity, individualMaxIntensities, metlinMasses, absolutePath, "pdf", false, false);
      ionToPlottingFilePath.put(ion, absolutePath + ".pdf");
    }

    return ionToPlottingFilePath;
  }

}
