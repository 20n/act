package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.twentyn.bioreactor.util.json.DateTimeSerDe;
import org.joda.time.DateTime;

public abstract class SensorData {

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
