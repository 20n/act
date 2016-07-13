package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

public class PHSensorData {

  @JsonProperty("pH")
  private Double pH;

  @JsonProperty("device")
  private String deviceName;

  @JsonProperty("timestamp")
  private DateTime timeOfReading;

  public PHSensorData() {}

  public PHSensorData(Double pH, String deviceName, DateTime timeOfReading) {
    this.pH = pH;
    this.deviceName = deviceName;
    this.timeOfReading = timeOfReading;
  }

  public Double getpH() {
    return pH;
  }

  public void setpH(Double pH) {
    this.pH = pH;
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
}
