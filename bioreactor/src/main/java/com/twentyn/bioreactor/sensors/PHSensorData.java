package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;

public class PHSensorData extends SensorData {

  @JsonProperty("pH")
  private Double pH;

  public PHSensorData(Double pH, String deviceName, DateTime timeOfReading) {
    super(deviceName, timeOfReading);
    this.pH = pH;
  }

  public Double getpH() {
    return pH;
  }

  public void setpH(Double pH) {
    this.pH = pH;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    PHSensorData that = (PHSensorData) o;

    return pH != null ? pH.equals(that.pH) : that.pH == null;

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (pH != null ? pH.hashCode() : 0);
    return result;
  }
}
