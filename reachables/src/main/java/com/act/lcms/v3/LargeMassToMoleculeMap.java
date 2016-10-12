package com.act.lcms.v3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;


public class LargeMassToMoleculeMap {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LargeMassToMoleculeMap.class);

  private NavigableMap<Float, List<NamedMolecule>> massToMoleculeMap;

  public LargeMassToMoleculeMap() {
    massToMoleculeMap = new TreeMap<>();
  }

  public NavigableMap<Float, List<NamedMolecule>> getMassToMoleculeMap() {
    return massToMoleculeMap;
  }

  public void add(Float mass, NamedMolecule molecule) {
    List<NamedMolecule> matchingMolecules = massToMoleculeMap.get(mass);
    LOGGER.info("Getting matching molecules for mass %f", mass);
    if (matchingMolecules == null) {
      matchingMolecules = new ArrayList<>();
      massToMoleculeMap.put(mass, matchingMolecules);
    }
    matchingMolecules.add(molecule);
    LOGGER.info("Final set");
    LOGGER.info(matchingMolecules.toString());
  }

  public Map<Float, List<NamedMolecule>> getMassWindow(Float fromKey, Float toKey) {
    return massToMoleculeMap.subMap(fromKey, toKey);
  }

  public Map<Float, List<NamedMolecule>> getMassCenteredWindow(Float center, Float windowSize) {
    return massToMoleculeMap.subMap(center - windowSize / 2, center + windowSize / 2);
  }

  public List<NamedMolecule> getSortedFromCenter(Float center, Float windowSize) {
    Map<Float, List<NamedMolecule>> subMap =  getMassCenteredWindow(center, windowSize);
    Map<Float, List<NamedMolecule>> newMap = new TreeMap<>();
    for (Entry<Float, List<NamedMolecule>> entry: subMap.entrySet()) {
      newMap.put(Math.abs(entry.getKey() - center), entry.getValue());
    }
    List<NamedMolecule> l = new ArrayList<>();
    newMap.values().forEach(l::addAll);
    LOGGER.info("Query - center: %f, windowSize: %f.", center, windowSize);
    LOGGER.info("Results:");
    LOGGER.info(l.toString());
    LOGGER.info("Original Submap");
    LOGGER.info(subMap.toString());
    return l;
  }
}
