package com.twentyn.bioreactor.pH;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import java.io.IOException;

public class Sensor {

  // Device address
  private static final int ADDRESS = 97;
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
  // Device name
  private static final String DEVICE_NAME = "pH_sensor_0";

  //I2C bus
  private I2CBus bus;
  // Device object
  private I2CDevice sensor;


  public Sensor() {

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

  public byte[] getDeviceResponse() {

    byte[] deviceResponse = new byte[N_BYTES];

    try {
      sensor.write(READ_COMMAND);
      Thread.sleep(READ_QUERY_TIMEOUT);

      sensor.read(deviceResponse, 0, N_BYTES);

    } catch (IOException e) {
      System.out.println("Error reading sensor value: " + e.getMessage());
    } catch (InterruptedException e) {
      System.out.println("Interrupted Exception: " + e.getMessage());
    }
    return deviceResponse;
  }

  public static void main(String[] args) {
    Sensor sensor = new Sensor();
    byte[] response = sensor.getDeviceResponse();
    System.out.print("pH: ");
    for (byte b : response) {
      System.out.print((char)(b & 0xFF));
    }
    System.out.println();
  }
}
