package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.twentyn.bioreactor.util.json.DateTimeSerde;
import org.joda.time.DateTime;

public class DOSensorData {

  @JsonProperty("dissolved_oxygen")
  private Double dissolvedOxygen;

  @JsonProperty("saturation_percentage")
  private Double saturationPercentage;

  @JsonProperty("device")
  private String deviceName;

  @JsonProperty("timestamp")
  @JsonSerialize(using=DateTimeSerde.DateTimeSerializer.class)
  @JsonDeserialize(using=DateTimeSerde.DateTimeDeserializer.class)
  private DateTime timeOfReading;

  public DOSensorData() {}

  public DOSensorData(Double dissolvedOxygen, Double saturationPercentage, String deviceName, DateTime timeOfReading) {
    this.dissolvedOxygen = dissolvedOxygen;
    this.saturationPercentage = saturationPercentage;
    this.deviceName = deviceName;
    this.timeOfReading = timeOfReading;
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

    DOSensorData that = (DOSensorData) o;

    if (!dissolvedOxygen.equals(that.dissolvedOxygen)) return false;
    if (!saturationPercentage.equals(that.saturationPercentage)) return false;
    if (!deviceName.equals(that.deviceName)) return false;
    return timeOfReading.equals(that.timeOfReading);

  }

  @Override
  public int hashCode() {
    int result = dissolvedOxygen.hashCode();
    result = 31 * result + saturationPercentage.hashCode();
    result = 31 * result + deviceName.hashCode();
    result = 31 * result + timeOfReading.hashCode();
    return result;
  }
}
