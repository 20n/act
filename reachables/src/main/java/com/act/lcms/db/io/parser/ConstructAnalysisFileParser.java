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

package com.act.lcms.db.io.parser;

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

  private List<Pair<String, List<ConstructAssociatedChemical>>> constructProducts = new ArrayList<>();

  public void parse(File inFile) throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(inFile))) {

      String line;
      String constructId = null;
      List<ConstructAssociatedChemical> products = null;
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
          products.add(new ConstructAssociatedChemical(chemical, kind));
        }
      }
      // Finish processing anything that's left over.
      if (constructId != null) {
        handleConstructProductsList(constructId, products);
      }
    }
  }

  private void handleConstructProductsList(String construct, List<ConstructAssociatedChemical> products) {
    int step = 0;
    for (int i = products.size() - 1; i >= 0; i--) {
      products.get(i).setIndex(step);
      step++;
    }
    constructProducts.add(Pair.of(construct, products));
  }

  public List<Pair<String, List<ConstructAssociatedChemical>>> getConstructProducts() {
    return constructProducts;
  }

  public static class ConstructAssociatedChemical {
    Integer index;
    String chemical;
    String kind;

    public ConstructAssociatedChemical(String chemical, String kind) {
      this.chemical = chemical;
      this.kind = kind;
    }

    public ConstructAssociatedChemical(Integer index, String chemical, String kind) {
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
