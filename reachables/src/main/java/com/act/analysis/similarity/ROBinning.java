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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ROBinning {
  private static final Logger LOGGER = LogManager.getFormatterLogger(ROBinning.class);
  private static final int SUBSTRATE_SIDE_OF_REACTION = 0;

  public static final String OPTION_DB = "d";

  public static final String HELP_MESSAGE = StringUtils.join(new String[] {
      "This class does substructure matching of every chemical against the RO corpus and adds the matching substrates of",
      "ROs to the chemical DB"
  }, "");
  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
  static {
    HELP_FORMATTER.setWidth(100);
  }

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_DB)
        .argName("db-name")
        .desc("The database name from which chemicals are read and updated")
        .hasArg().required()
        .longOpt("db-name")
    );
    // Everybody needs a little help from their friends.
    add(Option.builder("h")
        .argName("help")
        .desc("Prints this help message")
        .longOpt("help")
    );
  }};

  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  public static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
  }

  private ErosCorpus erosCorpus;
  private NoSQLAPI api;
  private Map<String, Pair<MolSearch, Set<Integer>>> smileToSearchQuery = new HashMap<>();

  public static void main(String[] args) throws Exception {
    Options opts = new Options();
    for (Option.Builder b : OPTION_BUILDERS) {
      opts.addOption(b.build());
    }

    CommandLine cl = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cl = parser.parse(opts, args);
    } catch (ParseException e) {
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(ROBinning.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    if (cl.hasOption("help")) {
      HELP_FORMATTER.printHelp(ROBinning.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      return;
    }

    String dbName = cl.getOptionValue(OPTION_DB);

    // We read and write to the same database
    NoSQLAPI api = new NoSQLAPI(dbName, dbName);
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
      for (String substrateSmile : extractSubstratesFromRO(smartsNotation)) {
        Pair<MolSearch, Set<Integer>> molSearchListPair = smileToSearchQuery.get(substrateSmile);
        if (molSearchListPair == null) {
          MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
          searchOptions.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
          searchOptions.setStereoSearchType(SearchConstants.STEREO_EXACT);

          MolSearch ms = new MolSearch();
          ms.setSearchOptions(searchOptions);
          ms.setQuery(new MolHandler(substrateSmile, true).getMolecule());

          Set<Integer> newRoList = new HashSet<>();
          molSearchListPair = Pair.of(ms, newRoList);
          smileToSearchQuery.put(substrateSmile, molSearchListPair);
        }

        molSearchListPair.getRight().add(ro.getId());
      }
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
    for (Map.Entry<String, Pair<MolSearch, Set<Integer>>> entry : smileToSearchQuery.entrySet()) {
      MolSearch searcher = entry.getValue().getLeft();
      searcher.setTarget(target);

      // int[][] hits is an array containing the matches as arrays or null if there are no hits.
      // The match arrays contain the atom indexes of the target atoms that match the query atoms
      // (in the order of the appropriate query atoms). If there is substructure match, there atleast is one
      // hit with a length > 0.
      int[][] hits = searcher.findAll();
      if (hits != null) {
        for (int i = 0; i < hits.length; i++) {
          if (hits[i].length > 0) {
            matchedResults.addAll(entry.getValue().getRight());
            break;
          }
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
      List<Integer> matchedRos = processChemical(chem.getInChI());
      if (matchedRos != null) {
        api.getWriteDB().updateChemicalWithRoBinningInformation(chem.getUuid(), matchedRos);
      }
    }
  }

  public List<Integer> processChemical(String inchi) throws SearchException {
    Molecule molecule;
    try {
      molecule = MolImporter.importMol(inchi, "inchi");
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
      return null;
    }
    return processChemical(molecule);
  }

  public List<Integer> processChemical(Molecule molecule) throws SearchException {
    Cleaner.clean(molecule, 2);
    molecule.aromatize(MoleculeGraph.AROM_BASIC);
    List<Integer> results = rosThatMatchTargetMolecule(molecule);
    Collections.sort(results);
    return results;
  }
}
