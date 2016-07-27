package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
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

    List<Molecule> substrates, products;
    try {
      substrates = dbApi.getFirstSubstratesAsMolecules(reactions);
      products = dbApi.getFirstProductsAsMolecules(reactions);
    } catch (MolFormatException e) {
      throw new ReactionException("Couldn't get substrates and products from DB: " + e.getMessage());
    }

    Molecule substructure = mcsCalculator.getMCS(substrates);

    Molecule firstSubstrate = substrates.get(0);
    Molecule expectedProduct = products.get(0);

    searcher.setSeedReactor(seedReactor);
    searcher.setSubstrate(firstSubstrate);
    searcher.setExpectedProduct(expectedProduct);
    searcher.setSubstructure(substructure);

    try {
      searcher.initSearch();
    } catch (SearchException e) {
      LOGGER.warn("SearchException on GeneralReactionSearcher.init(): %s", e.getMessage());
      throw new ReactionException(e.getMessage());
    }

    Reactor fullReactor;
    while ((fullReactor = searcher.getNextGeneralization()) != null) {
      if (checkReactorAgainstReactions(fullReactor, substrates, products)) {
        return fullReactor;
      }
    }

    LOGGER.warn("Didn't find a generalization that fit all reactions. Returning seed reactor only.");
    return seedReactor;
  }

  /**
   * Checks the Reactor against the Reactions represented by the substrate and product lists. Returns true iff the
   * Reactor correctly predicts all Reactions.
   *
   * @param fullReactor The Reactor to check.
   * @param substrates The substrates it should act on.
   * @param products The products it should produce.
   * @return True if the reactor produces the correct product on each substrate.
   */
  public boolean checkReactorAgainstReactions(Reactor fullReactor, List<Molecule> substrates, List<Molecule> products) {
    try {
      for (Integer i = 1; i < substrates.size(); i++) {
        Molecule substrate = substrates.get(i);
        Molecule product = products.get(i);
        fullReactor.setReactants(new Molecule[] {substrate});

        projector.runTillProducesProduct(fullReactor, product);
      }
    } catch (ReactionException e) {
      return false;
    }
    return true;
  }
}
