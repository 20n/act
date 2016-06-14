package com.act.analysis.similarity;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import chemaxon.util.MolHandler;
import com.act.biointerpretation.mechanisminspection.Ero;
import com.act.biointerpretation.mechanisminspection.ErosCorpus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ROBinning {
  private static final int SUBSTRATE_SIDE_OF_REACTION = 0;
  private static final Logger LOGGER = LogManager.getFormatterLogger(ROBinning.class);
  private ErosCorpus erosCorpus;
  private NoSQLAPI api;

  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  public static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
  }
  private Map<Integer, Map<String, MolSearch>> roToSearchQuery = new HashMap<>();

  public static void main(String[] args) throws Exception {
    NoSQLAPI api = new NoSQLAPI("jarvis", "jarvis");
    ErosCorpus erosCorpus = new ErosCorpus();
    erosCorpus.loadCorpus();

    ROBinning roBinning = new ROBinning(erosCorpus, api);
    roBinning.init();
    roBinning.processChemicals();
  }

  public ROBinning(ErosCorpus loadedCorpus, NoSQLAPI noSQLAPI) {
    erosCorpus = loadedCorpus;
    api = noSQLAPI;
  }

  public void init() throws MolFormatException {
    for (Ero ro : erosCorpus.getRos()) {
      String smartsNotation = ro.getRo();
      Map<String, MolSearch> chemicalToMolSearch = new HashMap<>();

      for (String substrateSmile : extractSubstratesFromRO(smartsNotation)) {
        MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
        searchOptions.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
        searchOptions.setStereoSearchType(SearchConstants.STEREO_EXACT);

        MolSearch ms = new MolSearch();
        ms.setSearchOptions(searchOptions);
        ms.setQuery(new MolHandler(substrateSmile, true).getMolecule());
        chemicalToMolSearch.put(substrateSmile, ms);
      }
      roToSearchQuery.put(ro.getId(), chemicalToMolSearch);
    }
  }

  private List<String> extractSubstratesFromRO(String ro) {
    String[] splitReaction = ro.split(">>");
    List<String> chemicalsInSmilesNotation = new ArrayList<>();
    String substrateSide = splitReaction[SUBSTRATE_SIDE_OF_REACTION];

    // Split the substrate side of the reaction to it's individual components. A reaction is structured as such:
    // molA.molB>>molC.molD, so we have to split on the '.' on the substrate side.
    String[] substrateSmiles = substrateSide.split("\\.");
    for (String substrateSmile : substrateSmiles) {
      // Remove all the redundant chemical labeling from the processed RO.
      substrateSmile = substrateSmile.replaceAll(":[0-9]+", "");
      chemicalsInSmilesNotation.add(substrateSmile);
    }
    return chemicalsInSmilesNotation;
  }

  private List<Integer> rosThatMatchTargetMolecule(Molecule target) throws SearchException {
    Set<Integer> matchedResults = new HashSet<>();
    for (Map.Entry<Integer, Map<String, MolSearch>> entry : roToSearchQuery.entrySet()) {
      for (Map.Entry<String, MolSearch> chemToPattern : entry.getValue().entrySet()) {
        MolSearch searcher = chemToPattern.getValue();
        searcher.setTarget(target);
        int[][] hits = searcher.findAll();
        int longestHit = 0;
        if (hits != null) {
          for (int i = 0; i < hits.length; i++) {
            if (hits[i].length > longestHit) {
              longestHit = hits[i].length;
            }
          }
        }

        if (Integer.valueOf(longestHit).doubleValue() > 0.1) {
          matchedResults.add(entry.getKey());
        }
      }
    }

    List<Integer> result = new ArrayList<>();
    result.addAll(matchedResults);
    return result;
  }

  protected void processChemicals() throws IOException, ReactionException, SearchException {
    Iterator<Chemical> chemicals = api.readChemsFromInKnowledgeGraph();
    while (chemicals.hasNext()) {
      Chemical chem = chemicals.next();
      Molecule molecule;
      try {
        molecule = MolImporter.importMol(chem.getInChI(), "inchi");
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
        continue;
      }

      Cleaner.clean(molecule, 2);
      molecule.aromatize(MoleculeGraph.AROM_BASIC);

      List<Integer> matchedRos = rosThatMatchTargetMolecule(molecule);
      api.getWriteDB().updateChemicalWithRoBinningInformation(chem.getUuid(), matchedRos);
    }
  }

  public List<Integer> processOneChemical(String inchi) throws SearchException {
    Molecule molecule;
    try {
      molecule = MolImporter.importMol(inchi, "inchi");
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
      return null;
    }

    Cleaner.clean(molecule, 2);
    molecule.aromatize(MoleculeGraph.AROM_BASIC);
    return rosThatMatchTargetMolecule(molecule);
  }
}
