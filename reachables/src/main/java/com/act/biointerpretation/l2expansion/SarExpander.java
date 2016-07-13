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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SarExpander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SarExpander.class);

  final Iterable<CharacterizedGroup> sarGroups;
  final Iterable<String> inchis;
  final ErosCorpus roCorpus;
  final ReactionProjector projector;

  public SarExpander(Iterable<CharacterizedGroup> sarGroups,
                     Iterable<String> inchis,
                     ErosCorpus roCorpus,
                     ReactionProjector projector) {
    this.sarGroups = sarGroups;
    this.inchis = inchis;
    this.roCorpus = roCorpus;
    this.projector = projector;
  }

  public L2PredictionCorpus buildPredictionCorpus() throws ReactionException {
    Map<Integer, Reactor> roIdToReactor = new HashMap<>();
    for (Ero ro : roCorpus) {
      roIdToReactor.put(ro.getId(), getReactor(ro));
    }

    for (CharacterizedGroup sarGroup : sarGroups) {
      Sar sar = sarGroup.getSar();
      Set<Integer> roIds = sarGroup.getRos();

      for (String inchi : inchis) {

        for (Integer roId : roIds) {
          try {
            Reactor reactor = roIdToReactor.get(roId);
            Molecule substrate = getMolecule(inchi);
            Map<Molecule[], List<Molecule[]>> subToProd =
                projector.getRoProjectionMap(new Molecule[] {substrate}, reactor);

          } catch (IOException e) {
            LOGGER.warn("Error projecting RO %d on substrate %s", roId, inchi);
            continue;
          }
        }
      }
    }
    return new L2PredictionCorpus();
  }

  private Reactor getReactor(Ero ro) throws ReactionException {
    Reactor reactor = new Reactor();
    reactor.setReactionString(ro.getRo());
    return reactor;
  }

  private Molecule getMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi);
  }
}
