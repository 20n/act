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

package com.act.biointerpretation.mechanisminspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ErosCorpus implements Iterable<Ero> {

  private static final Logger LOGGER = LogManager.getFormatterLogger(ErosCorpus.class);
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String VALIDATION_EROS_FILE_NAME = "validation_eros.json";

  private List<Ero> ros;
  private Map<Integer, Ero> roIdToEroMap;

  public ErosCorpus(List<Ero> ros) {
    this.ros = new ArrayList<>(ros);
    roIdToEroMap = new HashMap<>();
  }

  public ErosCorpus() {
    this(new ArrayList<>());
  }

  public List<Ero> getRos() {
    return ros;
  }

  public void setRos(List<Ero> eros) {
    this.ros = eros;
  }

  /**
   * Loads the mechanistic validation RO corpus from the resources directory.
   *
   * @throws IOException
   */
  public void loadValidationCorpus() throws IOException {
    InputStream erosStream = INSTANCE_CLASS_LOADER.getResourceAsStream(VALIDATION_EROS_FILE_NAME);
    loadCorpus(erosStream);
  }

  /**
   * Loads an RO corpus from the supplied input stream.
   *
   * @param erosStream The input stream to load from.
   * @throws IOException
   */
  public void loadCorpus(InputStream erosStream) throws IOException {
    ErosCorpus erosCorpus = OBJECT_MAPPER.readValue(erosStream, ErosCorpus.class);
    setRos(erosCorpus.getRos());
  }

  /**
   * Get the list of RO ids in this corpus.
   *
   * @return The list of Ids.
   */
  public List<Integer> getRoIds() {
    return ros.stream().map(ro -> ro.getId()).collect(Collectors.toList());
  }

  /**
   * Filter by arbitrary predicate.
   * @filter the predicate to match.
   */
  public void filterCorpus(Predicate<Ero> filter) {
    ros.removeIf(ro -> !filter.test(ro));
  }

  /**
   * Filter corpus to contain only RO ids in the supplied list.
   *
   * @param roIdList The list of relevant ids.
   */
  public void filterCorpusById(List<Integer> roIdList) {
    Set<Integer> roSet = new HashSet<>(roIdList);
    filterCorpus(ro -> roSet.contains(ro.getId()));
  }

  /**
   * Filter corpus to contain only RO with IDs in the supplied file.
   *
   * @param roIdFile A file containing RO ids, one per line.
   */
  public void filterCorpusByIdFile(File roIdFile) throws IOException {
    List<Integer> roIds = getRoIdListFromFile(roIdFile);
    filterCorpusById(roIds);
  }

  /**
   * Builds an ro list from only the ros specified in the given file.
   *
   * @param file A file with one ro id per line.
   * @return List of relevant Eros from the corpus.
   */
  private List<Integer> getRoIdListFromFile(File file) throws IOException {
    List<Integer> roIdList = new ArrayList<>();

    try (BufferedReader eroReader = getErosReader(file)) {

      String roId;
      while ((roId = eroReader.readLine()) != null) {
        String trimmedId = roId.trim();

        if (!trimmedId.equals(roId)) {
          LOGGER.warn("Leading or trailing whitespace found in ro id file.");
        }
        if (trimmedId.equals("")) {
          LOGGER.warn("Blank line detected in ro id file and ignored.");
          continue;
        }

        roIdList.add(Integer.parseInt(trimmedId));
      }
    }

    return roIdList;
  }

  /**
   * Filter corpus to only contain ROs with the given number of substrates.
   *
   * @param count The required number of substrates.
   */
  public void filterCorpusBySubstrateCount(Integer count) {
    filterCorpus(ro -> ro.getSubstrate_count().equals(count));
  }

  /**
   * Filter corpus to only contain ROs with the given number of products.
   *
   * @param count The required number of products.
   */
  public void filterCorpusByProductCount(Integer count) {
    filterCorpus(ro -> ro.getProduct_count().equals(count));
  }

  /**
   * Retain only ROs with a name in this corpus.
   */
  public void retainNamedRos() {
    ros.removeIf(ro -> ro.getName().isEmpty());
  }

  /**
   * Gets the ERO with the given roId from the corpus.
   *
   * @param roId The ro id.
   * @return The Ero.
   */
  public Ero getEro(Integer roId) {
    // If map already has entry for this roId, return it
    Ero result;
    if ((result = roIdToEroMap.get(roId)) != null) {
      return result;
    }

    // Otherwise build map in hopes of finding the correct ID along the way
    for (Ero ro : ros) {
      roIdToEroMap.put(ro.getId(), ro);
    }

    // Now, the ID should be there!
    if ((result = roIdToEroMap.get(roId)) != null) {
      return result;
    }

    // Now, if the ID is not there, throw an exception!
    throw new IllegalArgumentException("Supplied RO ID is not in corpus!");
  }

  /**
   * Gets a reader for the RO ID file.
   *
   * @param erosFile A file containing the RO ids, with one RO ID per line.
   * @return A reader for the list of RO Ids.
   */
  private BufferedReader getErosReader(File erosFile) throws FileNotFoundException {
    FileInputStream erosInputStream = new FileInputStream(erosFile);
    BufferedReader erosReader = new BufferedReader(new InputStreamReader(erosInputStream));
    return erosReader;
  }

  @Override
  public Iterator<Ero> iterator() {
    return getRos().iterator();
  }
}
