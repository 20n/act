package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.sars.Sar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AllPredictionsGenerator implements PredictionGenerator {

  private static final Logger LOGGER = LogManager.getFormatterLogger(AllPredictionsGenerator.class);

  private static final String INCHI_SETTINGS = new StringBuilder("inchi:").
      append("SAbs").append(','). // Force absolute stereo to ensure standard InChIs are produced.
      append("AuxNone").append(','). // Don't write the AuxInfo block.
      append("Woff"). // Disable warnings.
      toString();

  private static final Reactor NULL_REACTOR = new Reactor();

  final ReactionProjector projector;
  Integer nextUid = 0;

  private Map<Ero, Reactor> roToReactorMap;

  public AllPredictionsGenerator(ReactionProjector projector) {
    this.projector = projector;
    roToReactorMap = new HashMap<>();
  }

  @Override
  public List<L2Prediction> getPredictions(List<Molecule> substrates, Ero ro, Optional<Sar> sar)
      throws IOException, ReactionException {
    // If SAR is supplied, test it before moving on with reaction
    if (sar.isPresent()) {
      if (!sar.get().test(substrates)) {
        return new ArrayList<L2Prediction>();
      }
    }

    aromatizeMolecules(substrates);
    Molecule[] substratesArray = substrates.toArray(new Molecule[substrates.size()]);
    Reactor reactor = getReactor(ro);
    Map<Molecule[], List<Molecule[]>> projectionMap =
        projector.getRoProjectionMap(substratesArray, reactor);

    return getAllPredictions(projectionMap, ro, sar);
  }


  /**
   * Returns all predictions corresponding to a given projection map
   *
   * @param projectionMap The map from substrates to products.
   * @param ro The RO.
   * @param sar The SAR.
   * @return The list of predictions.
   * @throws IOException
   */
  private List<L2Prediction> getAllPredictions(Map<Molecule[], List<Molecule[]>> projectionMap,
                                               Ero ro,
                                               Optional<Sar> sar) throws IOException {

    L2PredictionRo predictionRo = new L2PredictionRo(ro.getId(), ro.getRo());
    List<L2Prediction> result = new ArrayList<>();

    for (Molecule[] substrates : projectionMap.keySet()) {
      List<L2PredictionChemical> predictedSubstrates =
          L2PredictionChemical.getPredictionChemicals(getInchis(substrates));

      for (Molecule[] products : projectionMap.get(substrates)) {
        List<L2PredictionChemical> predictedProducts =
            L2PredictionChemical.getPredictionChemicals(getInchis(products));

        L2Prediction prediction = new L2Prediction(nextUid, predictedSubstrates, predictionRo, predictedProducts);
        if (sar.isPresent()) {
          prediction.setSar(sar.get());
        }
        result.add(prediction);
        nextUid++;
      }
    }
    return result;
  }


  /**
   * Translate an array of chemaxon Molecules into an ArrayList of their String inchi representations
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

  private Reactor getReactor(Ero ro) {
    Reactor reactor = roToReactorMap.getOrDefault(ro, NULL_REACTOR);
    if (!reactor.equals(NULL_REACTOR)) {
      return reactor;
    }

    reactor = new Reactor();
    try {
      reactor.setReactionString(ro.getRo());
    } catch (ReactionException e) {
      LOGGER.error("Cannot turn ro %d into a Reactor.", ro.getId());
      throw new IllegalArgumentException();
    }
    roToReactorMap.put(ro, reactor);
    return reactor;
  }

  /**
   * Aromatizes all molecules in the input array
   *
   * @param molecules Molecules to aromatize.
   * @throws MolFormatException
   */
  private void aromatizeMolecules(List<Molecule> molecules) throws MolFormatException {
    for (Molecule mol : molecules) {
      mol.aromatize(MoleculeGraph.AROM_BASIC);
    }
  }

}
