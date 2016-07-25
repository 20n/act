package com.twentyn.bioreactor.pH;

import com.pi4j.io.gpio.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MotorPinConfiguration {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MotorPinConfiguration.class);

  public enum PinNumberingScheme {
    BOARD,
    // TODO: Implement BCM pin configuration
    BCM
  }

  private PinNumberingScheme scheme;
  private GpioController gpioController;
  private GpioPinDigitalOutput pumpReversePin;
  private GpioPinDigitalOutput pumpForwardPin;
  private GpioPinDigitalOutput pumpEnablePin;

  public MotorPinConfiguration(PinNumberingScheme scheme) {
    this.scheme = scheme;
  }

  public void initializeGPIOPinsAndSetConfigToStartState() {
    if (this.scheme == PinNumberingScheme.BOARD) {
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
    } else {
      LOGGER.error("We currently do not support any other pin configurations.");
    }
  }

  public void shutdownFermentation() {
    gpioController.shutdown();
  }

  public GpioController getGpioController() {
    return gpioController;
  }

  public GpioPinDigitalOutput getPumpReversePin() {
    return pumpReversePin;
  }

  public GpioPinDigitalOutput getPumpForwardPin() {
    return pumpForwardPin;
  }

  public GpioPinDigitalOutput getPumpEnablePin() {
    return pumpEnablePin;
  }
}
