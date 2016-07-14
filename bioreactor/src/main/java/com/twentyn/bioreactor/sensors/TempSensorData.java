package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

public class TempSensorData {

  @JsonProperty("temperature")
  private Double temperature;

  @JsonProperty("device")
  private String deviceName;

  @JsonProperty("timestamp")
  private DateTime timeOfReading;

  public TempSensorData(Double temperature, String deviceName, DateTime timeOfReading) {
    this.temperature = temperature;
    this.deviceName = deviceName;
    this.timeOfReading = timeOfReading;
  }
}
