package com.act.analysis.similarity;

import act.server.NoSQLAPI;
import act.shared.Chemical;
import chemaxon.formats.MolImporter;
import chemaxon.reaction.ReactionException;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
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
    ROBinning roBinning = new ROBinning();
    roBinning.init(api);
    roBinning.processChemicals();
  }

  public void init(NoSQLAPI apiArg) throws IOException {
    api = apiArg;
    erosCorpus = new ErosCorpus();
    erosCorpus.loadCorpus();

    for (Ero ro : erosCorpus.getRos()) {
      String smartsNotation = ro.getRo();
      Map<String, MolSearch> chemicalToMolSearch = new HashMap<>();

      for (String chemical : roToListOfSmilePatterns(smartsNotation)) {
        MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
        searchOptions.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
        searchOptions.setStereoSearchType(SearchConstants.STEREO_EXACT);

        MolSearch ms = new MolSearch();
        ms.setSearchOptions(searchOptions);
        ms.setQuery(new MolHandler(chemical, true).getMolecule());
        chemicalToMolSearch.put(chemical, ms);
      }
      roToSearchQuery.put(ro.getId(), chemicalToMolSearch);
    }
  }

  private List<String> roToListOfSmilePatterns(String ro) {
    String[] splitReaction = ro.split(">>");
    List<String> chemicalsInSmilesNotation = new ArrayList<>();
    String substrateSide = splitReaction[0];
    String[] individualChemical = substrateSide.split("\\.");
    for (String chemical : individualChemical) {
      chemicalsInSmilesNotation.add(chemical.replaceAll(":[0-9]+", ""));
    }
    return chemicalsInSmilesNotation;
  }

  private List<Integer> matchVague(Molecule target) throws SearchException {
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

      List<Integer> matchedRos = matchVague(molecule);
      api.getWriteDB().updateChemicalWithRoBinningInformation(chem.getUuid(), matchedRos);
    }
  }
}
