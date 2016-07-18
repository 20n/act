package com.act.biointerpretation.sars;

import java.util.List;

public interface EnzymeGroupCharacterizer {

  /**
   * Generates a SAR for the given SeqGroup, if possible. If a SAR can be generated,
   * return CharacterizedGroup containing the SeqGroup, the SAR, and the ROs associated.
   *
   * @param group The seq group to characterize.
   * @return The corresponding SAR, if one could be generated, or empty.
   */
  List<CharacterizedGroup> characterizeGroup(SeqGroup group);
}
