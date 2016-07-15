package com.twentyn.bioreactor.simulation;

import com.twentyn.bioreactor.pH.ControlSystem;
import com.twentyn.bioreactor.sensors.PHSensorData;
import org.joda.time.DateTime;

import java.io.File;

public class Simulator extends ControlSystem {

  private static final Double FLOW_RATE = 1.0; // in mL/s
  private static final Double ACID_PH = 3.0;
  private static final Double BASE_PH = 11.0;
  private static final String DEVICE_NAME = "virtual_device";
  private static final Double INIT_PH_DATA = 1.0;

  private PHSensorData currentSensorData;
  private Action lastAction;
  private Integer volume; // in mL

  private class Action {
    private SOLUTION solution;
    private Integer duration;
    private DateTime timestamp;

    public Action(SOLUTION solution, Integer duration, DateTime timestamp) {
      this.solution = solution;
      this.duration = duration;
      this.timestamp = timestamp;
    }

    public Integer getDuration() {
      return duration;
    }

    public SOLUTION getSolution() {
      return solution;
    }
  }

  public Simulator() {
    currentSensorData = new PHSensorData(INIT_PH_DATA, DEVICE_NAME, new DateTime());
  }

  private void updateSensorDataWithAction(Action action) {
    Double solutionPH = (action.getSolution().equals(SOLUTION.ACID)) ? ACID_PH : BASE_PH;
    Double addedVolume = FLOW_RATE * action.getDuration();
    Double totalVolume = addedVolume + volume;
    Double bioreactorPH = (volume * currentSensorData.getpH() + addedVolume * solutionPH) / totalVolume;
    currentSensorData = new PHSensorData(bioreactorPH, DEVICE_NAME, new DateTime());
  }

  @Override
  protected void takeAction() {
    lastAction = new Action(solution, PUMP_TIME_WAIT_IN_MILLI_SECONDS, new DateTime());
  }

  @Override
  protected PHSensorData readPhSensorData(File f) {
    updateSensorDataWithAction(lastAction);
    return currentSensorData;
  }
}

