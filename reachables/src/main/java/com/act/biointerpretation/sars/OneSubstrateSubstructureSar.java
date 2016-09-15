package com.act.biointerpretation.sars;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A SAR that accepts only single substrates which contain a particular substructure.
 */
public class OneSubstrateSubstructureSar implements Sar {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateSubstructureSar.class);

  private static final String INCHI_SETTINGS = "inchi:AuxNone,SNon,Woff,DoNotAddH";
  private static final String PRINT_FAILURE = "FAILED_TO_PRINT_SAR";

  /**
   * Search loosely by default to avoid weeding out potentially viable matches.
   */
  private static final MolSearchOptions LOOSE_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  static {
    LOOSE_SEARCH_OPTIONS.setStereoModel(SearchConstants.STEREO_IGNORE);
    LOOSE_SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
    LOOSE_SEARCH_OPTIONS.setTimeoutLimitMilliseconds(1000);
  }

  private Molecule substructure;
  private MolSearch searcher;

  /**
   * For Json reading.
   */
  private OneSubstrateSubstructureSar() {
    searcher = new MolSearch();
    searcher.setSearchOptions(LOOSE_SEARCH_OPTIONS);
  }

  public OneSubstrateSubstructureSar(Molecule substructure, MolSearchOptions searchOptions) {
    this();
    this.substructure = substructure;
    searcher.setQuery(substructure);
  }

  public OneSubstrateSubstructureSar(Molecule substructure) {
    this(substructure, LOOSE_SEARCH_OPTIONS);
  }

  @Override
  public boolean test(List<Molecule> substrates) {
    // This class of SARs is only valid on single-substrate reactions.
    if (substrates.size() != 1) {
      return false;
    }

    // Return true if the searcher finds a match
    searcher.setTarget(substrates.get(0));

    try {
      return searcher.findFirst() != null;
    } catch (SearchException e) {
      // Log error but don't propagate upward. Have never seen this before.
      LOGGER.error("Error on testing substrates with SAR %s", getSubstructureInchi());
      return false;
    }
  }

  @JsonProperty("substructure_inchi")
  private String getSubstructureInchi() {
    try {
      return MolExporter.exportToFormat(substructure, INCHI_SETTINGS);
    } catch (IOException e) {
      LOGGER.error("IOException on exporting sar to inchi, %s", e.getMessage());
      return PRINT_FAILURE;
    }
  }

  /**
   * For JSON reading
   *
   * @param inchi Inchi to set as substructure, as read from json.
   * @throws MolFormatException
   */
  private void setSubstructureInchi(String inchi) throws MolFormatException {
    substructure = MolImporter.importMol(inchi);
    searcher.setQuery(substructure);
  }

  public static class Factory implements SarFactory {

    private final McsCalculator mcsCalculator;

    public Factory(McsCalculator mcsCalculator) {
      this.mcsCalculator = mcsCalculator;
    }

    @Override
    public Sar buildSar(List<RxnMolecule> reactions) {
      if (!DbAPI.areAllOneSubstrate(reactions)) {
        throw new IllegalArgumentException("Reactions are not all one substrate.");
      }

      List<Molecule> substrates = reactions.stream().map(rxn -> rxn.getReactants()[0]).collect(Collectors.toList());
      Molecule sarMcs = mcsCalculator.getMCS(substrates);
      return new OneSubstrateSubstructureSar(sarMcs);
    }
  }
}
