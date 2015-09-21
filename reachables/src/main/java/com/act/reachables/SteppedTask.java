package com.act.reachables;

public abstract class SteppedTask {
  abstract double percentDone();
  abstract void doMoreWork();
  abstract void init();
  abstract void finalize(TaskMonitor tm);

  void run() {
    TaskMonitor tm = new TaskMonitor();
    init();
    while (percentDone() != 100) {
      doMoreWork();
    }
    finalize(tm);
  }
}

class TaskMonitor {
  TaskMonitor() {}

  void setStatus(String status) {
  }
  void setPercentCompleted(int pc) {
  }
}
