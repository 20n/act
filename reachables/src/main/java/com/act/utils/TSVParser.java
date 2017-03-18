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
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TSVParser {
  private List<Map<String, String>> results = null;
  Map<String, Integer> headerMap = null;

  public static final CSVFormat TSV_FORMAT = CSVFormat.newFormat('\t').
      withRecordSeparator('\n').withQuote('"').withIgnoreEmptyLines(true).withHeader();


  public void parse(File file) throws IOException {
    parse(new FileInputStream(file));
  }

  public void parse(InputStream inStream) throws IOException {
    List<Map<String, String>> results = new ArrayList<>();
    try (CSVParser parser = new CSVParser(new InputStreamReader(inStream), TSV_FORMAT)) {
      headerMap = parser.getHeaderMap();
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

  public Map<String, Integer> getHeaderMap() { return this.headerMap; }

  public List<String> getHeader() {
    List<String> header = new ArrayList<>(this.headerMap.size());
    for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
      header.add(entry.getValue(), entry.getKey());
    }
    return header;
  }
}
