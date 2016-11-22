package com.act.analysis.synonyms;


import act.installer.pubchem.MeshTermType;
import act.installer.pubchem.PubchemMeshSynonyms;
import act.installer.pubchem.PubchemSynonymType;
import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolExporter;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeImporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// TODO add methods to optimize large queries

public class SynonymsExtractor {

  // We extract the chemicals from this database
  private static final String VALIDATOR_PROFILING_DATABASE = "validator_profiling_2";

  // Default host. If running on a laptop, please set a SSH bridge to access speeakeasy
  private static final String DEFAULT_HOST = "localhost";
  private static final Integer DEFAULT_PORT = 27017;


  private PubchemMeshSynonyms pubchemSynonymsDriver;
  private MongoDB db;

  public SynonymsExtractor() {
    pubchemSynonymsDriver = new PubchemMeshSynonyms();
    db = new MongoDB(DEFAULT_HOST, DEFAULT_PORT, VALIDATOR_PROFILING_DATABASE);
  }

  public static void populateChemaxonSynonyms(Synonyms synonyms) {

    Molecule mol;
    String chemaxonTraditionalName;
    String chemaxonCommonNames;
    try {
      mol = MoleculeImporter.importMolecule(synonyms.getInchi());
      chemaxonTraditionalName = MolExporter.exportToFormat(mol, "name:t");
      synonyms.setChemaxonTraditionalName(chemaxonTraditionalName);
      chemaxonCommonNames = MolExporter.exportToFormat(mol, "name:common,all");
      synonyms.setChemaxonCommonNames(Arrays.asList(chemaxonCommonNames.split("\n")));
    } catch (IOException e) {
    }
  }

  public void populatePubchemSynonyms(Synonyms synonyms) {
    String inchi = synonyms.getInchi();
    String compoundID = pubchemSynonymsDriver.fetchCIDFromInchi(inchi);
    Map<MeshTermType, List<String>> meshSynonyms = pubchemSynonymsDriver.fetchMeshTermsFromCID(compoundID).
        entrySet().stream().
        collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> new ArrayList<>(e.getValue())));
    Map<PubchemSynonymType, List<String>> pubchemSynonyms = pubchemSynonymsDriver.fetchPubchemSynonymsFromCID(compoundID).
        entrySet().stream().
        collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> new ArrayList<>(e.getValue())));
    synonyms.setMeshTerms(meshSynonyms);
    synonyms.setPubchemSynonyms(pubchemSynonyms);
  }

  public void populateBrendaSynonyms(Synonyms synonyms) {
    Chemical c = db.getChemicalFromInChI(synonyms.getInchi());
    if (c != null) {
      List<String> brendaNames = c.getBrendaNames();
      synonyms.setBrendaSynonyms(brendaNames);
    }

  }
}
