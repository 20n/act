package com.act.biointerpretation.sars;

import chemaxon.jchem.db.DatabaseSearchException;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public interface Sar {

  /**
   * Test a given list of substrates to see whether this SAR will accept them.
   *
   * @param substrates The substrates of a chemical reaction.
   * @return True if this SAR can act on the given substrates.
   */
  boolean test(List<Molecule> substrates) throws DatabaseSearchException, SQLException, IOException, SearchException;

  String printSar();
}
