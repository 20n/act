package com.twentyn.bioreactor.pH;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi4j.io.gpio.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.omg.CORBA.OBJ_ADAPTER;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class ControlSystem {

  private static final Integer TOTAL_DURATION_OF_RUN_IN_MILLISECONDS = 10000;
  private static final String SENSOR_READING_FILE_LOCATION = "/tmp/sensors/v1/pH/reading.json";
  private ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LogManager.getFormatterLogger(ControlSystem.class);
  private static final Double TARGET_PH = 7.0;

  private GpioController gpioController;
  private GpioPinDigitalOutput pumpReversePin;
  private GpioPinDigitalOutput pumpForwardPin;
  private GpioPinDigitalOutput pumpEnablePin;

  public ControlSystem() {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss.SSSZZ");
    OBJECT_MAPPER.setDateFormat(dateFormat);
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

        if (phValue > TARGET_PH) {
          pumpEnablePin.high();
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

  public static void main(String[] args) throws InterruptedException {

    ControlSystem controlSystem = new ControlSystem();
    controlSystem.run();

//    System.out.println("<--Pi4J--> GPIO Control Example ... started.");
//
//    // create gpio controller
//    final GpioController gpio = GpioFactory.getInstance();
//
//    // provision gpio pin #01 as an output pin and turn on
//    final GpioPinDigitalOutput pin11 =
//        gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, "Motor", PinState.LOW);
//
//    final GpioPinDigitalOutput pin12 =
//        gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Motor1", PinState.LOW);
//
//    final GpioPinDigitalOutput pin7 =
//        gpio.provisionDigitalOutputPin(RaspiPin.GPIO_07, "Motor2", PinState.LOW);
//
//    // set shutdown state for pins
//    pin11.setShutdownOptions(true, PinState.LOW);
//    pin12.setShutdownOptions(true, PinState.LOW);
//    pin7.setShutdownOptions(true, PinState.LOW);
//
//    pin12.high();
//    pin7.high();
//
//    System.out.println("--> GPIO state should be: Motor running");
//
//    Thread.sleep(1000);
//
//    // turn off gpio pin #01
//    pin7.low();
//    System.out.println("--> GPIO state should be: Motor stopped");
//
//    Thread.sleep(1000);
//
//    // toggle the current state of gpio pin #01 (should turn on)
//    pin7.toggle();
//    System.out.println("--> GPIO state should be: Motor running");
//
//    Thread.sleep(1000);
//
//    // toggle the current state of gpio pin #01  (should turn off)
//    pin7.toggle();
//    System.out.println("--> GPIO state should be: Motor stopped");
//
//    Thread.sleep(1000);
//
//    // // turn on gpio pin #01 for 1 second and then off
//    // System.out.println("--> GPIO state should be: ON for only 1 second");
//    // pin7.pulse(1000, true); // set second argument to 'true' use a blocking call
//
//    pin7.low();
//    Thread.sleep(500);
//
//    // stop all GPIO activity/threads by shutting down the GPIO controller
//    // (this method will forcefully shutdown all GPIO monitoring threads and scheduled tasks)
//    gpio.shutdown();
  }
}
