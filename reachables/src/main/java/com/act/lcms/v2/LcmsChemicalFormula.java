package com.act.lcms.v2;


import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class LcmsChemicalFormula implements ChemicalFormula {

  private static final String PATTERN_STRING = "([A-Z][a-z]?)(\\d*)";

  private static Pattern PATTERN = Pattern.compile(PATTERN_STRING);

  private Map<Element, Integer> elementCounts;
  private String name;

  public LcmsChemicalFormula(String chemicalFormula) {
    fromString(chemicalFormula);
  }

  public LcmsChemicalFormula(Map<Element, Integer> elementCounts) {
    this.elementCounts = elementCounts;
  }

  public LcmsChemicalFormula(Map<Element, Integer> elementCounts, String name) {
    this.elementCounts = elementCounts;
    this.name = name;
  }

  public Map<Element, Integer> getElementCounts() {
    return this.elementCounts;
  }

  public Integer getElementCount(Element element) {
    return elementCounts.getOrDefault(element, 0);
  }

  public Double getMonoIsotopicMass() {
    return elementCounts
        .entrySet()
        .stream()
        .mapToDouble(entry -> entry.getKey().getMass() * entry.getValue())
        .sum();
  }

  public Optional<String> getName() {
    return Optional.ofNullable(this.name);
  }

  @Override
  public boolean equals(Object chemicalFormula) {
    return (chemicalFormula instanceof ChemicalFormula) &&
        getElementCounts().equals(((ChemicalFormula) chemicalFormula).getElementCounts());
  }

  private TreeMap<Element, Integer> getSortedElementCounts() {
    TreeMap<Element, Integer> treeMap = new TreeMap<>(
        new Comparator<Element>() {
          @Override
          public int compare(Element e1, Element e2) {
            if (e1.getSymbol().equals(e2.getSymbol())) {
              return 0;
            } else if (e1.getSymbol().equals("C")) {
              return -1;
            } else if (e2.getSymbol().equals("C")) {
              return 1;
            } else if (e1.getSymbol().equals("H")) {
              return -1;
            } else if (e2.getSymbol().equals("H")) {
              return 1;
            } else {
              return e1.getSymbol().compareTo(e2.getSymbol());
            }
          }
        }
    );
    treeMap.putAll(elementCounts);
    return treeMap;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<Element, Integer> entry: getSortedElementCounts().entrySet()) {
      builder.append(entry.getKey().getSymbol());
      Integer count = entry.getValue();
      if (count > 1) {
        builder.append(count.toString());
      }
    }
    return builder.toString();
  }

  public void fromString(String formulaString) {
    elementCounts = new HashMap<>();
    Matcher matches = PATTERN.matcher(formulaString);
    while (matches.find()) {
      Element element = new LcmsElement(matches.group(1));
      Integer count = (matches.group(2).equals("")) ? 1 : Integer.parseInt(matches.group(2));
      elementCounts.put(element, count);
    }
  }
}
