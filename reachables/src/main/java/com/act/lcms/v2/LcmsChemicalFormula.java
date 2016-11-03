package com.act.lcms.v2;


import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class LcmsChemicalFormula implements ChemicalFormula {

  // The following pattern matches element + count combinations in a formula string.
  private static final Pattern ELEMENT_COUNT_PATTERN = Pattern.compile("([A-Z][a-z]?)(\\d*)");

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

  private Comparator<Element> getElementComparator(ChemicalFormula formula) {
    if (formula.getElementCount(LcmsCommonElements.CARBON.getElement()) > 0) {
      return (Element e1, Element e2) -> {
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
      };
    } else {
      return (Element e1, Element e2) -> e1.getSymbol().compareTo(e2.getSymbol());
    }
  }

  private TreeMap<Element, Integer> getSortedElementCounts() {
    TreeMap<Element, Integer> treeMap = new TreeMap<>(getElementComparator(this));
    treeMap.putAll(elementCounts);
    return treeMap;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<Element, Integer> entry : getSortedElementCounts().entrySet()) {
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
    Matcher matches = ELEMENT_COUNT_PATTERN.matcher(formulaString);
    // Example: in "C8H9NO2", there will be 4 matches for this pattern, each of which having two groups.
    // First match: "C8", with group 1 being "C" and group 2 being "8"
    // Second match: "H9", group 1 is "H", group 2 is "9"
    // Third match: "N", group 1 is "N", group 2 is "" (empty string)
    // Fourth match: "O2", group 1 is "O" and group 2 is "2"
    while (matches.find()) {
      Element element = new LcmsElement(matches.group(1));
      Integer count = (matches.group(2).equals("")) ? 1 : Integer.parseInt(matches.group(2));
      elementCounts.put(element, count);
    }
  }
}
