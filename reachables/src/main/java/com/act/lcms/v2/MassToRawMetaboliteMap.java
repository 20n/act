package com.act.lcms.v2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * This class holds the API for a {Double mass -> List<RawMetabolite> rawMetabolites} map.
 * Its purpose is to store in memory enumerated lists of structures or formulae and allow for quick lookups.
 * Along with the NavigableMap holding the data structure, an enum defines the expected kind of metabolite.
 * TODO: add support to convert RawMetabolite -> Metabolite once #492 goes through and provide APIs to retrieve
 * Metabolites directly form the map.
 */

public class MassToRawMetaboliteMap {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MassToRawMetaboliteMap.class);

  // The data structure holding the RawMetabolites.
  // A NaviagableMap has very convenient properties (sorted, API for extracting sub-maps) for our use case.
  private NavigableMap<Double, List<RawMetabolite>> massToRawMetaboliteMap;
  private RawMetaboliteKind kind;

  // Sub-class defining the kind of metabolite. Defined at the top-level to avoid storing it in every RawMetabolite.
  public enum RawMetaboliteKind  {
    INCHI, FORMULA
  }

  public MassToRawMetaboliteMap(RawMetaboliteKind kind) {
    this.massToRawMetaboliteMap = new TreeMap<>();
    this.kind = kind;
  }

  public NavigableMap<Double, List<RawMetabolite>> getMassToMoleculeMap() {
    return massToRawMetaboliteMap;
  }

  public RawMetaboliteKind getKind() {
    return kind;
  }

  /**
   * Add a RawMetabolite to the map
   */
  public void add(RawMetabolite rawMetabolite) {
    Double mass = rawMetabolite.getMonoIsotopicMass();
    List<RawMetabolite> matchingMetabolites = massToRawMetaboliteMap.get(mass);
    if (matchingMetabolites == null) {
      matchingMetabolites = new ArrayList<>();
      massToRawMetaboliteMap.put(mass, matchingMetabolites);
    }
    matchingMetabolites.add(rawMetabolite);
  }

  /**
   * Retrieve all the RawMetabolites within a given mono-isotopic mass window
   */
  public Map<Double, List<RawMetabolite>> getMassWindow(Double minMass, Double maxMass, Boolean inclusive) {
    return massToRawMetaboliteMap.subMap(minMass, inclusive, maxMass, inclusive);
  }

  /**
   * Retrieve all the RawMetabolites within a given centered mono-isotopic mass window
   */
  public Map<Double, List<RawMetabolite>> getMassCenteredWindow(Double center, Double windowSize) {
    // We default to being inclusive of the query bounds
    return getMassWindow(center - windowSize / 2, center + windowSize / 2, true);
  }

  /**
   * Retrieve a sorted list of the RawMetabolites within a given a centered mono-isotopic mass window,
   * ordered by their closeness to the center.
   */
  public List<RawMetabolite> getSortedFromCenter(Double center, Double windowSize) {
    Map<Double, List<RawMetabolite>> subMap =  getMassCenteredWindow(center, windowSize);
    Map<Double, List<RawMetabolite>> newMap = new TreeMap<>();
    subMap.entrySet().stream().forEachOrdered(entry -> newMap.put(Math.abs(entry.getKey() - center), entry.getValue()));
    List<RawMetabolite> rawMetabolites = new ArrayList<>();
    newMap.values().forEach(rawMetabolites::addAll);
    return rawMetabolites;
  }
}
