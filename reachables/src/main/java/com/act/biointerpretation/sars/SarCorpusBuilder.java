package com.act.biointerpretation.sars;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class SarCorpusBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarCorpus.class);

  private final Iterable<ReactionGroup> enzymeGroups;
  private final EnzymeGroupCharacterizer characterizer;

  public SarCorpusBuilder(Iterable<ReactionGroup> enzymeGroups, EnzymeGroupCharacterizer characterizer) {
    this.enzymeGroups = enzymeGroups;
    this.characterizer = characterizer;
  }

  public SarCorpus build() {
    SarCorpus corpus = new SarCorpus();
    int counter = 1;
    for (ReactionGroup group : enzymeGroups) {
      List<CharacterizedGroup> characterizations = characterizer.characterizeGroup(group);
      for (CharacterizedGroup characterization : characterizations) {
        corpus.addCharacterizedGroup(characterization);
      }
      if (counter % 1 == 0) {
        LOGGER.info("Processed %d groups, characterized %d so far.", counter, corpus.size());
      }
      counter++;
    }
    return corpus;
  }
}
