package com.act.biointerpretation.sars;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class OneSubstrateMcsCharacterizer implements EnzymeGroupCharacterizer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateMcsCharacterizer.class);
  private static final Integer ONLY_SUBSTRATE = 0;
  private static final String INCHI_SETTINGS = "inchi";

  private final MongoDB db;
  private final McsCalculator mcsCalculator;

  public OneSubstrateMcsCharacterizer(MongoDB db, McsCalculator mcsCalculator) {
    this.db = db;
    this.mcsCalculator = mcsCalculator;
  }

  /**
   * Characterizes the SeqGroup by finding the MCS of its substrates and returning a corresponding SAR.
   * Can be applied to any set of at least two reactions.
   *
   * @param group The seq group to characterize.
   * @return The resulting CharacterizedGroup, which contains the SAR and a list of associated ROs,
   * or an empty Optional if no SAR was found.
   * @throws IOException
   */
  @Override
  public Optional<CharacterizedGroup> characterizeGroup(SeqGroup group) {
    List<Reaction> reactions = getReactions(group);

    // Need at least two different reactions to build a MCS sar.
    if (reactions.size() < 2) {
      return Optional.empty();
    }

    // Can only build a SAR if all reactions have exactly one substrate
    for (Reaction reaction : reactions) {
      if (reaction.getSubstrates().length != 1) {
        return Optional.empty();
      }
    }

    try {
      List<Molecule> molecules = getMolecules(reactions);

      Molecule substructure = mcsCalculator.getMCS(molecules);
      Sar sar = new OneSubstrateSubstructureSar(substructure);

      return Optional.of(new CharacterizedGroup(group, sar, getRos(reactions)));

    } catch (MolFormatException e) {
      // Report error, but return empty rather than throwing an error. One malformed inchi shouldn't kill the run.
      LOGGER.warn("Error on seqGroup for seqs %s", group.getSeqIds());
      return Optional.empty();
    }
  }

  /**
   * Gets all mechanistic validator results from a set of reactions.
   *
   * @param reactions The reactions associated with the group.
   * @return The set of ROs associated with any of these reactions.
   */
  private Set<Integer> getRos(Iterable<Reaction> reactions) {
    Set<Integer> result = new HashSet<>();

    for (Reaction reaction : reactions) {
      JSONObject validatorResults = reaction.getMechanisticValidatorResult();
      if (validatorResults != null) {
        for (Object roId : reaction.getMechanisticValidatorResult().keySet()) {
          result.add(Integer.parseInt(roId.toString()));
        }
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
  private List<Reaction> getReactions(SeqGroup group) {
    List<Reaction> reactions = new ArrayList<>();
    for (Long reactionId : group.getReactionIds()) {
      reactions.add(db.getReactionFromUUID(reactionId));
    }
    return reactions;
  }

  private List<Molecule> getMolecules(List<Reaction> reactions) throws MolFormatException {
    List<Molecule> molecules = new ArrayList<>(reactions.size());

    for (Reaction reaction : reactions) {
      Chemical chemical = db.getChemicalFromChemicalUUID(reaction.getSubstrates()[ONLY_SUBSTRATE]);
      Molecule mol = MolImporter.importMol(chemical.getInChI(), INCHI_SETTINGS);
      molecules.add(mol);
    }

    return molecules;
  }
}
