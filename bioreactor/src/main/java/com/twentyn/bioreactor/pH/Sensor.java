package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.joda.time.DateTime;

public class Sensor {

  private static final String SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading.json";
  private static final Logger LOGGER = LogManager.getFormatterLogger(ControlSystem.class);

  // Device address
  private static final int ADDRESS = 99;
  // READ command for sensor
  private static final byte READ_COMMAND = (byte) 0x52; // R in hex
  // Number of bytes to read from the response
  // pH sensor: the response format is [1,{pH},null] where pH is encoded over 5 bytes
  //            hence, total number of bytes to read is 7 (response should never be more according to datasheet)
  //            http://www.atlas-scientific.com/_files/_datasheets/_circuit/pH_EZO_datasheet.pdf
  private static final int N_BYTES = 7;
  // Time delay to read response from the chip
  // According to the datasheet, 1sec. Adding 500ms for safety.
  private static final int READ_QUERY_TIMEOUT = 1500;
  private static final int RETRY_TIMEOUT = 500;
  private static final int N_RETRIES = 3;
  // Device name
  private static final String DEVICE_NAME = "pH_sensor_0";

  //I2C bus
  private I2CBus bus;
  // Device object
  private I2CDevice sensor;

  private ObjectMapper objectMapper = new ObjectMapper();


  public Sensor() {

    objectMapper.registerModule(new JodaModule());
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    // Connect to bus
    int i2CBus = I2CBus.BUS_1;
    try {
      bus = I2CFactory.getInstance(i2CBus);
      System.out.println("Connected to bus");
    } catch (Exception e) {
      System.err.format("Connection to bus %d failed: %s\n", i2CBus, e);
    }

    // Connect to device
    try {
      sensor = bus.getDevice(ADDRESS);
      System.out.format("Connected to device at address %d\n", ADDRESS);

    } catch (Exception e) {
      System.err.format("Connection to device at address %d failed: %s\n", ADDRESS, e);
    }
  }

  public Boolean readSuccess(byte[] deviceResponse) {
    if (deviceResponse[0] == (byte) 1) {
      return true;
    }
    return false;
  }

  public byte[] getDeviceResponse() {

    byte[] deviceResponse = new byte[N_BYTES];

    try {
      sensor.write(READ_COMMAND);
      Thread.sleep(READ_QUERY_TIMEOUT);

      sensor.read(deviceResponse, 0, N_BYTES);
      int retryCounter = 0;
      while (!readSuccess(deviceResponse) && retryCounter < N_RETRIES) {
        Thread.sleep(RETRY_TIMEOUT);
        retryCounter++;
        sensor.read(deviceResponse, 0, N_BYTES);
      }
      if (!readSuccess(deviceResponse)) {
        System.err.format("Did not manage to read sensor values after %d retries\n", N_RETRIES);
      }

    } catch (IOException e) {
      System.out.println("Error reading sensor value: " + e.getMessage());
    } catch (InterruptedException e) {
      System.out.println("Interrupted Exception: " + e.getMessage());
    }
    return deviceResponse;
  }

  public Double getPHValueFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse);
    return Double.parseDouble(response);
  }

  public static void main(String[] args) {
    Sensor sensor = new Sensor();
    byte[] response = sensor.getDeviceResponse();
    Double phValueFromResponse = sensor.getPHValueFromResponse(response);
    DateTime currTime = new DateTime();
    PHSensorData phSensorData = new PHSensorData(phValueFromResponse, DEVICE_NAME, currTime);
    try {
      System.out.println(sensor.objectMapper.writeValueAsString(phSensorData));
    } catch (IOException e) {
      System.err.println("Exception when trying to write phSensorData");
    }
  }
}
