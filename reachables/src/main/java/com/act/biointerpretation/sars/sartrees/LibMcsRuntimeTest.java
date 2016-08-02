package com.act.biointerpretation.sars.sartrees;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.io.formats.mdl.MolImport;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * Created by gil on 8/1/16.
 */
public class LibMcsRuntimeTest {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LibMcsRuntimeTest.class);
  private static final Integer NUM_SUBSTRATES = 10000;
  private static final Integer ONE_BILLION = 1000000000;

  private static final String PREDICTION_CORPUS = "/mnt/shared-data/Gil/untargetted_metabolomics/predictions/mass_filtered_predictions";
  public static void main(String[] args) throws IOException, InterruptedException {

    File inputCorpusFile = new File(PREDICTION_CORPUS);
    L2PredictionCorpus fullCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(inputCorpusFile);
    LOGGER.info("Number of predictions: %d", fullCorpus.getCorpus().size());

    LibraryMCS libMcs = new LibraryMCS();

    int counter = 0;
    for (String inchi : fullCorpus.getUniqueSubstrateInchis()) {
      try {
        Molecule mol = MolImporter.importMol(inchi, "inchi");
        libMcs.addMolecule(mol);
        counter++;
      } catch (IOException e) {
        LOGGER.info("Cannot import %s:%s", inchi, e.getMessage());
      }
      if (counter > NUM_SUBSTRATES) {
        break;
      }
    }

    LOGGER.info("Running LibMCS search on %d molecules.", NUM_SUBSTRATES);
    Long startTime = System.nanoTime();
    libMcs.search();
    LOGGER.info("Done! Took %f seconds", new Double(System.nanoTime() - startTime) / new Double(ONE_BILLION));
  }
}
