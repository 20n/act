package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.sars.NoSar;
import com.act.biointerpretation.sars.Sar;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class L2Expander implements Serializable {
  private static final long serialVersionUID = 5846728290095735668L;

  private static transient final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);

  private static transient final String INCHI_IMPORT_SETTINGS = "inchi";

  // This SAR accepts every substrate
  @JsonIgnore
  protected static transient final Sar NO_SAR = new NoSar();

  private PredictionGenerator generator;

  public abstract Iterable<PredictionSeed> getPredictionSeeds();

  public L2Expander(PredictionGenerator generator) {
    this.generator = generator;
  }

  public L2PredictionCorpus getPredictions(OutputStream outputStream) {
    L2PredictionCorpus result = new L2PredictionCorpus();

    OutputStreamWriter writer = null;
    if (outputStream != null) {
      writer = new OutputStreamWriter(outputStream);
    }

    ObjectMapper objectMapper = new ObjectMapper();

    for (PredictionSeed seed : getPredictionSeeds()) {
      // Apply reactor to substrate if possible
      try {
        List<L2Prediction> results = generator.getPredictions(seed);
        if (writer != null) {
          try {
            String resultJson = objectMapper.writeValueAsString(results);
            writer.write(resultJson);
            writer.write("\n");
            writer.flush();
          } catch (Exception e) {
            LOGGER.error("Caught exception when writing progress, skipping: %s", e.getMessage());
          }
        }
        result.addAll(results);
        // If there is an error on a certain RO, metabolite pair, we should log the error, but the expansion may
        // produce some valid results, so no error is thrown.
      } catch (ReactionException e) {
        LOGGER.error("ReactionException on getPredictions. %s", e.getMessage());
      } catch (IOException e) {
        LOGGER.error("IOException during prediction generation. %s", e.getMessage());
      }
    }

    return result;
  }

  /**
   * Filters the RO list to keep only those ROs with n substrates.
   *
   * @param roList The initial list of Ros.
   * @param n The number of substrates to screen for.
   * @return The subset of the ros which have exactly n substrates.
   */
  protected List<Ero> getNSubstrateRos(List<Ero> roList, int n) {
    List<Ero> nSubstrateReactions = new ArrayList<>();

    for (Ero ro : roList) {
      if (ro.getSubstrate_count() == n) {
        nSubstrateReactions.add(ro);
      }
    }

    LOGGER.info("Proceeding with %d %d substrate ROs.", nSubstrateReactions.size(), n);
    return nSubstrateReactions;
  }

  /**
   * This function imports a given inchi to a Molecule.
   *
   * @param inchi Input inchi.
   * @return The resulting Molecule.
   * @throws MolFormatException
   */
  protected Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
  }

}
