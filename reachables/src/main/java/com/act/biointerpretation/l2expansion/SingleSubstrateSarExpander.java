package com.act.biointerpretation.l2expansion;

import chemaxon.struc.Molecule;
import com.act.biointerpretation.sars.CharacterizedGroup;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SerializableReactor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SingleSubstrateSarExpander extends L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(SingleSubstrateSarExpander.class);

  final Iterable<CharacterizedGroup> sarGroups;
  final Iterable<Molecule> substrates;

  public SingleSubstrateSarExpander(Iterable<CharacterizedGroup> sarGroups,
                                    Iterable<Molecule> substrates,
                                    PredictionGenerator generator) {
    super(generator);
    this.sarGroups = sarGroups;
    this.substrates = substrates;
  }

  @Override
  public Iterable<PredictionSeed> getPredictionSeeds() {

    List<PredictionSeed> result = new ArrayList<>();

    for (CharacterizedGroup sarGroup : sarGroups) {
      List<Sar> sars = sarGroup.getSars();
      SerializableReactor reactor = sarGroup.getReactor();

      for (Molecule mol : substrates) {
        List<Molecule> singleSubstrateContainer = Arrays.asList(mol);

        result.add(new PredictionSeed(sarGroup.getGroupName(), singleSubstrateContainer, reactor, sars));
      }

    }
    return result;
  }
}
