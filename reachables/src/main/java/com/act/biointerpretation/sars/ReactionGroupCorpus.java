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

package com.act.biointerpretation.sars;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a corpus of reaction groups. This should serve as the intermediate format between pipelines that group
 * reactions together, i.e. by sequence clustering, RO groups, LibMCS clustering, or any other method,
 * and pipelines that characterize those grouped reactions with SARs and generalized ROs.
 */
public class ReactionGroupCorpus implements Iterable<ReactionGroup> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty
  private List<ReactionGroup> reactionGroups;

  public ReactionGroupCorpus() {
    reactionGroups = new ArrayList<>();
  }

  public ReactionGroupCorpus(Collection<ReactionGroup> groups) {
    reactionGroups = new ArrayList<>(groups);
  }

  @Override
  public Iterator<ReactionGroup> iterator() {
    return reactionGroups.iterator();
  }

  public void printToJsonFile(File outputFile) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
      OBJECT_MAPPER.writeValue(writer, this);
    }
  }

  public void addGroup(ReactionGroup group) {
    reactionGroups.add(group);
  }

  /**
   * Read a ReactionGroupCorpus corpus from file.
   *
   * @param corpusFile The file to read.
   * @return The ReactionGroupCorpus.
   * @throws IOException
   */
  public static ReactionGroupCorpus loadFromJsonFile(File corpusFile) throws IOException {
    return OBJECT_MAPPER.readValue(corpusFile, ReactionGroupCorpus.class);
  }

  /**
   * Read a ReactionGroupCorpus from a text file.
   * File should contain DB name on the first line.
   * Thereafter each line should have one group of reactions, written as a tab separated list of reaction IDs
   */
  public static ReactionGroupCorpus loadFromTextFile(File corpusFile) throws IOException {
    ReactionGroupCorpus corpus = new ReactionGroupCorpus();

    try (BufferedReader reader = new BufferedReader(new FileReader(corpusFile))) {
      String dbName = reader.readLine();

      String line;
      while ((line = reader.readLine()) != null) {
        List<String> fields = Arrays.asList(line.split(","));
        ReactionGroup group = new ReactionGroup(fields.get(0), dbName);
        for (String idString : fields.subList(1, fields.size())) {
          group.addReactionId(Long.parseLong(idString));
        }
        corpus.addGroup(group);
      }
    }

    return corpus;
  }
}
