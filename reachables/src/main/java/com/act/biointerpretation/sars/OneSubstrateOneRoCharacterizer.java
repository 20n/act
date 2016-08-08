package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
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
import java.util.stream.Collectors;

public class OneSubstrateOneRoCharacterizer implements ReactionGroupCharacterizer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(OneSubstrateOneRoCharacterizer.class);

  private final DbAPI dbApi;
  private final FullReactionBuilder reactionBuilder;
  private final ErosCorpus roCorpus;
  private final List<SarFactory> sarFactories;

  public OneSubstrateOneRoCharacterizer(DbAPI dbApi,
                                        List<SarFactory> sarFactories,
                                        FullReactionBuilder reactionBuilder,
                                        ErosCorpus roCorpus) {
    this.dbApi = dbApi;
    this.sarFactories = sarFactories;
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
    List<Reaction> workingReactionList = dbApi.getReactions(group);
    workingReactionList.removeIf(r -> r.getSubstrates().length != 1 || r.getProducts().length != 1);
    Integer initialReactionCount = workingReactionList.size();

    while (workingReactionList.size() > 1) {
      Integer roId = getMostCommonRo(workingReactionList);
      // If no RO explains any of the reactions, reject this set.
      if (roId == null) {
        break;
      }

      List<Reaction> matchingReactions = getReactionsMatching(workingReactionList, roId);
      LOGGER.info("%d of %d reactions matching RO %d", matchingReactions.size(), initialReactionCount, roId);
      workingReactionList.removeAll(matchingReactions);

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
    List<Sar> sars = buildSars(reactions);

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

  private List<Sar> buildSars(List<RxnMolecule> reactions) {
    List<Sar> sars = new ArrayList<>();
    for (SarFactory builder : sarFactories) {
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
    Map<Integer, Integer> roIdToCount = roIdToReactions.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
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
