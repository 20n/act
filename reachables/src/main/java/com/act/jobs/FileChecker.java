package com.act.jobs;

import java.io.File;
import java.io.IOException;

public class FileChecker {

  public static void verifyInputFile(File inputFile) throws IOException {
    if (!inputFile.exists()) {
      throw new IOException("Input file " + inputFile.getAbsolutePath() + " does not exist.");
    }
    if (inputFile.isDirectory()) {
      throw new IOException("Input file " + inputFile.getAbsolutePath() + " is a directory.");
    }
  }

  public static void verifyAndCreateOutputFile(File outputFile) throws IOException {
    if (outputFile.isDirectory()) {
      throw new IOException("Input file " + outputFile.getAbsolutePath() + " is a directory.");
    }
    try {
      outputFile.createNewFile();
    } catch (IOException e) {
      throw new IOException("createNewFile() on file " + outputFile.getAbsolutePath() + " failed: " + e.getMessage());
    }
  }
}
