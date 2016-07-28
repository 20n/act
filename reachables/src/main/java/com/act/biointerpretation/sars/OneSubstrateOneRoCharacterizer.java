package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OneSubstrateOneRoCharacterizer implements ReactionGroupCharacterizer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateOneRoCharacterizer.class);

  private final DbAPI dbApi;
  private final FullReactionBuilder reactionBuilder;
  private final ErosCorpus roCorpus;
  private final List<SarBuilder> sarBuilders;

  public OneSubstrateOneRoCharacterizer(DbAPI dbApi,
                                        List<SarBuilder> sarBuilders,
                                        FullReactionBuilder reactionBuilder,
                                        ErosCorpus roCorpus) {
    this.dbApi = dbApi;
    this.sarBuilders = sarBuilders;
    this.reactionBuilder = reactionBuilder;
    this.roCorpus = roCorpus;
  }

  /**
   * Characterizes the ReactionGroup by splitting it into sets of reactions that have the same RO, and characterizing
   * each by its own generalized Reactor and SAR.
   *
   * @param group The  ReactionGroup to characterize.
   * @return A list of the resulting CharacterizedGroups.
   * @throws IOException
   */
  @Override
  public List<CharacterizedGroup> characterizeGroup(ReactionGroup group) {
    List<CharacterizedGroup> resultGroups = new ArrayList<>();
    List<Reaction> allReactions = dbApi.getReactions(group);
    allReactions.removeIf(r -> r.getSubstrates().length != 1 || r.getProducts().length != 1);

    while (allReactions.size() > 1) {
      Integer roId = getMostCommonRo(allReactions);
      // If no RO explains any of the reactions, reject this set.
      if (roId == null) {
        break;
      }

      List<Reaction> matchingReactions = getReactionsMatching(allReactions, roId);
      LOGGER.info("%d reactions matching RO %d", matchingReactions.size(), roId);
      allReactions.removeAll(matchingReactions);

      if (matchingReactions.size() == 1) {
        LOGGER.warn("Group %s has only 1 substrate for RO %d", group.getName(), roId);
        continue;
      }

      List<RxnMolecule> rxnMolecules = dbApi.getRxnMolecules(matchingReactions);
      Optional<CharacterizedGroup> characterization = characterizeUniformGroup(rxnMolecules, roId, group.getName());
      if (characterization.isPresent()) {
        resultGroups.add(characterization.get());
      }
    }
    return resultGroups;
  }

  /**
   * Characterizes a group of reactions that all share the same RO by applying the SAR builders and the
   * FullReactionBuilder to the reactions. If any step fails, an empty Optional is returned.
   *
   * @param reactions The reactions the characterize.
   * @param roId The RO that matches all of the reactions.
   * @param groupName The name of the ReactionGroup.
   * @return
   */
  private Optional<CharacterizedGroup> characterizeUniformGroup(List<RxnMolecule> reactions,
                                                                Integer roId,
                                                                String groupName) {
    List<Sar> sars;
    try {
      sars = buildSars(reactions);
    } catch (MolFormatException e) {
      return Optional.empty();
    }

    Reactor fullReactor;
    try {
      fullReactor = buildFullReactor(reactions, roId);
    } catch (ReactionException e) {
      return Optional.empty();
    }

    return Optional.of(new CharacterizedGroup(groupName, sars, new SerializableReactor(fullReactor, roId)));
  }

  private Reactor buildFullReactor(List<RxnMolecule> reactions, Integer roId) throws ReactionException {
    Reactor seedReactor = roCorpus.getEro(roId).getReactor();
    return reactionBuilder.buildReaction(reactions, seedReactor);
  }

  private List<Sar> buildSars(List<RxnMolecule> reactions) throws MolFormatException {
    List<Sar> sars = new ArrayList<>();
    for (SarBuilder builder : sarBuilders) {
      sars.add(builder.buildSar(reactions));
    }
    return sars;
  }

  /**
   * Gets the mechanistic validator result associated with the most of the reactions.
   *
   * @param reactions The reactions associated with the group.
   * @return The most common RO.
   */
  private Integer getMostCommonRo(List<Reaction> reactions) {
    Map<Integer, List<Reaction>> roIdToReactions = getRoIdToReactionsMap(reactions);
    Map<Integer, Integer> roIdToCount = Maps.transformValues(roIdToReactions, reactionList -> reactionList.size());
    return Collections.max(roIdToCount.entrySet(), Map.Entry.comparingByValue()).getKey();
  }

  /**
   * Gets all the reactions in the list which have the given RO as a mechanistic validator result.
   *
   * @param reactions The reactions associated with the group.
   * @return The set of ROs associated with all of these reactions.
   */
  private List<Reaction> getReactionsMatching(List<Reaction> reactions, Integer roId) {
    return getRoIdToReactionsMap(reactions).get(roId);
  }

  /**
   * Process the mechanistic validator results of the given reactions to build a map from RO ID to all reactions with
   * that ID as a validator result.
   *
   * @param reactions The reactions to process.
   * @return The map from ro id -> associated reactions.
   */
  private Map<Integer, List<Reaction>> getRoIdToReactionsMap(List<Reaction> reactions) {
    Map<Integer, List<Reaction>> roIdToReactionsMap = new HashMap<>();

    for (Reaction reaction : reactions) {
      JSONObject validatorResults = reaction.getMechanisticValidatorResult();
      if (validatorResults != null) {
        for (Object roId : reaction.getMechanisticValidatorResult().keySet()) {
          Integer id = Integer.parseInt(roId.toString());
          List<Reaction> reactionList = roIdToReactionsMap.get(id);
          if (reactionList == null) {
            reactionList = new ArrayList<>();
            roIdToReactionsMap.put(id, reactionList);
          }
          reactionList.add(reaction);
        }
      }
    }

    return roIdToReactionsMap;
  }
}
