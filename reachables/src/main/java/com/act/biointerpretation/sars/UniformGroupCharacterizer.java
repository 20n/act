package com.act.biointerpretation.sars;

import act.shared.Reaction;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UniformGroupCharacterizer implements EnzymeGroupCharacterizer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(UniformGroupCharacterizer.class);

  private final DbAPI dbApi;
  private final FullReactionBuilder reactionBuilder;
  private final ErosCorpus roCorpus;
  private final List<SarBuilder> sarBuilders;

  private Map<Integer, Reactor> roIdToReactorMap;

  public UniformGroupCharacterizer(DbAPI dbApi,
                                   List<SarBuilder> sarBuilders,
                                   FullReactionBuilder reactionBuilder,
                                   ErosCorpus roCorpus) {
    this.dbApi = dbApi;
    this.sarBuilders = sarBuilders;
    this.reactionBuilder = reactionBuilder;
    this.roCorpus = roCorpus;
    roIdToReactorMap = new HashMap<>();
  }

  /**
   * Characterizes the ReactionGroup by finding the MCS of its substrates and returning a corresponding SAR.
   * Can be applied to any set of at least two reactions.
   *
   * @param group The seq group to characterize.
   * @return The resulting CharacterizedGroup, which contains the SAR and a list of associated ROs,
   * or an empty Optional if no SAR was found.
   * @throws IOException
   */
  @Override
  public List<CharacterizedGroup> characterizeGroup(ReactionGroup group) {
    List<CharacterizedGroup> resultGroups = new ArrayList<>();
    List<Reaction> allReactions = dbApi.getReactions(group);
    allReactions.removeIf(r -> r.getSubstrates().length != 1 || r.getProducts().length != 1);

    while (allReactions.size() > 1) {
      Integer roId = getPluralityRo(allReactions);
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
      Optional<CharacterizedGroup> characterization = characterizeUniformGroup(rxnMolecules, roId, group);
      if (characterization.isPresent()) {
        resultGroups.add(characterization.get());
      }
    }
    return resultGroups;
  }


  private Optional<CharacterizedGroup> characterizeUniformGroup(List<RxnMolecule> reactions,
                                                                Integer roId,
                                                                ReactionGroup group) {
    Reactor seedReactor = null;
    try {
      seedReactor = getReactor(roId);
    } catch (ReactionException e) {
      LOGGER.error("Couldn't import reactor from RO %d: %s", roId, e.getMessage());
      return Optional.empty();
    }
    Reactor fullReactor = null;
    try {
      fullReactor = reactionBuilder.buildReaction(reactions, seedReactor);
    } catch (ReactionException e) {
      LOGGER.warn("Couldn't build full reactor for reaction group %s: %s", group.getName(), e.getMessage());
      return Optional.empty();
    }

    List<Sar> sars = new ArrayList<>();
    for (SarBuilder builder : sarBuilders) {
      try {
        sars.add(builder.buildSar(reactions));
      } catch (MolFormatException e) {
        LOGGER.error("Couldn't build sar for reaction group %s: %s", group.getName(), e.getMessage());
        return Optional.empty();
      }
    }

    String name = group.getName();
    return Optional.of(new CharacterizedGroup(group, sars, new SerializableReactor(fullReactor, roId, name)));
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
   * Gets all the reactions in the list which have the given RO as a mechanistic validator result.
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
  private Integer getPluralityRo(List<Reaction> reactions) {
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
}
