package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import com.act.biointerpretation.sars.CharacterizedGroup;
import com.act.biointerpretation.sars.Sar;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SingleSubstrateSarExpander implements L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SingleSubstrateSarExpander.class);

  final Iterable<CharacterizedGroup> sarGroups;
  final Iterable<String> inchis;
  final ErosCorpus roCorpus;
  final PredictionGenerator generator;

  public SingleSubstrateSarExpander(Iterable<CharacterizedGroup> sarGroups,
                                    Iterable<String> inchis,
                                    ErosCorpus roCorpus,
                                    PredictionGenerator generator) {
    this.sarGroups = sarGroups;
    this.inchis = inchis;
    this.roCorpus = roCorpus;
    this.generator = generator;
  }

  public L2PredictionCorpus getPredictions() {

    Map<Integer, Ero> roIdToRo = new HashMap<>();
    for (Ero ro : roCorpus) {
      roIdToRo.put(ro.getId(), ro);
    }

    L2PredictionCorpus result = new L2PredictionCorpus();

    for (CharacterizedGroup sarGroup : sarGroups) {
      Sar sar = sarGroup.getSar();
      Set<Integer> roIds = sarGroup.getRos();

      for (String inchi : inchis) {

        for (Integer roId : roIds) {
          try {
            Ero ro = roIdToRo.get(roId);
            List<Molecule> substrates = Arrays.asList(getMolecule(inchi));

            result.addAll(generator.getPredictions(substrates, ro, Optional.of(sar)));

          } catch (IOException e) {
            // If there is an error on a certain RO, metabolite pair, we should log the error, but the the expansion may
            // produce some valid results, so no error is thrown.
            LOGGER.warn("Error projecting RO %d on substrate %s", roId, inchi);
          } catch (ReactionException e) {
            LOGGER.warn("Error projecting RO %d on substrate %s", roId, inchi);
          }
        }
      }
    }

    return result;
  }

  private Molecule getMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi);
  }
}
