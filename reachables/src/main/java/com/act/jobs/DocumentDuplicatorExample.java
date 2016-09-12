package com.act.jobs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This template provides an example for how a Java function should be wrapped by a JavaRunnable instance
 * in order to make it useable in our workflow manager.
 */
public class DocumentDuplicatorExample {

  private static final Logger LOGGER = LogManager.getFormatterLogger(DocumentDuplicatorExample.class);

  // This field will need to be handled in getRunnableDocumentDuplicator()
  final Integer numberTimes;

  public DocumentDuplicatorExample(Integer numberTimes) {
    this.numberTimes = numberTimes;
  }

  /**
   * Imagine that this function contained nontrivial functionality that we wanted to wrap in a job
   *
   * @param text Unfortunately this function's parameter is a String. To use this in a workflow, we'll have to have
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
   * This method encapsulates the functionality of duplicateText in a way that can be used in a workflow.
   * You provide the input file and output file through this method interface, and a parameterless JavaRunnable
   * that uses those lcms is returned.
   *
   * @param inputFile The file from which to read the input.
   * @param outputFile The file to which to write the output.
   * @param numberTimes The number of times to duplicate the input text.
   * @return A JavaRunnable to represent this action.
   */
  public static JavaRunnable getRunnableDocumentDuplicator(File inputFile, File outputFile, Integer numberTimes) {
    // Since JavaRunnable is a one-method interface, we can use lambdas to write this very succinctly!
    // The returned lambda will call the below code from its run() method.
    return () -> {
      // Check validity of input and output lcms
      if (!inputFile.exists() || inputFile.isDirectory()) {
        throw new IOException("Input file does not exist, or is a directory!.");
      }
      outputFile.createNewFile();

      // Handle input
      BufferedReader reader = new BufferedReader(new FileReader(inputFile));
      String textToEcho = reader.readLine();

      // Build the text duplicator
      DocumentDuplicatorExample textDuplicator = new DocumentDuplicatorExample(numberTimes);

      // Run the duplicator
      String result = textDuplicator.duplicateText(textToEcho);

      // Handle output
      FileWriter writer = new FileWriter(outputFile);
      writer.write(result);

      LOGGER.info("We did it!");
    };
  }

  /**
   * Now, to use this in a workflow, all we'll have to do is call:
   *
   * val duplicatorJob = JavaJobWrapper.wrapJavaFunction(
   *     DocumentDuplicatorExample.getRunnableDocumentDuplicator(fileIn, fileOut, n))
   */
}
