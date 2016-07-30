package com.act.biointerpretation.sars.sartrees;

import chemaxon.clustering.LibraryMCS;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class LibMcsClustering {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LibMcsClustering.class);

  private static final String PREDICTIONS_FILE =
      "/mnt/shared-data/Gil/untargetted_metabolomics/mass_filtered_predictions";
  private static final Boolean ALL_NODES = false;
  private static final String INCHI_IMPORT_SETTINGS = "inchi";


  public SarTree buildSarTree(LibraryMCS libMcs, List<String> inchiList) throws InterruptedException, IOException {

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

  public Molecule importMolecule(String inchi) throws MolFormatException {
    return MolImporter.importMol(inchi, INCHI_IMPORT_SETTINGS);
  }

  public static void main(String[] args) throws Exception {

  }
}
