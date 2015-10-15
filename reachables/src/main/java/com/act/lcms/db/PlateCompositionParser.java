package com.act.lcms.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlateCompositionParser {
  // TODO: factor out the well composition tables into a common parser that can be refined by type after some practice.

  private Map<String, String> plateProperties = new HashMap<>();
  private Map<String, Map<Pair<String, String>, String>> compositionTables = new HashMap<>();
  private Map<Pair<String, String>, Pair<Integer, Integer>> coordinatesToIndices = new HashMap<>();

  public void processFile(File inFile) throws IOException {
    try (BufferedReader br = new BufferedReader(new FileReader(inFile))) {
      String line;

      boolean readingCompositionTable = false;
      String compositionTableName = null;
      List<String> compositionTableColumns = null;
      int rowIndexInCompositionTable = 0;
      while ((line = br.readLine()) != null) {

        if (line.startsWith(">>")) {
          // TODO: add max table width based on plate type.
          String[] fields = StringUtils.split(line, "\t");
          readingCompositionTable = true;
          if (fields.length < 2) {
            throw new RuntimeException(String.format("Found malformed composition table header: %s", line));
          }
          compositionTableColumns = Arrays.asList(fields);
          compositionTableName = fields[0].replaceFirst("^>>", "");
          rowIndexInCompositionTable = 0;

        } else if (line.startsWith(">")) {
          String[] fields = StringUtils.split(line, "\t", 2);
          // Found a plate attribute.
          if (fields.length != 2) {
            System.err.format("Too few fields: %s\n", StringUtils.join(fields, ", "));
            System.err.flush();
            throw new RuntimeException(String.format("Found malformed plate attribute line: %s", line));
          }
          plateProperties.put(fields[0].replaceFirst("^>", ""), fields[1]);

        } else if (line.trim().length() == 0) {
          // Assume a blank line terminates a composition table.
          readingCompositionTable = false;
          compositionTableName = null;
          compositionTableColumns = null;
          rowIndexInCompositionTable = 0;

        } else if (readingCompositionTable) {
          // This split method with a very long name preserves blanks and doesn't merge consecutive delimiters.
          String[] fields = StringUtils.splitByWholeSeparatorPreserveAllTokens(line, "\t");
          // The split ^^ preserves blanks, so we can exactly compare the lengths.
          if (fields.length != compositionTableColumns.size()) {
            throw new RuntimeException(
                String.format("Found %d fields where %d were expected in composition table line:\n  '%s'\n",
                    fields.length, compositionTableColumns.size(), line));
          }

          for (int i = 1; i < fields.length; i++) {
            String val = compositionTableColumns.get(i);
            // No need to store empty values;
            if (val == null || val.isEmpty()) {
              continue;
            }
            Pair<String, String> coordinates = Pair.of(fields[0], val);
            coordinatesToIndices.put(coordinates, Pair.of(rowIndexInCompositionTable, i - 1));
            Map<Pair<String, String>, String> thisTable = compositionTables.get(compositionTableName);
            if (thisTable == null) {
              thisTable = new HashMap<>();
              compositionTables.put(compositionTableName, thisTable);
            }
            // TODO: add paranoid check for repeated keys?  Shouldn't be possible unless tables are repeated.
            thisTable.put(coordinates, fields[i]);
          }
          rowIndexInCompositionTable++;
        }
      }
    }
  }

  public Map<String, String> getPlateProperties() {
    return plateProperties;
  }

  public Map<String, Map<Pair<String, String>, String>> getCompositionTables() {
    return compositionTables;
  }

  public Map<Pair<String, String>, Pair<Integer, Integer>> getCoordinatesToIndices() {
    return coordinatesToIndices;
  }
}

