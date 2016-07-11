package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class OneSubstrateSubstructureSar implements Sar {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateSubstructureSar.class);
  private static final String INCHI_SETTINGS = "inchi:AuxNone";
  private static final String PRINT_FAILURE = "FAILED_TO_PRINT_SAR";
  private static final MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  static {
    searchOptions.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
    searchOptions.setStereoSearchType(SearchConstants.STEREO_EXACT);
  }

  Molecule substructure;
  MolSearch searcher;

  public OneSubstrateSubstructureSar(Molecule substructure) {
    this.substructure = substructure;
    searcher = new MolSearch();
    searcher.setSearchOptions(searchOptions);
    searcher.setQuery(substructure);
  }

  @Override
  public boolean test(List<Molecule> substrates) throws SearchException {
    if (substrates.size() != 1) {
      return false;
    }

    searcher.setTarget(substrates.get(0));
    return searcher.getMatchCount() > 0;
  }

  @JsonProperty("substructure_inchi")
  private String getSubstructureInchi() {
    try {
      return MolExporter.exportToFormat(substructure, INCHI_SETTINGS);
    } catch (IOException e) {
      LOGGER.error("Exception on exporting sar to inchi, %s", e.getMessage());
      return PRINT_FAILURE;
    }
  }
}
