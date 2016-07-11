package com.act.biointerpretation.sars;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OneSubstrateMCSCharacterizer implements EnzymeGroupCharacterizer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateMCSCharacterizer.class);
  private static final Integer ONLY_SUBSTRATE = 0;
  private static final String INCHI_SETTINGS = "inchi";
  private final MongoDB db;
  private final McsCalculator mcsCalculator;

  public OneSubstrateMCSCharacterizer(MongoDB db, McsCalculator mcsCalculator) {
    this.db = db;
    this.mcsCalculator = mcsCalculator;
  }

  /**
   * Builds a OneSubstrateSubstructureSar by calculating the MCS of the substrates of the reactions.
   * Currently only implemented for pairs of reactions with exactly one substrate each.
   *
   * @param group The seq group to characterize.
   * @return The resulting SAR, or an empty Optional if no SAR was found.
   * @throws IOException
   */
  @Override
  public Optional<CharacterizedGroup> getSar(SeqGroup group) {
    Collection<Reaction> reactions = getReactions(group);

    // Can only build a SAR for exactly two reactions
    if (reactions.size() != 2) {
      return Optional.empty();
    }

    // Can only build a SAR if all reactions have exactly one substrate
    for (Reaction reaction : reactions) {
      if (reaction.getSubstrates().length != 1) {
        return Optional.empty();
      }
    }

    List<Molecule> molecules = new ArrayList<>(2);
    for (Reaction reaction : reactions) {
      Chemical chemical = db.getChemicalFromChemicalUUID(reaction.getSubstrates()[ONLY_SUBSTRATE]);
      try {
        Molecule mol = MolImporter.importMol(chemical.getInChI(), INCHI_SETTINGS);
        molecules.add(mol);
      } catch (IOException e) {
        // Report error, but return empty rather than throwing an error. One malformed inchi shouldn't kill the run.
        LOGGER.warn("Error importing molecule from inchi, on chemical id %d.", chemical.getUuid());
        return Optional.empty();
      }
    }

    Molecule substructure = mcsCalculator.getMCS(molecules);
    Sar sar = new OneSubstrateSubstructureSar(substructure);
    CharacterizedGroup result = new CharacterizedGroup(group, sar, getRos(reactions));
    return Optional.of(result);
  }

  /**
   * Gets all mechanistic validator results from a set of reactions.
   *
   * @param reactions The reactions associated with the group.
   * @return
   */
  private Set<Integer> getRos(Iterable<Reaction> reactions) {
    Set<Integer> result = new HashSet<>();

    for (Reaction reaction : reactions) {
      for (Object roId : reaction.getMechanisticValidatorResult().keySet()) {
        result.add(Integer.parseInt(roId.toString()));
      }
    }

    return result;
  }

  /**
   * Looks up reaction ids from a SeqGroup in the DB, and returns the corresponding Reactions.
   *
   * @param group the SeqGroup.
   * @return The Reactions.
   */
  private Collection<Reaction> getReactions(SeqGroup group) {
    Collection<Reaction> reactions = new ArrayList<>();
    for (Long reactionId : group.getReactionIds()) {
      reactions.add(db.getReactionFromUUID(reactionId));
    }
    return reactions;
  }
}
