package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.joda.time.DateTime;

public class Sensor {

  private static final String SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading_test.json";
  private static final String SENSOR_LOG_FILE_LOCATION = "/tmp/sensors/v1/pH/reading_log.json";
  private static final Logger LOGGER = LogManager.getFormatterLogger(Sensor.class);

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

  public Sensor(int address) {
    objectMapper.registerModule(new JodaModule());
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    // Connect to bus
    int i2CBus = I2CBus.BUS_1;
    try {
      bus = I2CFactory.getInstance(i2CBus);
      LOGGER.info("Connected to bus");
    } catch (Exception e) {
      LOGGER.error("Connection to bus %d failed: %s\n", i2CBus, e);
    }

    // Connect to device
    try {
      sensor = bus.getDevice(ADDRESS);
      LOGGER.info("Connected to device at address %d\n", address);

    } catch (Exception e) {
      LOGGER.error("Connection to device at address %d failed: %s\n", address, e);
    }
  }

  public Boolean readSuccess(byte[] deviceResponse) {
    if (deviceResponse[0] == (byte) 1) {
      return true;
    }
    return false;
  }

  public Double readSensorValue() {

    byte[] deviceResponse = new byte[N_BYTES];
    try {
      sensor.write(READ_COMMAND);
      Thread.sleep(READ_QUERY_TIMEOUT);

      sensor.read(deviceResponse, 0, N_BYTES);
      int retryCounter = 0;
      while (!readSuccess(deviceResponse) && retryCounter < N_RETRIES) {
        LOGGER.debug("Read failed: will try %d times more", N_RETRIES - retryCounter);
        Thread.sleep(RETRY_TIMEOUT);
        retryCounter++;
        sensor.read(deviceResponse, 0, N_BYTES);
      }
      if (!readSuccess(deviceResponse)) {
        LOGGER.error("Did not manage to read sensor values after %d retries\n", N_RETRIES);
      }
      LOGGER.debug("Read succeeded after %d retries", retryCounter);

    } catch (IOException e) {
      LOGGER.error("Error reading sensor value: " + e.getMessage());
    } catch (InterruptedException e) {
      LOGGER.error("Interrupted Exception: " + e.getMessage());
    }
    return parseSensorValueFromResponse(deviceResponse);
  }

  public Double parseSensorValueFromResponse(byte[] deviceResponse) {
    String response = new String(deviceResponse);
    return Double.parseDouble(response);
  }

  public void run() {
    try {
      JsonGenerator g = objectMapper.getFactory().createGenerator(
          new File(SENSOR_LOG_FILE_LOCATION), JsonEncoding.UTF8);
      while(true) {
        Double phValueFromResponse = readSensorValue();
        DateTime currTime = new DateTime();
        PHSensorData phSensorData = new PHSensorData(phValueFromResponse, DEVICE_NAME, currTime);
        try {
          // Writing single value for control module to use
          objectMapper.writeValue(new File(SENSOR_READING_FILE_LOCATION), phSensorData);
          // Appending value to log file
          objectMapper.writeValue(g, phSensorData);
        } catch (IOException e) {
          LOGGER.error("Exception when trying to write phSensorData: %s", e);
        }
      }
    } catch (IOException e) {
      LOGGER.error("Exception when trying to log phSensorData: %s", e);
    }
  }

  public static void main(String[] args) {
    Sensor sensor = new Sensor(ADDRESS);
    sensor.run();
  }
}
