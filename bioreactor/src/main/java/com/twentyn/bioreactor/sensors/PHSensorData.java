package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.twentyn.bioreactor.util.json.DateTimeSerde;
import org.joda.time.DateTime;

public class PHSensorData {

  @JsonProperty("pH")
  private Double pH;

  @JsonProperty("device")
  private String deviceName;

  @JsonProperty("timestamp")
  @JsonSerialize(using=DateTimeSerde.DateTimeSerializer.class)
  @JsonDeserialize(using=DateTimeSerde.DateTimeDeserializer.class)
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PHSensorData that = (PHSensorData) o;

    if (!pH.equals(that.pH)) return false;
    if (!deviceName.equals(that.deviceName)) return false;
    return timeOfReading.equals(that.timeOfReading);

  }

  @Override
  public int hashCode() {
    int result = pH.hashCode();
    result = 31 * result + deviceName.hashCode();
    result = 31 * result + timeOfReading.hashCode();
    return result;
  }
}
