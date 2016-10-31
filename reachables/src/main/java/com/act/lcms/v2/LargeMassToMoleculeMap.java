package com.act.lcms.v2;

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

  private NavigableMap<Double, List<RawMetabolite>> massToMoleculeMap;

  public LargeMassToMoleculeMap() {
    massToMoleculeMap = new TreeMap<>();
  }

  public NavigableMap<Double, List<RawMetabolite>> getMassToMoleculeMap() {
    return massToMoleculeMap;
  }

  public void add(Double mass, RawMetabolite molecule) {
    List<RawMetabolite> matchingMolecules = massToMoleculeMap.get(mass);
    if (matchingMolecules == null) {
      matchingMolecules = new ArrayList<>();
      massToMoleculeMap.put(mass, matchingMolecules);
    }
    matchingMolecules.add(molecule);
  }

  public Map<Double, List<RawMetabolite>> getMassWindow(Double fromKey, Double toKey) {
    return massToMoleculeMap.subMap(fromKey, toKey);
  }

  public Map<Double, List<RawMetabolite>> getMassCenteredWindow(Double center, Double windowSize) {
    return massToMoleculeMap.subMap(center - windowSize / 2, center + windowSize / 2);
  }

  public List<RawMetabolite> getSortedFromCenter(Double center, Double windowSize) {
    Map<Double, List<RawMetabolite>> subMap =  getMassCenteredWindow(center, windowSize);
    Map<Double, List<RawMetabolite>> newMap = new TreeMap<>();
    for (Entry<Double, List<RawMetabolite>> entry: subMap.entrySet()) {
      newMap.put(Math.abs(entry.getKey() - center), entry.getValue());
    }
    List<RawMetabolite> l = new ArrayList<>();
    newMap.values().forEach(l::addAll);
    return l;
  }
}
