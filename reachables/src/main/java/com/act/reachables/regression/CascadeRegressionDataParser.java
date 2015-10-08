package com.act.reachables.regression;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CascadeRegressionDataParser {
  public static final String REGRESSION_SUITE_ENTRY_DELIMITER = ">>>";

  public static final String REGRESSION_SUITE_FIELD_DELIMITER = "\t";
  public static final String REGRESSION_SUITE_NESTED_FIELD_DELIMITER = ";"; // For cofactor lists.

  public static class CascadeChemical {
    private String name;
    private String inchi;

    public CascadeChemical(String name, String inchi) {
      this.name = name;
      this.inchi = inchi;
    }

    public String getName() {
      return name;
    }

    public String getInchi() {
      return inchi;
    }
  }

  public static class CascadeRegressionStep {
    Integer stepNumber;
    String easyDescription;
    List<CascadeChemical> substrates;
    List<CascadeChemical> products;
    List<String> cofactors;
    List<Map<String, String>> sequenceEntries;

    public CascadeRegressionStep(Integer stepNumber, String easyDescription, List<CascadeChemical> substrates,
                                 List<CascadeChemical> products, List<String> cofactors,
                                 List<Map<String, String>> sequenceEntries) {
      this.stepNumber = stepNumber;
      this.easyDescription = easyDescription;
      this.substrates = substrates;
      this.products = products;
      this.cofactors = cofactors;
      this.sequenceEntries = sequenceEntries;
    }

    public Integer getStepNumber() {
      return stepNumber;
    }

    public String getEasyDescription() {
      return easyDescription;
    }

    public List<CascadeChemical> getSubstrates() {
      return substrates;
    }

    public List<CascadeChemical> getProducts() {
      return products;
    }

    public List<String> getCofactors() {
      return cofactors;
    }

    public List<Map<String, String>> getSequenceEntries() {
      return sequenceEntries;
    }
  }

  public CascadeRegressionStep processRegressionTextData(List<String> textData) {
    // Ignore incomplete or null entries.
    if (textData == null || textData.size() < 1) {
      return null;
    }

    Integer stepNumber = null;
    String easyDescription = null;
    List<CascadeChemical> substrates = new ArrayList<>(1);
    List<CascadeChemical> products = new ArrayList<>(1);
    List<String> cofactors = new ArrayList<>();
    List<String> sequenceFields = new ArrayList<>();
    List<Map<String, String>> sequenceEntries = new ArrayList<>();

    boolean inSequencesTable = false;
    for (String line : textData) {
      if (line.isEmpty()) {
        continue;
      }

      // N.B.: the split APIs in String and StringUtils are weeeird.  Use -1 to ensure that empty fields are preserved.
      String[] fields = line.split(REGRESSION_SUITE_FIELD_DELIMITER, -1);
      if (!inSequencesTable) {
        if (fields.length < 2) {
          System.err.format("Not enough fields found for line: %s\n", line);
          // TODO: it's probably best to bail here.
          continue;
        }

        switch (fields[0]) {
          case ">>>step":
            stepNumber = Integer.valueOf(fields[1]);
            // There are some spurious 0xA0 characters in there...
            // With help from http://stackoverflow.com/questions/8519669/replace-non-ascii-character-from-string
            // (all other replacement methods failed!)
            easyDescription = fields[2].replaceAll("[^\\x00-\\x7F]", " ");
            break;
          case "product":
            products.add(new CascadeChemical(fields[1], fields[2].replaceAll("\"", "")));
            break;
          case "substrate":
            substrates.add(new CascadeChemical(fields[1], fields[2].replaceAll("\"", "")));
            break;
          case "cofactors":
            cofactors.addAll(Arrays.asList(StringUtils.split(fields[1], REGRESSION_SUITE_NESTED_FIELD_DELIMITER)));
            break;
          case "ec":
            sequenceFields.addAll(Arrays.asList(fields)); // "ec" line is header for subsequent TSV.
            inSequencesTable = true;
            break;
          default:
            System.err.format("Unrecognized regression data field %s, ignoring\n", fields[0]);
            break;
        }
      } else {
        if (fields.length != sequenceFields.size()) {
          System.err.format("Expected %d fields but found %d for line\n",
              sequenceFields.size(), fields.length);
          System.err.format("    ->%s\n", String.join(" :: ", Arrays.asList(fields)));

          continue;
        }
        Map<String, String> sequenceEntry = new HashMap<>(fields.length);
        for (int i = 0; i < fields.length; i++) {
          if (fields[i] != null && !fields[i].isEmpty()) {
            sequenceEntry.put(sequenceFields.get(i), fields[i]);
          }
        }
        sequenceEntries.add(sequenceEntry);
      }
    }

    return new CascadeRegressionStep(stepNumber, easyDescription, substrates, products, cofactors, sequenceEntries);
  }

  public static void main(String[] args) throws Exception {
    CascadeRegressionDataParser parser = new CascadeRegressionDataParser();

    File regressionDataFile = new File(args[0]);
    // TODO: file error checking.
    try (BufferedReader br = new BufferedReader(new FileReader(regressionDataFile))) {
      List<String> regressionSuiteTextEntry = new ArrayList<>();
      String line = null;

      CascadeRegressionStep step;
      while ((line = br.readLine()) != null) {
        if (line.startsWith(REGRESSION_SUITE_ENTRY_DELIMITER)) {
          step = parser.processRegressionTextData(regressionSuiteTextEntry);
          regressionSuiteTextEntry = new ArrayList<>();
          if (step != null) {
            System.out.format("%d: %s\n", step.getStepNumber(), step.getEasyDescription());
          }
        }
        regressionSuiteTextEntry.add(line);
      }
      // Process whatever is left from the regression suite file.
      step = parser.processRegressionTextData(regressionSuiteTextEntry);
      if (step != null) {
        System.out.format("%d: %s\n", step.getStepNumber(), step.getEasyDescription());
      }
    }
  }
}
