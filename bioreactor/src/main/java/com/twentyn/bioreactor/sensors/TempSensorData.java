package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

public class TempSensorData extends SensorData {

  private static final Integer NOMINAL_READ_DELAY = 600;

  @JsonProperty("temperature")
  private Double temperature;

  public TempSensorData() {
    super.deviceType = "TEMP";
    super.readQueryTimeDelay = NOMINAL_READ_DELAY + ADD_READ_DELAY;
  }

  public TempSensorData(Double temperature, String deviceName, DateTime timeOfReading) {
    super(deviceName, timeOfReading);
    super.deviceType = "TEMP";
    super.readQueryTimeDelay = NOMINAL_READ_DELAY + ADD_READ_DELAY;
    this.temperature = temperature;
  }

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(Double temperature) {
    this.temperature = temperature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TempSensorData that = (TempSensorData) o;

    return temperature != null ? temperature.equals(that.temperature) : that.temperature == null;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (temperature != null ? temperature.hashCode() : 0);
    return result;
  }

  @Override
  public void parseSensorDataFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse).trim();
    setTemperature(Double.parseDouble(response));
  }
}
