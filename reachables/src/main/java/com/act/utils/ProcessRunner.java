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

package com.act.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A simple class to run child processes and log their output.
 * TODO: is there a way we can hook into `ShellJob` without having to run inside a workflow?
 */
public class ProcessRunner {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ProcessRunner.class);

  /**
   * Run's a child process using the specified command and arguments.
   * @param command The process to run.
   * @param args The arguments to pass to that process.
   * @return The exit code of the child process.
   * @throws InterruptedException
   * @throws IOException
   */
  public static int runProcess(String command, List<String> args) throws InterruptedException, IOException {
    return runProcess(command, args, null);
  }

  /**
   * Run's a child process using the specified command and arguments, timing out after a specified number of seconds
   * if the process does not terminate on its own in that time.
   * @param command The process to run.
   * @param args The arguments to pass to that process.
   * @param timeoutInSeconds A timeout to impose on the child process; an InterruptedException is likely to occur
   *                         when the child process times out.
   * @return The exit code of the child process.
   * @throws InterruptedException
   * @throws IOException
   */
  public static int runProcess(String command, List<String> args, Long timeoutInSeconds)
      throws InterruptedException, IOException {
    /* The ProcessBuilder doesn't differentiate the command from its arguments, but we do in our API to ensure the user
     * doesn't just pass us a single string command, which invokes the shell and can cause all sorts of bugs and
     * security issues. */
    List<String> commandAndArgs = new ArrayList<String>(args.size() + 1) {{
      add(command);
      addAll(args);
    }};
    ProcessBuilder processBuilder = new ProcessBuilder(commandAndArgs);
    LOGGER.info("Running child process: %s", StringUtils.join(commandAndArgs, " "));

    Process p = processBuilder.start();
    // Log whatever the child writes.
    new StreamLogger(p.getInputStream(), l -> LOGGER.info("[child STDOUT]: %s", l)).run();
    new StreamLogger(p.getErrorStream(), l -> LOGGER.warn("[child STDERR]: %s", l)).run();
    // Wait for the child process to exit, timing out if it takes to long to finish.
    if (timeoutInSeconds != null) {
      p.waitFor(timeoutInSeconds, TimeUnit.SECONDS);
    } else {
      p.waitFor();
    }

    // 0 is the default success exit code in *nix land.
    if (p.exitValue() != 0) {
      LOGGER.error("Child process exited with non-zero status code: %d", p.exitValue());
    }

    return p.exitValue();
  }

  private static class StreamLogger implements Runnable {
    /* With help from http://stackoverflow.com/questions/14165517/processbuilder-forwarding-stdout-and-stderr-of-started-processes-without-blocki
     * Asynchronously read and log a child process's stdout or stderr. */
    private InputStream stream;
    private Consumer<String> consumer;
    public StreamLogger(InputStream stream, Consumer<String> consumer) {
      this.stream = stream;
      this.consumer = consumer;
    }

    @Override
    public void run() {
      new BufferedReader(new InputStreamReader(stream)).lines().forEach(consumer);
      try {
        stream.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
