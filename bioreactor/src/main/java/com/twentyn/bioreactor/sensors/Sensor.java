package com.twentyn.bioreactor.sensors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class Sensor {

  protected static final Logger LOGGER = LogManager.getFormatterLogger(Sensor.class);

  //I2C bus
  protected I2CBus bus;
  // Device object
  protected I2CDevice sensor;
  // Device address
  protected int deviceAddress;
  // Device name
  protected String deviceName;
  // Sensor reading file location
  protected String sensorReadingFileLocation;
  // Sensor reading log file location
  protected String sensorReadingLogFileLocation;

  // Sensor config parameters
  protected byte readCommand;
  protected int readQueryTimeout;
  protected int retryTimeout;
  protected int nRetries;
  protected int nBytes;


  protected ObjectMapper objectMapper = new ObjectMapper();

  public Sensor() {
    objectMapper.registerModule(new JodaModule());
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
  }

  public void connect() {
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
      sensor = bus.getDevice(deviceAddress);
      LOGGER.info("Connected to device at address %d\n", deviceAddress);

    } catch (Exception e) {
      LOGGER.error("Connection to device at address %d failed: %s\n", deviceAddress, e);
    }
  }

  public void setSensorConfig(byte readCommand, int readQueryTimeout, int retryTimeout, int nRetries, int nBytes) {
    this.readCommand = readCommand;
    this.readQueryTimeout = readQueryTimeout;
    this.retryTimeout = retryTimeout;
    this.nRetries = nRetries;
    this.nBytes = nBytes;
  }

  public Boolean readSuccess(byte[] deviceResponse) {
    if (deviceResponse[0] == (byte) 1) {
      return true;
    }
    return false;
  }

  public void issueCommand(byte command, int timeout) {
    try {
      sensor.write(command);
      Thread.sleep(timeout);
    } catch (IOException e) {
      LOGGER.error("Error writing read command: " + e.getMessage());
    } catch (InterruptedException e) {
      LOGGER.error("Interrupted Exception: " + e.getMessage());
    }
  }

  public byte[] readSensorResponse() {

    byte[] deviceResponse = new byte[nBytes];
    try {
      sensor.write(readCommand);
      Thread.sleep(readQueryTimeout);

      sensor.read(deviceResponse, 0, nBytes);
      int retryCounter = 0;
      while (!readSuccess(deviceResponse) && retryCounter < nRetries) {
        LOGGER.debug("Read failed: will try %d times more", nRetries - retryCounter);
        Thread.sleep(retryTimeout);
        retryCounter++;
        sensor.read(deviceResponse, 0, nBytes);
      }
      if (!readSuccess(deviceResponse)) {
        LOGGER.error("Did not manage to read sensor values after %d retries\n", nRetries);
      }
      LOGGER.debug("Read succeeded after %d retries", retryCounter);

    } catch (IOException e) {
      LOGGER.error("Error reading sensor value: " + e.getMessage());
    } catch (InterruptedException e) {
      LOGGER.error("Interrupted Exception: " + e.getMessage());
    }
    return deviceResponse;
  }

}
