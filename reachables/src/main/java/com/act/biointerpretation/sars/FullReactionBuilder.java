package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class FullReactionBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(FullReactionBuilder.class);

  private final DbAPI dbApi;
  private final McsCalculator mcsCalculator;
  private final GeneralReactionSearcher searcher;
  private final ReactionProjector projector;

  public FullReactionBuilder(DbAPI dbApi,
                             McsCalculator mcsCalculator,
                             GeneralReactionSearcher searcher,
                             ReactionProjector projector) {
    this.dbApi = dbApi;
    this.mcsCalculator = mcsCalculator;
    this.searcher = searcher;
    this.projector = projector;
  }

  /**
   * Builds a Reactor that matches every reaction in the list and generalizes the seedReactor.
   *
   * @param reactions The reactions that the generalization must match.
   * @param seedReactor The seed reactor to generalize.
   * @return The full Reactor.
   * @throws ReactionException If somethign goes seriously wrong, and returning just the original seed is not a severe
   *                           enough mode of failure.
   */
  public Reactor buildReaction(List<Reaction> reactions, Reactor seedReactor) throws ReactionException {
    if (!DbAPI.areAllOneSubstrate(reactions) || !DbAPI.areAllOneProduct(reactions)) {
      throw new IllegalArgumentException("FullReactionBuilder only handles one substrate, one product reactions.");
    }

    List<RxnMolecule> rxnMolecules = dbApi.getRxnMolecules(reactions);
    List<Molecule> allSubstrates = Lists.transform(rxnMolecules, rxn -> getOnlySubstrate(rxn));

    Molecule substructure = mcsCalculator.getMCS(allSubstrates);

    Molecule firstSubstrate = allSubstrates.get(0);
    Molecule expectedProduct = getOnlyProduct(rxnMolecules.get(0));

    try {
      searcher.initSearch(seedReactor, firstSubstrate, expectedProduct, substructure);
    } catch (SearchException e) {
      LOGGER.warn("SearchException on GeneralReactionSearcher.init(): %s", e.getMessage());
      throw new ReactionException(e.getMessage());
    }

    Reactor fullReactor;
    while ((fullReactor = searcher.getNextGeneralization()) != null) {
      if (checkReactorAgainstReactions(fullReactor, rxnMolecules)) {
        return fullReactor;
      }
    }

    LOGGER.warn("Didn't find a generalization that fit all reactions. Returning seed reactor only.");
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
