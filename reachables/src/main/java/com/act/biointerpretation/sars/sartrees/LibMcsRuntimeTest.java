package com.act.biointerpretation.sars.sartrees;

import chemaxon.clustering.ClusteringException;
import chemaxon.clustering.InvalidLicenseKeyException;
import chemaxon.clustering.LibraryMCS;
import chemaxon.clustering.MBaseNode;
import chemaxon.clustering.MGraph;
import chemaxon.clustering.SphereExclusion;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.marvin.io.formats.mdl.MolImport;
import chemaxon.struc.Molecule;
import chemaxon.clustering.Ward;
import com.act.biointerpretation.l2expansion.L2InchiCorpus;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.chemaxon.clustering.wards.LanceWilliamsMerges;
import com.chemaxon.search.mcs.SearchMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by gil on 8/1/16.
 */
public class LibMcsRuntimeTest {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LibMcsRuntimeTest.class);
  private static final Integer NUM_SUBSTRATES = 100000;
  private static final Integer ONE_BILLION = 1000000000;

  private static final String PREDICTION_CORPUS = "/mnt/shared-data/Gil/untargetted_metabolomics/predictions/7ro_predictions_by_ro/predictions.33";
  private static final String POSITIVE_INCHIS = "/mnt/shared-data/Gil/untargetted_metabolomics/7RO_L2_PA_NEG0/lcms_positives";
  private static final String OUTPUT_INCHIS = "/mnt/shared-data/Gil/untargetted_metabolomics/tmp_inchis";

  public static void main(String[] args)
      throws IOException, InterruptedException, InvalidLicenseKeyException, ClusteringException, SQLException {

    File inputCorpusFile = new File(PREDICTION_CORPUS);
    L2PredictionCorpus fullCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(inputCorpusFile);
    LOGGER.info("Number of predictions: %d", fullCorpus.getCorpus().size());

    L2InchiCorpus fullUniqueSubstrates = new L2InchiCorpus(fullCorpus.getUniqueSubstrateInchis());
    fullUniqueSubstrates.writeToFile(new File("/mnt/shared-data/Gil/untargetted_metabolomics/tmp_inchis"));

    L2InchiCorpus positiveInchis = new L2InchiCorpus();
    positiveInchis.loadCorpus(new File(POSITIVE_INCHIS));
    Set<String> inchiSet = new HashSet<>();
    inchiSet.addAll(positiveInchis.getInchiList());

    L2PredictionCorpus positiveCorpus = fullCorpus.applyFilter(prediction -> inchiSet.contains(prediction.getProductInchis().get(0)));
    L2InchiCorpus positiveUniqueSubstrates = new L2InchiCorpus(positiveCorpus.getUniqueSubstrateInchis());
    positiveUniqueSubstrates.writeToFile(new File(OUTPUT_INCHIS));
    LibraryMCS.main(new String[]{});

    System.exit(0);

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

    libMcs.setMCSMode(SearchMode.FAST);

    LOGGER.info("Running LibMCS search on %d molecules.", NUM_SUBSTRATES);
    Long startTime = System.nanoTime();
    libMcs.search();
    LOGGER.info("Done! Took %f seconds", new Double(System.nanoTime() - startTime) / new Double(ONE_BILLION));
  }
}
