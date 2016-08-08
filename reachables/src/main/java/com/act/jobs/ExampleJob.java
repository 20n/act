package com.act.jobs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * This template provides an example for how a Java function should be wrapped by a JavaRunnable instance
 * in order to make it useable in our workflow manager.
 */
public class ExampleJob {

  // This field will need to be handled in getDuplicateTextJob
  final Integer numberTimes;

  public ExampleJob(Integer numberTimes) {
    this.numberTimes = numberTimes;
  }

  /**
   * Imagine that this function contained nontrivial functionality that we wanted to wrap in a job
   *
   * @param text Unfortunately this function's parameter is a String. To use this in a job, we'll have to have
   * it read in its input from a file.
   * @return Unfortunately this method also outputs a string. We'll have to make this print to file as well.
   */
  public String duplicateText(String text) {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < numberTimes; i++) {
      builder.append(text).append(text);
    }

    return builder.toString();
  }

  /**
   * This method encapsulates the functionality of duplicateText in a way that can be used as a Job.
   * You provide the input file and output file through the method interface, and a parameterless JavaRunnable
   * job that uses those files is returned.
   *
   * @param inputFile The file from which to read the input.
   * @param outputFile The file to which to write the output.
   * @param numberTimes The number of times to duplicate the input text.
   * @return A JavaRunnable to represent this action.
   */
  public static JavaRunnable getDuplicateTextJob(File inputFile, File outputFile, Integer numberTimes) {
    // Since JavaRunnable is a one-method interface, we can use lambdas to write this very succinctly!
    return () -> {
      // Handle input file
      BufferedReader reader = new BufferedReader(new FileReader(inputFile));
      String textToEcho = reader.readLine();

      // Build the text duplicator
      ExampleJob textDuplicator = new ExampleJob(numberTimes);

      // Run the job
      String result = textDuplicator.duplicateText(textToEcho);

      // Handle output file
      FileWriter writer = new FileWriter(outputFile);
      writer.write(result);

      System.out.println("We did it!");
    };
  }

  /**
   * Now, to use this in a workflow, all we'll have to do is call:
   *
   * val duplicatorJob = JavaJobWrapper.wrapJavaFunction(ExampleJob.getDuplicateTextJob(fileIn, fileOut, n))
   */
}
