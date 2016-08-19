package com.act.biointerpretation.l2expansion;

import act.shared.Chemical;
import chemaxon.formats.MolFormatException;
import chemaxon.reaction.ReactionException;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import com.act.biointerpretation.sars.SerializableReactor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO: write tests for this class.
public class TwoSubstrateRoExpander extends L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(TwoSubstrateRoExpander.class);
  private static final Integer TWO_SUBSTRATES = 2;

  private final List<Chemical> chemicalsA;
  private final List<Chemical> chemicalsB;
  private final ErosCorpus roCorpus;

  public TwoSubstrateRoExpander(List<Chemical> chemicalsA,
                                List<Chemical> chemicalsB,
                                ErosCorpus roCorpus,
                                PredictionGenerator generator) {
    super(generator);
    this.chemicalsA = chemicalsA;
    this.chemicalsB = chemicalsB;
    this.roCorpus = roCorpus;
  }

  /**
   * This function performs pairwise L2 expansion on two sets of substrates. The function is optimized for only
   * computing RO expansions on chemical combinations where both chemicals have passed the RO substructure matching.
   * This is why this class requires chemicals rather than just inchis - we can't run the optimizations if the chemicals
   * aren't in our DB.
   *
   * @return The L2PredictionCorpus of all products generated.
   * @throws IOException
   * @throws ReactionException
   */
  @Override
  public Iterable<PredictionSeed> getPredictionSeeds() {

    roCorpus.filterCorpusBySubstrateCount(TWO_SUBSTRATES);
    LOGGER.info("The number of ROs to apply is %d", roCorpus.getRos().size());

    LOGGER.info("Constructing ro to molecule structures for metabolite list and chemicals of interest list.");
    Map<Integer, Set<Molecule>> roIdToMoleculesA = constructRoToMolecules(chemicalsA);
    Map<Integer, Set<Molecule>> roIdToMoleculesB = constructRoToMolecules(chemicalsB);

    LOGGER.info("Perform L2 expansion for each ro in the list");
    List<PredictionSeed> result = new ArrayList<>();

    int roProcessedCounter = 0;
    for (Ero ro : roCorpus.getRos()) {

      SerializableReactor reactor;
      try {
        reactor = new SerializableReactor(ro.getReactor(), ro.getId());
      } catch (ReactionException e) {
        LOGGER.info("Skipping ro %d, couldn't get Reactor.", ro.getId());
        continue;
      }

      roProcessedCounter++;
      LOGGER.info("Processing the %d indexed ro out of %s ros", roProcessedCounter, roCorpus.getRos().size());

      Set<Molecule> roMoleculesA = roIdToMoleculesB.get(ro.getId());
      Set<Molecule> roMoleculesB = roIdToMoleculesA.get(ro.getId());

      if (roMoleculesA == null || roMoleculesB == null) {
        continue;
      }

      for (Molecule moleculeA : roMoleculesA) {
        for (Molecule moleculeB : roMoleculesB) {
          List<Molecule> substrates = Arrays.asList(moleculeA, moleculeB);
          result.add(new PredictionSeed(ro.getId().toString(), substrates, reactor, NO_SAR));
        }
      }
    }
    return result;
  }

  /**
   * This function constructs a ro to set of molecules map
   *
   * @param chemicals List of chemicals to process
   * @return A map of ro to set of molecules that match the ro's substructure
   */
  private Map<Integer, Set<Molecule>> constructRoToMolecules(List<Chemical> chemicals) {
    Map<Integer, Set<Molecule>> result = new HashMap<>();
    for (Chemical chemical : chemicals) {
      try {
        Molecule mol = L2InchiCorpus.importMolecule(chemical.getInChI());

        for (Integer roId : chemical.getSubstructureRoIds()) {
          Set<Molecule> molecules = result.get(roId);
          if (molecules == null) {
            molecules = new HashSet<>();
            result.put(roId, molecules);
          }
          molecules.add(mol);
        }
      } catch (MolFormatException e) {
        LOGGER.error("MolFormatException on metabolite %s. %s", chemical.getInChI(), e.getMessage());
      }
    }
    return result;
  }

}
