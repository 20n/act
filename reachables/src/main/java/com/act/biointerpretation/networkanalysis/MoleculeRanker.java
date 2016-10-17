package com.act.biointerpretation.networkanalysis;

import chemaxon.struc.Molecule;
import com.act.lcms.v2.MassChargeCalculator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by gil on 10/14/16.
 */
public class MoleculeRanker {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MoleculeRanker.class);

  private static final Integer NUM_STEPS = 3;
  private final MetabolismNetwork network;
  private final Function<MassChargeCalculator.MZSource, Double> confidenceScorer;

  public MoleculeRanker(MetabolismNetwork network, Function<MassChargeCalculator.MZSource, Double> confidenceScorer) {
    this.network = network;
    this.confidenceScorer = confidenceScorer;
  }

  public Double scoreMolecule(String molecule) {
    Optional<NetworkNode> nodeOption = network.getNodeOption(molecule);
    if (!nodeOption.isPresent()) {
      return 0.0;
    }

    NetworkNode node = nodeOption.get();
    MetabolismNetwork precursorGraph = network.getPrecursorSubgraph(node, NUM_STEPS);

    List<MassChargeCalculator.MZSource> mzSources = precursorGraph.getNodes().stream().map(n ->
        new MassChargeCalculator.MZSource(n.getMetabolite().getInchi())).collect(Collectors.toList());
    MassChargeCalculator.MassChargeMap massChargeMap = null;
    try {
      massChargeMap = MassChargeCalculator.makeMassChargeMap(mzSources);
    } catch (IOException e) {
      LOGGER.error("Could not build mass charge map: IOException.");
      return 0.0;
    }

    for (MassChargeCalculator.MZSource mz : massChargeMap.mzSourceIter()) {
    }

    return 0.0;


  }
}
