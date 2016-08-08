package com.act.biointerpretation.sars;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class SarCorpusBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarCorpusBuilder.class);

  private final Iterable<ReactionGroup> reactionGroups;
  private final ReactionGroupCharacterizer characterizer;

  public SarCorpusBuilder(Iterable<ReactionGroup> reactionGroups, ReactionGroupCharacterizer characterizer) {
    this.reactionGroups = reactionGroups;
    this.characterizer = characterizer;
  }

  public SarCorpus build() {
    SarCorpus corpus = new SarCorpus();
    int counter = 1;
    for (ReactionGroup group : reactionGroups) {

      List<CharacterizedGroup> characterizations = characterizer.characterizeGroup(group);
      for (CharacterizedGroup characterization : characterizations) {
        corpus.addCharacterizedGroup(characterization);
      }

      LOGGER.info("Processed %d groups, characterized %d so far.", counter, corpus.size());
      counter++;
    }
    return corpus;
  }
}
