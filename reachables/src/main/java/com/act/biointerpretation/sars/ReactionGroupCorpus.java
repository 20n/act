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

public class ReactionGroupCorpus implements Iterable<ReactionGroup> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  static {
    OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @JsonProperty
  private List<ReactionGroup> reactionGroups;

  public ReactionGroupCorpus () {
    reactionGroups = new ArrayList<>();
  }

  public ReactionGroupCorpus (Collection<ReactionGroup> groups) {
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
   * File should contain one group of reactions per line, written as a tab separated list of reaction IDs
   */
  public static ReactionGroupCorpus loadFromTextFile(File corpusFile) throws IOException {
    ReactionGroupCorpus corpus = new ReactionGroupCorpus();

    try (BufferedReader reader = new BufferedReader(new FileReader(corpusFile))) {
      while (reader.ready()) {
        String line = reader.readLine();
        List<String> fields = Arrays.asList(line.split(","));
        ReactionGroup group = new ReactionGroup(fields.get(0));
        for (String idString : fields.subList(1, fields.size())) {
          group.addReactionId(Long.parseLong(idString));
        }
        corpus.addGroup(group);
      }
    }

    return corpus;
  }
}
