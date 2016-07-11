package com.twentyn.bioreactor.pH;

import com.pi4j.io.gpio.*;

public class ControlSystem {

  private static Integer TOTAL_DURATION_OF_RUN_IN_SECONDS = 60;
  private GpioController gpioController;
  private GpioPinDigitalOutput pumpReversePin;
  private GpioPinDigitalOutput pumpForwardPin;
  private GpioPinDigitalOutput pumpEnablePin;

  public ControlSystem() {
    initializeGPIOPins();
    run();
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

  private Double readData() {
    return 7.02;
  }

  private void run() {

  }

  public static void main(String[] args) throws InterruptedException {

    System.out.println("<--Pi4J--> GPIO Control Example ... started.");

    // create gpio controller
    final GpioController gpio = GpioFactory.getInstance();

    // provision gpio pin #01 as an output pin and turn on
    final GpioPinDigitalOutput pin11 =
        gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, "Motor", PinState.LOW);

    final GpioPinDigitalOutput pin12 =
        gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Motor1", PinState.LOW);

    final GpioPinDigitalOutput pin7 =
        gpio.provisionDigitalOutputPin(RaspiPin.GPIO_07, "Motor2", PinState.LOW);

    // set shutdown state for pins
    pin11.setShutdownOptions(true, PinState.LOW);
    pin12.setShutdownOptions(true, PinState.LOW);
    pin7.setShutdownOptions(true, PinState.LOW);

    pin12.high();
    pin7.high();

    System.out.println("--> GPIO state should be: Motor running");

    Thread.sleep(1000);

    // turn off gpio pin #01
    pin7.low();
    System.out.println("--> GPIO state should be: Motor stopped");

    Thread.sleep(1000);

    // toggle the current state of gpio pin #01 (should turn on)
    pin7.toggle();
    System.out.println("--> GPIO state should be: Motor running");

    Thread.sleep(1000);

    // toggle the current state of gpio pin #01  (should turn off)
    pin7.toggle();
    System.out.println("--> GPIO state should be: Motor stopped");

    Thread.sleep(1000);

    // // turn on gpio pin #01 for 1 second and then off
    // System.out.println("--> GPIO state should be: ON for only 1 second");
    // pin7.pulse(1000, true); // set second argument to 'true' use a blocking call

    pin7.low();
    Thread.sleep(500);

    // stop all GPIO activity/threads by shutting down the GPIO controller
    // (this method will forcefully shutdown all GPIO monitoring threads and scheduled tasks)
    gpio.shutdown();
  }
}
