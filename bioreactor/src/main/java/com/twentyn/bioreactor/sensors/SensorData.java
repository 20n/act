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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.twentyn.bioreactor.util.json.DateTimeSerDe;
import org.joda.time.DateTime;

public abstract class SensorData {

  // As a safeguard against missed readings, we add an extra 200 ms before we try to read the device response
  static final Integer ADD_READ_DELAY = 200; // 200 ms

  @JsonIgnore
  String deviceType;

  // Sensor have nominal read delays (aka processing time).
  @JsonIgnore
  Integer readQueryTimeDelay;

  @JsonProperty("device")
  private String deviceName;

  @JsonProperty("timestamp")
  @JsonSerialize(using=DateTimeSerDe.DateTimeSerializer.class)
  @JsonDeserialize(using=DateTimeSerDe.DateTimeDeserializer.class)
  private DateTime timeOfReading;

  public SensorData() {}

  public SensorData(String deviceName, DateTime timeOfReading) {
    this.deviceName = deviceName;
    this.timeOfReading = timeOfReading;
  }

  public String getDeviceType() {
    return deviceType;
  }

  public void setDeviceType(String deviceType) {
    this.deviceType = deviceType;
  }

  public Integer getReadQueryTimeDelay() {
    return readQueryTimeDelay;
  }


  public String getDeviceName() {
    return deviceName;
  }

  public void setDeviceName(String deviceName) {
    this.deviceName = deviceName;
  }

  public DateTime getTimeOfReading() {
    return timeOfReading;
  }

  public void setTimeOfReading(DateTime timeOfReading) {
    this.timeOfReading = timeOfReading;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SensorData that = (SensorData) o;

    if (deviceName != null ? !deviceName.equals(that.deviceName) : that.deviceName != null) return false;
    return timeOfReading != null ? timeOfReading.equals(that.timeOfReading) : that.timeOfReading == null;

  }

  @Override
  public int hashCode() {
    int result = deviceName != null ? deviceName.hashCode() : 0;
    result = 31 * result + (timeOfReading != null ? timeOfReading.hashCode() : 0);
    return result;
  }

  public abstract void parseSensorDataFromResponse(byte[] deviceResponse);
}
