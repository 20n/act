package com.act.biointerpretation.sars;

import act.shared.Reaction;
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
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * A SAR that accepts only single substrates which contain a particular substructure.
 */
public class OneSubstrateSubstructureSar implements Sar {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateSubstructureSar.class);
  private static final String INCHI_SETTINGS = "inchi:AuxNone";
  private static final String PRINT_FAILURE = "FAILED_TO_PRINT_SAR";
  private static final MolSearchOptions DEFAULT_STRICT_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);

  /**
   *
   */
  static {
    // The suggested setting for substructure searching
    DEFAULT_STRICT_OPTIONS.setStereoModel(SearchConstants.STEREO_MODEL_COMPREHENSIVE);
    // Incorporates stereo info but allows non-specific structure to match specific structure
    DEFAULT_STRICT_OPTIONS.setStereoSearchType(SearchConstants.STEREO_SPECIFIC);
    DEFAULT_STRICT_OPTIONS.setTimeoutLimitMilliseconds(1000);
  }

  private Molecule substructure;
  private MolSearch searcher;

  /**
   * For Json reading.
   */
  private OneSubstrateSubstructureSar() {
    searcher = new MolSearch();
    searcher.setSearchOptions(DEFAULT_STRICT_OPTIONS);
  }

  public OneSubstrateSubstructureSar(Molecule substructure, MolSearchOptions searchOptions) {
    this();
    this.substructure = substructure;
    searcher.setQuery(substructure);
  }


  public OneSubstrateSubstructureSar(Molecule substructure) {
    this(substructure, DEFAULT_STRICT_OPTIONS);
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

  public static class Builder implements SarBuilder {

    private final DbAPI dbApi;
    private final McsCalculator mcsCalculator;

    public Builder(DbAPI dbApi, McsCalculator mcsCalculator) {
      this.dbApi = dbApi;
      this.mcsCalculator = mcsCalculator;
    }

    @Override
    public Sar buildSar(List<Reaction> reactions) throws MolFormatException {
      if (!DbAPI.areAllOneSubstrate(reactions)) {
        throw new MolFormatException("Reactions are not all one substrate.");
      }

      List<RxnMolecule> rxnMolecules = dbApi.getRxnMolecules(reactions);
      List<Molecule> substrates = Lists.transform(rxnMolecules, rxn -> rxn.getReactants()[0]);
      Molecule sarMcs = mcsCalculator.getMCS(substrates);
      return new OneSubstrateSubstructureSar(sarMcs);
    }
  }
}
