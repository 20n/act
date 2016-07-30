package com.act.biointerpretation.sars.sartrees;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.reaction.Reactor;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.l2expansion.L2PredictionCorpus;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import com.act.biointerpretation.sars.CharacterizedGroup;
import com.act.biointerpretation.sars.OneSubstrateSubstructureSar;
import com.act.biointerpretation.sars.Sar;
import com.act.biointerpretation.sars.SarCorpus;
import com.act.biointerpretation.sars.SerializableReactor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LibMcsClustering {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LibMcsClustering.class);

  private static final String PREDICTIONS_FILE =
      "/mnt/shared-data/Gil/untargetted_metabolomics/mass_filtered_predictions";
  private static final Boolean ALL_NODES = false;
  private static final String INCHI_IMPORT_SETTINGS = "inchi";


  public static SarTree buildSarTree(LibraryMCS libMcs, Collection<String> inchiList) throws InterruptedException, IOException {

    for (String inchi : inchiList) {
      try {
        libMcs.addMolecule(importMolecule(inchi));
      } catch (MolFormatException e) {
        LOGGER.warn("Error importing inchi %s:%s", inchi, e.getMessage());
      }
    }

    libMcs.search();
    LibraryMCS.ClusterEnumerator enumerator = libMcs.getClusterEnumerator(ALL_NODES);

    SarTree sarTree = new SarTree();
    while (enumerator.hasNext()) {
      Molecule molecule = enumerator.next();
      String hierId = molecule.getPropertyObject("HierarchyID").toString();
      SarTreeNode thisNode = new SarTreeNode(molecule, hierId);
      sarTree.addNode(thisNode);
    }

    return sarTree;
  }

  public static Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
  }

  public static void main(String[] args) throws Exception {
    L2PredictionCorpus predictionCorpus = L2PredictionCorpus.readPredictionsFromJsonFile(new File(PREDICTIONS_FILE));
    ErosCorpus roCorpus = new ErosCorpus();
    roCorpus.loadValidationCorpus();

    Map<String, L2PredictionCorpus> corpusesByRos = predictionCorpus.splitCorpus(prediction -> prediction.getProjectorName());
    Map<String, SarCorpus> sarCorpusesByRo = new HashMap<>();

    for (String projectorName : corpusesByRos.keySet()) {
      sarCorpusesByRo.put(projectorName, getSarCorpus(projectorName, corpusesByRos.get(projectorName), roCorpus));
    }
  }

  public static SarCorpus getSarCorpus(String projectorName, L2PredictionCorpus predictionCorpus, ErosCorpus roCorpus) throws ReactionException, IOException, InterruptedException {
    Integer roId = Integer.parseInt(projectorName);
    Reactor reactor = roCorpus.getEro(roId).getReactor();
    SerializableReactor serReactor = new SerializableReactor(reactor, roId);

    SarTree sarTree = buildSarTree(new LibraryMCS(), predictionCorpus.getUniqueProductInchis());

    SarCorpus sarCorpus = new SarCorpus();
    for (SarTreeNode node : sarTree.getNodes()) {
      Molecule substructure = node.getSubstructure();
      List<Sar> sarContainer = Arrays.asList(new OneSubstrateSubstructureSar(substructure));
      String name = node.getHierarchyId();
      CharacterizedGroup group = new CharacterizedGroup(name, sarContainer, serReactor);
      sarCorpus.addCharacterizedGroup(group);
    }

    return sarCorpus;
  }
}
