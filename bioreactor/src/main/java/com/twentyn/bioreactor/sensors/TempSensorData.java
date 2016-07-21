package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.twentyn.bioreactor.util.json.DateTimeSerde;
import org.joda.time.DateTime;

public class TempSensorData {

  @JsonProperty("temperature")
  private Double temperature;

  @JsonProperty("device")
  private String deviceName;

  @JsonProperty("timestamp")
  @JsonSerialize(using=DateTimeSerde.DateTimeSerializer.class)
  @JsonDeserialize(using=DateTimeSerde.DateTimeDeserializer.class)
  private DateTime timeOfReading;

  public TempSensorData() {}

  public TempSensorData(Double temperature, String deviceName, DateTime timeOfReading) {
    this.temperature = temperature;
    this.deviceName = deviceName;
    this.timeOfReading = timeOfReading;
  }

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(Double temperature) {
    this.temperature = temperature;
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

    TempSensorData that = (TempSensorData) o;

    if (!temperature.equals(that.temperature)) return false;
    if (!deviceName.equals(that.deviceName)) return false;
    return timeOfReading.equals(that.timeOfReading);

  }

  @Override
  public int hashCode() {
    int result = temperature.hashCode();
    result = 31 * result + deviceName.hashCode();
    result = 31 * result + timeOfReading.hashCode();
    return result;
  }
}
