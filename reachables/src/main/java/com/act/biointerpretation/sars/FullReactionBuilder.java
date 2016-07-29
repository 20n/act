package com.act.biointerpretation.sars;

import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class FullReactionBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(FullReactionBuilder.class);

  private final McsCalculator mcsCalculator;
  private final ExpandedReactionSearcher searcher;
  private final ReactionProjector projector;

  public FullReactionBuilder(
      McsCalculator mcsCalculator,
      ExpandedReactionSearcher searcher,
      ReactionProjector projector) {
    this.mcsCalculator = mcsCalculator;
    this.searcher = searcher;
    this.projector = projector;
  }

  /**
   * Builds a Reactor that matches every reaction in the list and expands the seedReactor.
   *
   * @param rxnMolecules The reactions that the expansion must match.
   * @param seedReactor The seed reactor to expand.
   * @return The full Reactor.
   * @throws ReactionException If somethign goes seriously wrong, and returning just the original seed is not a severe
   *                           enough mode of failure.
   */
  public Reactor buildReaction(List<RxnMolecule> rxnMolecules, Reactor seedReactor) throws ReactionException {
    if (!DbAPI.areAllOneSubstrate(rxnMolecules) || !DbAPI.areAllOneProduct(rxnMolecules)) {
      throw new IllegalArgumentException("FullReactionBuilder only handles one substrate, one product reactions.");
    }

    List<Molecule> allSubstrates = rxnMolecules.stream()
        .map(rxn -> getOnlySubstrate(rxn)).collect(Collectors.toList());

    Molecule substructure = mcsCalculator.getMCS(allSubstrates);

    Molecule firstSubstrate = allSubstrates.get(0);
    Molecule expectedProduct = getOnlyProduct(rxnMolecules.get(0));

    try {
      searcher.initSearch(seedReactor, firstSubstrate, expectedProduct, substructure);
    } catch (SearchException e) {
      LOGGER.warn("SearchException on ExpandedReactionSearcher.init(): %s", e.getMessage());
      throw new ReactionException(e.getMessage());
    }

    Reactor fullReactor;
    while ((fullReactor = searcher.getNextReactor()) != null) {
      if (checkReactorAgainstReactions(fullReactor, rxnMolecules)) {
        return fullReactor;
      }
    }

    LOGGER.warn("Didn't find an expansion that fit all reactions. Returning seed reactor only.");
    return seedReactor;
  }

  /**
   * Checks the Reactor against the Reactions represented by the RxnMolecule list. Returns true iff the
   * Reactor correctly predicts all reactions.
   *
   * @param fullReactor The Reactor to check.
   * @param reactions the ReactionMolecules.
   * @return True if the reactor produces the correct product on each substrate.
   */
  public boolean checkReactorAgainstReactions(Reactor fullReactor, List<RxnMolecule> reactions) {
    try {
      for (RxnMolecule reaction : reactions) {
        fullReactor.setReactants(new Molecule[] {getOnlySubstrate(reaction)});
        projector.runTillProducesProduct(fullReactor, getOnlyProduct(reaction));
      }
    } catch (ReactionException e) {
      return false;
    }
    return true;
  }

  public Molecule getOnlySubstrate(RxnMolecule molecule) {
    return molecule.getReactants()[0];
  }

  public Molecule getOnlyProduct(RxnMolecule molecule) {
    return molecule.getProducts()[0];
  }
}
