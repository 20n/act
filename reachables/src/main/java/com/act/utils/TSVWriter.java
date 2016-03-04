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
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();

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
