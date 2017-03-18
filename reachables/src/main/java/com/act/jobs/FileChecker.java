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

  public static void verifyOrCreateDirectory(File directory) throws IOException {
    if (!directory.exists()) {
      directory.mkdirs();
    }
    if (!directory.isDirectory()) {
      throw new IOException("File path is not a directory: " + directory.getAbsolutePath());
    }
  }
}
