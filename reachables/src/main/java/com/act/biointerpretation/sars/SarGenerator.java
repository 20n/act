package com.act.biointerpretation.sars;

import java.io.IOException;
import java.util.Optional;

public interface SarGenerator {
  /**
   * Generates a SAR for the given SeqGroup.  If no SAR can be generated, returns an empty optional.
   *
   * @param group The seq group to characterize.
   * @return The corresponding SAR, if one could be generated.
   */
  Optional<Sar> getSar(SeqGroup group) throws IOException;
}
