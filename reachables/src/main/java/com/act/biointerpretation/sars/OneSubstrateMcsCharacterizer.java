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
  private static final Double ACCEPT_ALL = 0D;
  private static final Integer CARBON = 4;

  private final MongoDB db;
  private final McsCalculator mcsCalculator;
  private final Double thresholdFraction;

  public OneSubstrateMcsCharacterizer(MongoDB db, McsCalculator mcsCalculator) {
    this.db = db;
    this.mcsCalculator = mcsCalculator;
    thresholdFraction = ACCEPT_ALL;
  }

  public OneSubstrateMcsCharacterizer(MongoDB db, McsCalculator mcsCalculator, Double thresholdFraction) {
    this.db = db;
    this.mcsCalculator = mcsCalculator;
    this.thresholdFraction = thresholdFraction;
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

    if (!isCharacterizable(reactions)) {
      return Optional.empty();
    }

    try {
      Sar sar = getSar(reactions);
      return Optional.of(new CharacterizedGroup(group, sar, getRos(reactions)));

    } catch (MolFormatException e) {
      // Report error, but return empty rather than throwing an error. One malformed inchi shouldn't kill the run.
      LOGGER.warn("Error on seqGroup for seqs %s", group.getSeqIds());
      return Optional.empty();
    }
  }

  /**
   * Tests the reactions for basic characteristics so we can reject the set if we have no hope of building a SAR.
   *
   * @param reactions The reactions to test.
   * @return Whether or not we should try to build a SAR.
   */
  private boolean isCharacterizable(List<Reaction> reactions) {
    // Need at least two different reactions to build a MCS sar.
    if (reactions.size() < 2) {
      return false;
    }
    // Can only build a SAR if all reactions have exactly one substrate
    for (Reaction reaction : reactions) {
      if (reaction.getSubstrates().length != 1) {
        return false;
      }
    }
    return true;
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
   * Bulids a Sar by calculating the MCS of the input reactions.
   *
   * @param reactions The reactions.
   * @return The substructure SAR.
   * @throws MolFormatException
   */
  public Sar getSar(List<Reaction> reactions) throws MolFormatException {
    List<Molecule> molecules = getMolecules(reactions);
    Molecule substructure = mcsCalculator.getMCS(molecules);
    return new OneSubstrateSubstructureSar(substructure);
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

  private Double getAvgCarbonCount(List<Molecule> molecules) {
    Double sum = 0D;
    for (Molecule mol : molecules) {
      sum += mol.getAtomCount(CARBON);
    }
    return sum / molecules.size();
  }
}
