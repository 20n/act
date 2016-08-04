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
