package com.act.lcms.db.io.parser;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TSVParser {
  private List<Map<String, String>> results = null;

  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();

  public void parse(File inFile) throws IOException {
    List<Map<String, String>> results = new ArrayList<>();
    try (CSVParser parser = new CSVParser(new FileReader(inFile), TSV_FORMAT)) {
      Iterator<CSVRecord> iter = parser.iterator();
      while (iter.hasNext()) {
        CSVRecord r = iter.next();
        results.add(r.toMap());
      }
    }
    this.results = results;
  }

  public List<Map<String, String>> getResults() {
    return this.results;
  }


}
