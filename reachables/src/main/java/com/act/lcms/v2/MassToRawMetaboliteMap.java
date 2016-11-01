package com.act.lcms.v2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;


public class MassToRawMetaboliteMap {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MassToRawMetaboliteMap.class);
  private static final RawMetaboliteKind DEFAULT_KIND = RawMetaboliteKind.FORMULA;

  private NavigableMap<Double, List<RawMetabolite>> massToRawMetaboliteMap;
  private RawMetaboliteKind kind;

  public enum RawMetaboliteKind  {
    INCHI, FORMULA
  }

  public MassToRawMetaboliteMap() {
    massToRawMetaboliteMap = new TreeMap<>();
    kind = DEFAULT_KIND;
  }

  public MassToRawMetaboliteMap(RawMetaboliteKind kind) {
    massToRawMetaboliteMap = new TreeMap<>();
    kind = kind;
  }

  public NavigableMap<Double, List<RawMetabolite>> getMassToMoleculeMap() {
    return massToRawMetaboliteMap;
  }

  public RawMetaboliteKind getKind() {
    return kind;
  }

  public void setKind(RawMetaboliteKind kind) {
    this.kind = kind;
  }

  public void add(Double mass, RawMetabolite rawMetabolite) {
    List<RawMetabolite> matchingMetabolites = massToRawMetaboliteMap.get(mass);
    if (matchingMetabolites == null) {
      matchingMetabolites = new ArrayList<>();
      massToRawMetaboliteMap.put(mass, matchingMetabolites);
    }
    matchingMetabolites.add(rawMetabolite);
  }

  public Map<Double, List<RawMetabolite>> getMassWindow(Double fromKey, Double toKey) {
    return massToRawMetaboliteMap.subMap(fromKey, toKey);
  }

  public Map<Double, List<RawMetabolite>> getMassCenteredWindow(Double center, Double windowSize) {
    return massToRawMetaboliteMap.subMap(center - windowSize / 2, center + windowSize / 2);
  }

  public List<RawMetabolite> getSortedFromCenter(Double center, Double windowSize) {
    Map<Double, List<RawMetabolite>> subMap =  getMassCenteredWindow(center, windowSize);
    Map<Double, List<RawMetabolite>> newMap = new TreeMap<>();
    for (Entry<Double, List<RawMetabolite>> entry: subMap.entrySet()) {
      newMap.put(Math.abs(entry.getKey() - center), entry.getValue());
    }
    List<RawMetabolite> rawMetabolites = new ArrayList<>();
    newMap.values().forEach(rawMetabolites::addAll);
    return rawMetabolites;
  }
}
