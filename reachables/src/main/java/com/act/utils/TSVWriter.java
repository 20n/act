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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// TODO: move this and TSVParser to an analysis utility class.  No reason for them to live deep in package hierarchies.
public class TSVWriter<K, V> implements AutoCloseable {

  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true);
  private List<K> header;
  private CSVPrinter printer;

  public TSVWriter(List<K> header) {
    this.header = header;
  }

  public void open(File f) throws IOException {
    String[] headerStrings = new String[header.size()];
    for (int i = 0; i < header.size(); i++) {
      headerStrings[i] = header.get(i).toString();
    }
    printer = new CSVPrinter(new FileWriter(f), TSV_FORMAT.withHeader(headerStrings));
  }

  public void open(File f, Boolean append) throws IOException {
    if (!append) {
      open(f);
    } else {
      printer = new CSVPrinter(new FileWriter(f, true), TSV_FORMAT);
    }
  }

  @Override
  public void close() throws IOException {
    if (printer != null) {
      printer.close();
      printer = null;
    }
  }

  public void append(Map<K, V> row) throws IOException {
    List<V> vals = new ArrayList<>(header.size());
    for (K field : header) {
      vals.add(row.get(field));
    }
    printer.printRecord(vals);
  }

  public void append(List<Map<K, V>> rows) throws IOException {
    for (Map<K, V> row : rows) {
      append(row);
    }
    printer.flush();
  }

  public void flush() throws IOException {
    printer.flush();
  }
}
