package com.act.jobs;

import java.io.IOException;

/**
 * This class provides the interface through which Java functions can be passed into the workflow manager
 * for incorporation into a workflow.
 */
public interface JavaRunnable {
  void run() throws IOException;
}
