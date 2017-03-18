/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package com.twentyn.bioreactor.pH;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
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

  public void switchMotorOn() {
    pumpEnablePin.high();
  }

  public void switchMotorOff() {
    pumpEnablePin.low();
  }
}
