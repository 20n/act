package com.act.biointerpretation.sars;

import java.util.List;

public interface ReactionGroupCharacterizer {

  /**
   * Generates a SAR for the given ReactionGroup, if possible. If a SAR can be generated,
   * return CharacterizedGroup containing the ReactionGroup, the SAR, and the ROs associated.
   *
   * @param group The seq group to characterize.
   * @return The corresponding SAR, if one could be generated, or empty.
   */

  List<CharacterizedGroup> characterizeGroup(ReactionGroup group);
}
