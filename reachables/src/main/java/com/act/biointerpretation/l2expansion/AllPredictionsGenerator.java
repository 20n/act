package com.act.biointerpretation.l2expansion;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SerializableReactor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates all predictions for a given PredictionSeed.
 */
public class AllPredictionsGenerator implements PredictionGenerator {

  private static final Logger LOGGER = LogManager.getFormatterLogger(AllPredictionsGenerator.class);

  private static final String INCHI_SETTINGS = new StringBuilder("inchi:").
      append("SAbs").append(','). // Force absolute stereo to ensure standard InChIs are produced.
      append("AuxNone").append(','). // Don't write the AuxInfo block.
      append("Woff"). // Disable warnings.
      toString();

  private static final Integer CLEAN_DIMENSION = 2;

  final ReactionProjector projector;
  Integer nextUid = 0; // Keeps track of the next available unique id to assign to a prediction

  public AllPredictionsGenerator(ReactionProjector projector) {
    this.projector = projector;
  }

  @Override
  public List<L2Prediction> getPredictions(PredictionSeed seed) throws IOException, ReactionException {
    List<Molecule> substrates = seed.getSubstrates();
    cleanAndAromatize(substrates);

    List<Sar> sars = seed.getSars();
    SerializableReactor reactor = seed.getRo();

    // If one or more SARs are supplied, test them before applying the reactor.
    for (Sar sar : sars) {
      if (!sar.test(substrates)) {
        return new ArrayList<L2Prediction>();
      }
    }

    Molecule[] substratesArray = substrates.toArray(new Molecule[substrates.size()]);

    try {
      Map<Molecule[], List<Molecule[]>> projectionMap =
          projector.getRoProjectionMap(substratesArray, reactor.getReactor());
      return getAllPredictions(projectionMap, reactor, sars);
    } catch (ReactionException e) {
      StringBuilder builder = new StringBuilder();
      builder.append(e.getMessage())
          .append(": substrates, reactor: ").append(getInchis(substratesArray))
          .append(",").append(reactor.getReactorSmarts());
      throw new ReactionException(builder.toString());
    }
  }

  /**
   * Returns all predictions corresponding to a given projection map
   *
   * @param projectionMap The map from substrates to products.
   * @param reactor The reactor.
   * @param sars The SARs.
   * @return The list of predictions.
   * @throws IOException
   */
  private List<L2Prediction> getAllPredictions(Map<Molecule[], List<Molecule[]>> projectionMap,
                                               SerializableReactor reactor,
                                               List<Sar> sars) throws IOException {

    List<L2Prediction> result = new ArrayList<>();

    for (Molecule[] substrates : projectionMap.keySet()) {
      List<L2PredictionChemical> predictedSubstrates =
          L2PredictionChemical.getPredictionChemicals(getInchis(substrates));

      for (Molecule[] products : projectionMap.get(substrates)) {
        List<L2PredictionChemical> predictedProducts =
            L2PredictionChemical.getPredictionChemicals(getInchis(products));

        L2Prediction prediction = new L2Prediction(nextUid, predictedSubstrates, reactor, sars, predictedProducts);

        result.add(prediction);
        nextUid++;
      }
    }
    return result;
  }


  /**
   * Translate an array of chemaxon Molecules into a List of their String inchi representations
   *
   * @param mols An array of molecules.
   * @return An array of inchis corresponding to the supplied molecules.
   */
  private List<String> getInchis(Molecule[] mols) throws IOException {
    List<String> inchis = new ArrayList<>();
    for (Molecule mol : mols) {
      inchis.add(MolExporter.exportToFormat(mol, INCHI_SETTINGS));
    }
    return inchis;
  }

  /**
   * Aromatizes all molecules in the input array
   *
   * @param molecules Molecules to aromatize.
   * @throws MolFormatException
   */
  private void cleanAndAromatize(List<Molecule> molecules) throws MolFormatException {
    for (Molecule mol : molecules) {
      mol.aromatize(MoleculeGraph.AROM_BASIC);
      Cleaner.clean(mol, CLEAN_DIMENSION);
    }
  }

}
