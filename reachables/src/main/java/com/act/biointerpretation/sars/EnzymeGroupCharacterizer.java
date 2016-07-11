package com.act.biointerpretation.sars;

import java.util.Optional;
import java.util.Set;

public interface EnzymeGroupCharacterizer {
  /**
   * Generates a SAR for the given SeqGroup, if possible. If a SAR can be generated,
   * return CharacterizedGroup containing the SeqGroup, the SAR, and the ROs associated.
   *
   * @param group The seq group to characterize.
   * @return The corresponding SAR, if one could be generated.
   */
  Optional<CharacterizedGroup> getSar(SeqGroup group);
}
