package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.calculations.hydrogenize.Hydrogenize;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class FullReactionBuilder {

  private static final Logger LOGGER = LogManager.getFormatterLogger(FullReactionBuilder.class);

  private static final Hydrogenize HYDROGENIZER = new Hydrogenize();


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
    HYDROGENIZER.convertImplicitHToExplicit(firstSubstrate);
    HYDROGENIZER.convertImplicitHToExplicit(expectedProduct);

    searcher.setSeedReactor(seedReactor);
    searcher.setSubstrate(firstSubstrate);
    searcher.setExpectedProduct(expectedProduct);
    searcher.setSubstructure(substructure);

    searcher.initSearch();

    Reactor fullReactor;
    while ((fullReactor = searcher.getNextGeneralization()) != null) {
      if (checkReactorAgainstReactions(fullReactor, substrates, products)) {
        return fullReactor;
      }
    }

    LOGGER.warn("Didn't find a generalization that fit all reactions. Returning seed reactor only.");
    return seedReactor;
  }

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
