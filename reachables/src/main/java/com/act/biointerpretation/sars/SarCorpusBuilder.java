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
