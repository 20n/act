package com.act.biointerpretation.sars;

import act.server.MongoDB;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class OneSubstrateMcsCharacterizer implements EnzymeGroupCharacterizer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateMcsCharacterizer.class);
  private static final Integer ONLY_SUBSTRATE = 0;
  private static final Integer ONLY_PRODUCT = 0;
  private static final String INCHI_SETTINGS = "inchi";
  private static final Double ACCEPT_ALL = 0D;
  private static final Integer CARBON = 6;

  private final MongoDB db;
  private final McsCalculator mcsCalculator;
  private final FullReactionBuilder reactionBuilder;
  private final ErosCorpus roCorpus;
  private final Double thresholdFraction;

  private Map<Integer, Reactor> roIdToReactorMap;

  public OneSubstrateMcsCharacterizer(MongoDB db,
                                      McsCalculator mcsCalculator,
                                      FullReactionBuilder reactionBuilder,
                                      ErosCorpus roCorpus) {
    this(db, mcsCalculator, reactionBuilder, roCorpus, ACCEPT_ALL);
  }

  public OneSubstrateMcsCharacterizer(MongoDB db,
                                      McsCalculator mcsCalculator,
                                      FullReactionBuilder reactionBuilder,
                                      ErosCorpus roCorpus,
                                      Double thresholdFraction) {
    this.db = db;
    this.mcsCalculator = mcsCalculator;
    this.reactionBuilder = reactionBuilder;
    this.roCorpus = roCorpus;
    this.thresholdFraction = thresholdFraction;
    roIdToReactorMap = new HashMap<>();
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
    Integer roId = getMajorityRo(reactions);

    reactions = getReactionsMatching(reactions, roId);

    if (!isCharacterizable(reactions)) {
      return Optional.empty();
    }

    try {
      List<Molecule> substrates = getSubstrates(reactions);

      Molecule substructure = mcsCalculator.getMCS(substrates);
      Sar substructureSar = new OneSubstrateSubstructureSar(substructure);

      // If the substructure is too small, return Optional.empty().
      if (substructure.getAtomCount(CARBON) < thresholdFraction * getAvgCarbonCount(substrates)) {
        return Optional.empty();
      }

      Sar carbonCountSar = new CarbonCountSar(getMinCarbonCount(substrates), getMaxCarbonCount(substrates));
      List<Sar> sars = Arrays.asList(carbonCountSar, substructureSar);

      // If the substructure is too small, return Optional.empty().
      if (substructure.getAtomCount(CARBON) < thresholdFraction * getAvgCarbonCount(substrates)) {
        return Optional.empty();
      }
      List<Molecule> products = getProducts(reactions);
      Molecule substrate1 = substrates.get(0);
      Molecule product1 = products.get(0);

      Reactor fullReactor;
      try {
        fullReactor = reactionBuilder.buildReaction(substrate1, product1, substructure, getReactor(roId));
      } catch (Exception e) {
        LOGGER.info("Couldn't build full reactor.");
        return Optional.empty();
      }

      return Optional.of(new CharacterizedGroup(group, sars, new SerializableReactor(fullReactor, roId)));

    } catch (MolFormatException e) {
      // Report error, but return empty rather than throwing an error. One malformed inchi shouldn't kill the run.
      LOGGER.warn("Error on seqGroup for seqs %s", group.getSeqIds());
      return Optional.empty();
    }
  }

  private Reactor getReactor(Integer roId) throws ReactionException {
    if (roIdToReactorMap.containsKey(roId)) {
      return roIdToReactorMap.get(roId);
    }

    List<Ero> ros = roCorpus.getRos();
    for (Ero ro : ros) {
      roIdToReactorMap.put(ro.getId(), ro.getReactor());
    }

    return roIdToReactorMap.get(roId);
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
    // Can only build a SAR if all reactions have exactly one substrate and one product
    for (Reaction reaction : reactions) {
      if (reaction.getSubstrates().length != 1 || reaction.getProducts().length != 1) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets all mechanistic validator results from a set of reactions.
   * Added check for RO explaining all reactions
   *
   * @param reactions The reactions associated with the group.
   * @return The set of ROs associated with all of these reactions.
   */
  private List<Reaction> getReactionsMatching(List<Reaction> reactions, Integer roId) {
    List<Reaction> matchingReactions = new ArrayList<>();

    for (Reaction reaction : reactions) {
      JSONObject validatorResults = reaction.getMechanisticValidatorResult();
      if (validatorResults != null) {
        for (Object validatorRo : reaction.getMechanisticValidatorResult().keySet()) {
          Integer validatorId = Integer.parseInt(validatorRo.toString());
          if (validatorId.equals(roId)) {
            matchingReactions.add(reaction);
            break;
          }
        }
      }
    }

    return matchingReactions;
  }

  /**
   * Gets the mechanistic validator result associated with the most of the reactions.
   *
   * @param reactions The reactions associated with the group.
   * @return The most common RO.
   */
  private Integer getMajorityRo(List<Reaction> reactions) {
    Map<Integer, Integer> roCountMap = new HashMap<>();

    for (Reaction reaction : reactions) {
      JSONObject validatorResults = reaction.getMechanisticValidatorResult();
      if (validatorResults != null) {
        for (Object roId : reaction.getMechanisticValidatorResult().keySet()) {
          Integer id = Integer.parseInt(roId.toString());
          if (roCountMap.containsKey(id)) {
            roCountMap.put(id, roCountMap.get(id) + 1);
          } else {
            roCountMap.put(id, 1);
          }
        }
      }
    }

    int maxCount = 0;
    Integer maxRoId = null;
    for (Integer roId : roCountMap.keySet()) {
      int count = roCountMap.get(roId);
      if (count > maxCount) {
        maxCount = count;
        maxRoId = roId;
      }
    }

    return maxRoId;
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

  private List<Molecule> getSubstrates(List<Reaction> reactions) throws MolFormatException {
    List<Molecule> molecules = new ArrayList<>(reactions.size());

    for (Reaction reaction : reactions) {
      Chemical chemical = db.getChemicalFromChemicalUUID(reaction.getSubstrates()[ONLY_SUBSTRATE]);
      Molecule mol = MolImporter.importMol(chemical.getInChI(), INCHI_SETTINGS);
      molecules.add(mol);
    }

    return molecules;
  }

  private List<Molecule> getProducts(List<Reaction> reactions) throws MolFormatException {
    List<Molecule> molecules = new ArrayList<>(reactions.size());

    for (Reaction reaction : reactions) {
      Chemical chemical = db.getChemicalFromChemicalUUID(reaction.getProducts()[ONLY_PRODUCT]);
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

  private Integer getMaxCarbonCount(List<Molecule> molecules) {
    Integer maxCount = 0;
    for (Molecule mol : molecules) {
      if (mol.getAtomCount(CARBON) > maxCount) {
        maxCount = mol.getAtomCount(CARBON);
      }
    }
    return maxCount;
  }


  private Integer getMinCarbonCount(List<Molecule> molecules) {
    Integer minCount = Integer.MAX_VALUE;
    for (Molecule mol : molecules) {
      if (mol.getAtomCount(CARBON) < minCount) {
        minCount = mol.getAtomCount(CARBON);
      }
    }
    return minCount;
  }
}
