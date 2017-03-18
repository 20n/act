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

package com.act.biointerpretation.desalting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class DesaltingROCorpus {

  private static final String DESALTER_CONSTANTS_FILE_PATH = "desalter_constants.txt";
  private static final String DESALTING_ROS_FILE_PATH = "desalting_ros.json";
  private final Class INSTANCE_CLASS_LOADER = getClass();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @JsonProperty("title")
  private String title;

  @JsonProperty("ros")
  private List<DesaltingRO> ros;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<DesaltingRO> getRos() {
    return ros;
  }

  public void setRos(List<DesaltingRO> ros) {
    this.ros = ros;
  }

  /**
   * This function returns a corpus Desalting ROs
   * @return DesaltingROCorpus object
   * @throws IOException
   */
  public DesaltingROCorpus getDesaltingROS() throws IOException {
    InputStream desaltingROSStream = INSTANCE_CLASS_LOADER.getResourceAsStream(DESALTING_ROS_FILE_PATH);
    DesaltingROCorpus corpus = OBJECT_MAPPER.readValue(desaltingROSStream, DesaltingROCorpus.class);
    return corpus;
  }

  /**
   * This function returns a reader file handle to the list of ROs files
   * @return BufferedReader reader
   * @throws FileNotFoundException
   */
  public BufferedReader getDesalterConstantsReader() throws FileNotFoundException {
    InputStream desalterConstantsStream = INSTANCE_CLASS_LOADER.getResourceAsStream(DESALTER_CONSTANTS_FILE_PATH);
    BufferedReader desaltConstantsReader = new BufferedReader(new InputStreamReader(desalterConstantsStream));
    return desaltConstantsReader;
  }
}
