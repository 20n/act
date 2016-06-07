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


/*
 * Carries out the main logic of L2 expansion by applying a set of ROs to a set of metabolites
 */
public class L2Expander {

  private static final Logger LOGGER = LogManager.getFormatterLogger(L2Expander.class);
  L2RoCorpus roCorpus;
  L2MetaboliteCorpus metaboliteCorpus;

  public L2Expander(L2RoCorpus roCorpus, L2MetaboliteCorpus metaboliteCorpus) {
    this.roCorpus = roCorpus;
    this.metaboliteCorpus = metaboliteCorpus;
  }

  /*
   * Builds the input metabolite corpus and RO corpus
   * Then applies every RO in the corpus to every metabolite in the corpus
   * Puts all of the pairs that succeeded, along with the predicted products, into an L2PredictionCorpus
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

        Molecule[] substrates = new Molecule[]{metabolites.get(inchi)};
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

  private static String[] getInchis(Molecule[] mols) {
    String[] results = new String[mols.length];
    for (int i = 0; i < results.length; i++) {
      results[i] = getInchi(mols[i]);
    }
    return results;
  }

  private static String getInchi(Molecule mol) {
    try {
      return MolExporter.exportToFormat(mol, "inchi:AuxNone");
    } catch (java.io.IOException e) {
      return "GET_INCHI_ERROR";
    }
  }
}

