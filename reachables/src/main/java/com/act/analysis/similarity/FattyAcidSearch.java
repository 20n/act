package com.act.analysis.similarity;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;
import com.act.utils.TSVWriter;
import com.act.lcms.db.io.parser.TSVParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is based on Chris's substructure search from com/act/biointerpretation/operators (see commit
 * e7fc12d7d8017949d83c42aca276bcf1b76fa802).  The list of substructures is hard-coded to find molecules that are based
 * on C5-C12 fatty acids.
 *
 * TODO: abstract the common parts of this and UmamiSearch into a shared base class.
 */
public class FattyAcidSearch {

  public static final List<String> ACIDS_5_THROUGH_12 = new ArrayList<String>() {{
    add("[CH3][CH2][CH2][CH2]C(=O)O");
    add("[CH3][CH2][CH2][CH2][CH2]C(=O)O");
    add("[CH3][CH2][CH2][CH2][CH2][CH2]C(=O)O");
    add("[CH3][CH2][CH2][CH2][CH2][CH2][CH2]C(=O)O");
    add("[CH3][CH2][CH2][CH2][CH2][CH2][CH2][CH2]C(=O)O");
    add("[CH3][CH2][CH2][CH2][CH2][CH2][CH2][CH2][CH2]C(=O)O");
    add("[CH3][CH2][CH2][CH2][CH2][CH2][CH2][CH2][CH2][CH2]C(=O)O");
    add("[CH3][CH2][CH2][CH2][CH2][CH2][CH2][CH2][CH2][CH2][CH2]C(=O)O");
  }};

  public static final Map<String, Double> ACIDS_5_THROUGH_12_LENGTH = new HashMap<String, Double>() {{
    // Ignore hydrogens, just count other molecules.  TODO: let Marvin do the counting.
    put(ACIDS_5_THROUGH_12.get(0), 7.0);
    put(ACIDS_5_THROUGH_12.get(1), 8.0);
    put(ACIDS_5_THROUGH_12.get(2), 9.0);
    put(ACIDS_5_THROUGH_12.get(3), 10.0);
    put(ACIDS_5_THROUGH_12.get(4), 11.0);
    put(ACIDS_5_THROUGH_12.get(5), 12.0);
    put(ACIDS_5_THROUGH_12.get(6), 13.0);
    put(ACIDS_5_THROUGH_12.get(7), 14.0);
  }};

  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  public static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
  }

  private Map<String, MolSearch> fattyAcidSearches = new HashMap<>(ACIDS_5_THROUGH_12.size());
  void init() throws MolFormatException {
    for (String smarts : ACIDS_5_THROUGH_12) {
      MolSearch ms = new MolSearch();
      ms.setSearchOptions(SEARCH_OPTIONS);
      ms.setQuery(new MolHandler(smarts, true).getMolecule());
      fattyAcidSearches.put(smarts, ms);
    }
  }

  public Map<String, Double> matchVague(Molecule target) throws Exception {
    MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
    searchOptions.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);

    Map<String, Double> results = new HashMap<>();
    for (Map.Entry<String, MolSearch> entry : fattyAcidSearches.entrySet()) {
      MolSearch searcher = entry.getValue();
      searcher.setTarget(target);
      /* This returns a list of atom-to-atom mappings identified by the searcher.  If the entire substructure was found
       * in the target molecule, the length of the array should match the number of atoms in the query.  See
       * https://www.chemaxon.com/jchem/doc/dev/java/api/index.html?chemaxon/sss/search/MolSearch.html
       * for more details. */
      int[][] hits = searcher.findAll();
      int longestHit = 0;
      if (hits != null) {
        for (int i = 0; i < hits.length; i++) {
          if (hits[i].length > longestHit) {
            longestHit = hits[i].length;
          }
        }
      }
      results.put(entry.getKey(), Integer.valueOf(longestHit).doubleValue() /
          ACIDS_5_THROUGH_12_LENGTH.get(entry.getKey()));
    }

    return results;
  }

  public static void main(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);

    TSVParser parser = new TSVParser();
    parser.parse(new File(args[1]));
    List<String> header = parser.getHeader();

    final List<String> fattyAcidHeaderFields = new ArrayList<String>() {{
      add("fa_5");
      add("fa_6");
      add("fa_7");
      add("fa_8");
      add("fa_9");
      add("fa_10");
      add("fa_11");
      add("fa_12");
    }};

    header.addAll(fattyAcidHeaderFields);
    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(args[2]));

    try {
      FattyAcidSearch matcher = new FattyAcidSearch();
      matcher.init();
      int rowNum = 0;
      for (Map<String, String> row : parser.getResults()) {
        rowNum++;
        try {
          String inchi = row.get("inchi");
          Molecule target = null;
          try {
            target = MolImporter.importMol(inchi);
          } catch (Exception e) {
            System.err.format("Skipping molecule %d due to exception: %s\n", rowNum, e.getMessage());
            continue;
          }
          Map<String, Double> results = matcher.matchVague(target);
          for (int i = 0; i < ACIDS_5_THROUGH_12.size(); i++) {
            row.put(fattyAcidHeaderFields.get(i), String.format("%.3f", results.get(ACIDS_5_THROUGH_12.get(i))));
          }

          writer.append(row);
          writer.flush();
        } catch (Exception e) {
          System.err.format("Exception on input line %d\n", rowNum);
          throw e;
        }
      }
    } finally {
      writer.close();
    }
  }
}