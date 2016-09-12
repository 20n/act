package com.act.biointerpretation.l2expansion;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeFormat$;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SerializableReactor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Generates all predictions for a given PredictionSeed.
 */
public class AllPredictionsGenerator implements PredictionGenerator {

  private String moleculeFormat;

  private static final Integer CLEAN_DIMENSION = 2;

  final ReactionProjector projector;
  Integer nextUid = 0; // Keeps track of the next available unique id to assign to a prediction

  public AllPredictionsGenerator(ReactionProjector projector) {
    this.projector = projector;
    this.moleculeFormat = MoleculeFormat$.MODULE$.stdInchi().toString();
  }

  // Allow for the caller to change the format.
  public AllPredictionsGenerator(ReactionProjector projector, String format) {
    this.projector = projector;
    moleculeFormat = format;
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
        return Collections.emptyList();
      }
    }

    Molecule[] substratesArray = substrates.toArray(new Molecule[substrates.size()]);

    try {
      Map<Molecule[], List<Molecule[]>> projectionMap =
          projector.getRoProjectionMap(substratesArray, reactor.getReactor());
      return getAllPredictions(projectionMap, seed.getProjectorName());
    } catch (ReactionException e) {
      StringBuilder builder = new StringBuilder();
      builder.append(e.getMessage())
          .append(": substrates, reactor: ").append(getMoleculeStrings(substratesArray))
          .append(",").append(reactor.getReactorSmarts());
      throw new ReactionException(builder.toString());
    }
  }

  /**
   * Returns all predictions corresponding to a given projection map
   *
   * @param projectionMap The map from substrates to products.
   * @return The list of predictions.
   * @throws IOException
   */
  private List<L2Prediction> getAllPredictions(Map<Molecule[], List<Molecule[]>> projectionMap,
                                               String name) throws IOException {

    List<L2Prediction> result = new ArrayList<>();

    for (Molecule[] substrates : projectionMap.keySet()) {
      List<L2PredictionChemical> predictedSubstrates =
          L2PredictionChemical.getPredictionChemicals(getMoleculeStrings(substrates));

      for (Molecule[] products : projectionMap.get(substrates)) {
        List<L2PredictionChemical> predictedProducts =
            L2PredictionChemical.getPredictionChemicals(getMoleculeStrings(products));

        L2Prediction prediction = new L2Prediction(nextUid, predictedSubstrates, name, predictedProducts);

        result.add(prediction);
        nextUid++;
      }
    }
    return result;
  }


  /**
   * Translate an array of Chemaxon Molecules into a List of their string representations
   *
   * @param mols An array of molecules.
   * @return An array of inchis corresponding to the supplied molecules.
   */
  private List<String> getMoleculeStrings(Molecule[] mols) throws IOException {
    List<String> moleculeStrings = new ArrayList<>();
    for (Molecule mol : mols) {
      // Grabs the string through the exporter.  Default is InChI w/o
      moleculeStrings.add(MoleculeExporter.exportMolecule(mol, MoleculeFormat$.MODULE$.withName(this.moleculeFormat)));
    }
    return moleculeStrings;
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