/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.act.lcms.db.analysis;

import com.act.lcms.MS1;
import com.act.lcms.db.model.MS1ScanForWellAndMassCharge;
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
  MS1ScanForWellAndMassCharge ms1ScanResults;

  public ScanData(KIND kind, Plate plate, T well, ScanFile scanFile, String targetChemicalName,
                  Map<String, Double> metlinMasses, MS1ScanForWellAndMassCharge ms1ScanResults) {
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

  public MS1ScanForWellAndMassCharge getMs1ScanResults() {
    return ms1ScanResults;
  }

  @Override
  public String toString() {
    return String.format("%s: %s @ %s, file %s",
        kind, plate.getBarcode(), well.getCoordinatesString(), scanFile.getFilename());
  }
}
