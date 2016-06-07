package com.act.biointerpretation.l2expansion;

import chemaxon.formats.MolExporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.Utils.ReactionProjector;
import com.act.biointerpretation.mechanisminspection.Ero;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Carries out the main logic of L2 expansion by applying a set of ROs to a set of metabolites.
 */
public class L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);
  L2RoCorpus roCorpus;
  L2MetaboliteCorpus metaboliteCorpus;

  /**
   * @param roCorpus An L2RoCorpus of all ROs to be tested.
   * @param metaboliteCorpus An L2MetaboliteCorpus of all metabolites on which to test the ROs.
   */
  public L2Expander(L2RoCorpus roCorpus, L2MetaboliteCorpus metaboliteCorpus) {
    this.roCorpus = roCorpus;
    this.metaboliteCorpus = metaboliteCorpus;
  }

  /**
   * Tests all reactions in the L2RoCorpus on all metabolites in L2MetaboliteCorpus.
   * @return corpus of all reactions that are predicted to occur.
   * @throws IOException
   */
  public L2PredictionCorpus getPredictionCorpus() throws IOException {

    //Build input corpuses
    List<L2Prediction> results = new ArrayList<>();
    metaboliteCorpus.buildCorpus();
    Map<String, Molecule> metabolites = metaboliteCorpus.getCorpus();
    roCorpus.buildCorpus();
    Map<Ero, Reactor> ros = roCorpus.getCorpus();

    //iterate over every (metabolite, ro) pair
    for (String inchi : metabolites.keySet()) {
      for (Ero ro : ros.keySet()) {

        Molecule[] substrates = new Molecule[]{ metabolites.get(inchi) };
        Reactor reactor = ros.get(ro);

        try {
          Molecule[] products = ReactionProjector.projectRoOnMolecules(substrates, reactor);
          if (products != null && products.length > 0) { //reaction worked if products are produced
            results.add(new L2Prediction(getInchis(substrates), ro, getInchis(products)));
          }
        } catch (ReactionException e) {
          System.out.println("Reaction exception!");
        }
      }
    }

    return new L2PredictionCorpus(results);
  }

  /**
   * Translate an array of chemaxon Molecules into their String inchi representations.
   * @param mols An array of molecules.
   * @return An array of inchis corresponding to the supplied molecules.
   */
  private static String[] getInchis(Molecule[] mols) {
    String[] results = new String[mols.length];
    for (int i = 0; i < results.length; i++) {
      results[i] = getInchi(mols[i]);
    }
    return results;
  }

  /**
   * Translate one chemaxon Molecule into its inchi representation.
   * @param mol The molecule to be translated.
   * @return The molecule's inchi.
   */
  private static String getInchi(Molecule mol) {
    try {
      return MolExporter.exportToFormat(mol, "inchi:AuxNone");
    } catch (java.io.IOException e) {
      return "GET_INCHI_ERROR";
    }
  }
}

