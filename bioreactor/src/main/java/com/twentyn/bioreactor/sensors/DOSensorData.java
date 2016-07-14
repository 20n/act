package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

public class DOSensorData {

  @JsonProperty("dissolved_oxygen")
  private Double dissolvedOxygen;

  @JsonProperty("saturation_percentage")
  private Double saturationPercentage;

  @JsonProperty("device")
  private String deviceName;

  @JsonProperty("timestamp")
  private DateTime timeOfReading;

  public DOSensorData(Double dissolvedOxygen, Double saturationPercentage, String deviceName, DateTime timeOfReading) {
    this.dissolvedOxygen = dissolvedOxygen;
    this.saturationPercentage = saturationPercentage;
    this.deviceName = deviceName;
    this.timeOfReading = timeOfReading;
  }
}
