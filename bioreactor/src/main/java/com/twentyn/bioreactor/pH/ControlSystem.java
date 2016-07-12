package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi4j.io.gpio.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import org.joda.time.DateTime;

public class ControlSystem {

  private static final Integer TOTAL_DURATION_OF_RUN_IN_MILLISECONDS = 60000;
  private static final String SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading.json";
  private ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LogManager.getFormatterLogger(ControlSystem.class);
  private static final Double TARGET_PH = 7.0;

  private GpioController gpioController;
  private GpioPinDigitalOutput pumpReversePin;
  private GpioPinDigitalOutput pumpForwardPin;
  private GpioPinDigitalOutput pumpEnablePin;

  public ControlSystem() {
    OBJECT_MAPPER.registerModule(new JodaModule());
    initializeGPIOPins();
  }

  private void initializeGPIOPins() {
    gpioController = GpioFactory.getInstance();
    pumpReversePin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_00, "ReverseMode", PinState.LOW);
    pumpForwardPin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_01, "ForwardMode", PinState.LOW);
    pumpEnablePin = gpioController.provisionDigitalOutputPin(RaspiPin.GPIO_07, "PumpEnable", PinState.LOW);

    // set shutdown state for pins
    pumpReversePin.setShutdownOptions(true, PinState.LOW);
    pumpForwardPin.setShutdownOptions(true, PinState.LOW);
    pumpEnablePin.setShutdownOptions(true, PinState.LOW);

    // Set mode to be forward
    pumpForwardPin.high();
  }

  private Double readPHValue() throws IOException {
    File file = new File(SENSOR_READING_FILE_LOCATION);
    PHSensorData sensorData = OBJECT_MAPPER.readValue(file, PHSensorData.class);
    return sensorData.getpH();
  }

  private void run() {
    DateTime startTime = new DateTime();
    DateTime currTime = new DateTime();

    while (currTime.getMillis() - startTime.getMillis() < TOTAL_DURATION_OF_RUN_IN_MILLISECONDS) {
      try {
        Double phValue = readPHValue();
        System.out.println(String.format("PH value is %d", phValue));

        if (phValue > TARGET_PH) {
          System.out.println(String.format("Pump more solution"));
          pumpEnablePin.high();
          Thread.sleep(1000);

          System.out.println(String.format("Stop pumping"));
          pumpEnablePin.low();
          Thread.sleep(1000);
        }
      } catch (IOException e) {
        LOGGER.error("Could not read pH value due to IOException. Error is %s:", e.getMessage());
      } catch (InterruptedException e) {
        LOGGER.error("Could not read pH value due to InterruptedException. Error is %s:", e.getMessage());
      }

      currTime = new DateTime();
    }

    gpioController.shutdown();
  }

  public static void main(String[] args) throws Exception {
    ControlSystem controlSystem = new ControlSystem();
    controlSystem.run();
  }
}
