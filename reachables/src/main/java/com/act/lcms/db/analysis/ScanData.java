package com.act.lcms.db.analysis;

import com.act.lcms.MS1;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.ScanFile;

import java.util.Map;

public class ScanData<T extends PlateWell<T>> {
  public enum KIND {
    STANDARD,
    POS_SAMPLE,
    NEG_CONTROL,
    BLANK,
  }

  KIND kind;
  Plate plate;
  T well;
  ScanFile scanFile;
  String targetChemicalName;
  Map<String, Double> metlinMasses;
  MS1.MS1ScanResults ms1ScanResults;

  public ScanData(KIND kind, Plate plate, T well, ScanFile scanFile, String targetChemicalName,
                  Map<String, Double> metlinMasses, MS1.MS1ScanResults ms1ScanResults) {
    this.kind = kind;
    this.plate = plate;
    this.well = well;
    this.scanFile = scanFile;
    this.targetChemicalName = targetChemicalName;
    this.metlinMasses = metlinMasses;
    this.ms1ScanResults = ms1ScanResults;
  }

  public KIND getKind() {
    return kind;
  }

  public Plate getPlate() {
    return plate;
  }

  public T getWell() {
    return well;
  }

  public ScanFile getScanFile() {
    return scanFile;
  }

  public String getTargetChemicalName() {
    return targetChemicalName;
  }

  public Map<String, Double> getMetlinMasses() {
    return metlinMasses;
  }

  public MS1.MS1ScanResults getMs1ScanResults() {
    return ms1ScanResults;
  }

  @Override
  public String toString() {
    return String.format("%s: %s @ %s, file %s",
        kind, plate.getBarcode(), well.getCoordinatesString(), scanFile.getFilename());
  }
}
