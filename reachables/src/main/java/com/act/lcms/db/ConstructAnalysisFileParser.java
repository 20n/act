package com.act.lcms.db;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConstructAnalysisFileParser {
  public static final String PRODUCT_KIND_SEPARATOR = "\t";
  public static final Pattern CONSTRUCT_DESIGNATOR_PATTERN = Pattern.compile("^>(.*)$");
  public static final String INTERMEDIATE_PRODUCT_DESIGNATOR = "INTERMEDIATE";

  private List<Pair<String, List<ConstructProductStep>>> constructProducts = new ArrayList<>();

  public void parse(File inFile) throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(inFile))) {

      String line;
      String constructId = null;
      List<ConstructProductStep> products = null;
      while ((line = reader.readLine()) != null) {
        Matcher matcher = CONSTRUCT_DESIGNATOR_PATTERN.matcher(line);
        if (matcher.matches()) {
          if (constructId != null) {
            handleConstructProductsList(constructId, products);
          }
          constructId = matcher.group(1).trim();
          products = new ArrayList<>();
        } else {
          if (constructId == null || products == null) {
            throw new RuntimeException("Found construct product step line without a pre-defined construct");
          }
          String[] fields = StringUtils.splitByWholeSeparatorPreserveAllTokens(line, PRODUCT_KIND_SEPARATOR);
          if (fields.length != 2) {
            System.err.format("Skipping line with unexpected number of fields (%d): %s\n", fields.length, line);
            continue;
          }
          String chemical = fields[0];
          String kind = fields[1];
          products.add(new ConstructProductStep(chemical, kind));
        }
      }
      // Finish processing anything that's left over.
      if (constructId != null) {
        handleConstructProductsList(constructId, products);
      }
    }
  }

  private void handleConstructProductsList(String construct, List<ConstructProductStep> products) {
    int step = 0;
    for (int i = products.size() - 1; i >= 0; i--) {
      products.get(i).setIndex(step);
      step++;
    }
    constructProducts.add(Pair.of(construct, products));
  }

  public List<Pair<String, List<ConstructProductStep>>> getConstructProducts() {
    return constructProducts;
  }

  public static class ConstructProductStep {
    Integer index;
    String chemical;
    String kind;

    public ConstructProductStep(String chemical, String kind) {
      this.chemical = chemical;
      this.kind = kind;
    }

    public ConstructProductStep(Integer index, String chemical, String kind) {
      this.index = index;
      this.chemical = chemical;
      this.kind = kind;
    }

    public Integer getIndex() {
      return index;
    }

    protected void setIndex(Integer index) {
      this.index = index;
    }

    public String getChemical() {
      return chemical;
    }

    public String getKind() {
      return kind;
    }
  }

}
