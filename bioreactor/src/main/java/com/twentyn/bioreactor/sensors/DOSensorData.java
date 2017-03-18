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

package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.util.Arrays;

public class DOSensorData extends SensorData {

  private static final Logger LOGGER = LogManager.getFormatterLogger(DOSensorData.class);
  private static final Integer NOMINAL_READ_DELAY = 1000;

  @JsonProperty("dissolved_oxygen")
  private Double dissolvedOxygen;

  @JsonProperty("saturation_percentage")
  private Double saturationPercentage;

  public DOSensorData() {
    super.deviceType = "DO";
    super.readQueryTimeDelay = NOMINAL_READ_DELAY + ADD_READ_DELAY;
  }

  public DOSensorData(Double dissolvedOxygen, Double saturationPercentage, String deviceName, DateTime timeOfReading) {
    super(deviceName, timeOfReading);
    super.deviceType = "DO";
    super.readQueryTimeDelay = NOMINAL_READ_DELAY + ADD_READ_DELAY;
    this.dissolvedOxygen = dissolvedOxygen;
    this.saturationPercentage = saturationPercentage;
  }

  public Double getDissolvedOxygen() {
    return dissolvedOxygen;
  }

  public void setDissolvedOxygen(Double dissolvedOxygen) {
    this.dissolvedOxygen = dissolvedOxygen;
  }

  public Double getSaturationPercentage() {
    return saturationPercentage;
  }

  public void setSaturationPercentage(Double saturationPercentage) {
    this.saturationPercentage = saturationPercentage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    DOSensorData that = (DOSensorData) o;

    if (dissolvedOxygen != null ? !dissolvedOxygen.equals(that.dissolvedOxygen) : that.dissolvedOxygen != null)
      return false;
    return saturationPercentage != null ? saturationPercentage.equals(that.saturationPercentage) : that.saturationPercentage == null;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (dissolvedOxygen != null ? dissolvedOxygen.hashCode() : 0);
    result = 31 * result + (saturationPercentage != null ? saturationPercentage.hashCode() : 0);
    return result;
  }

  @Override
  public void parseSensorDataFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse).trim();
    String[] responseArray = response.split(",");
    if (responseArray.length < 2) {
      LOGGER.error("Error while parsing sensor values: found array of size %d and expected 2.\n" +
          "Device response was %s", responseArray.length, Arrays.toString(responseArray));
    }
    setDissolvedOxygen(Double.parseDouble(responseArray[0]));
    setSaturationPercentage(Double.parseDouble(responseArray[1]));
  }
}
