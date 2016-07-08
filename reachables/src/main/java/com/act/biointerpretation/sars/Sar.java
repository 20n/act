package com.act.biointerpretation.sars;

import act.shared.Chemical;

import java.util.List;

public interface Sar {

  /**
   * Test a given list of substrates to see whether this SAR will accept them.
   *
   * @param substrates The substrates of a chemical reaction.
   * @return True if this SAR can act on the given substrates.
   */
  boolean test(List<Chemical> substrates);
}
