package com.act.analysis.similarity;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.struc.MolAtom;
import chemaxon.struc.Molecule;
import chemaxon.struc.MoleculeGraph;
import chemaxon.util.MolHandler;
import com.act.analysis.surfactant.TSVWriter;
import com.act.lcms.db.io.parser.TSVParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is based on Chris's substructure search from the biointerpretation branch.  The list of substructures
 * is hard-coded to find molecules that look like sacharides
 *
 * TODO: abstract the common parts of this and FattyAcidSearch into a shared base class.
 */
public class SaccharideSearch {

  // These live as lists to keep them ordered.
  public static final List<String> SACCHARIDE_SMARTS = new ArrayList<String>() {{
    add("CO[C@H:4]1[C@H:3](O)[C@@H:2](O)[C@H:1](O)O[C@@H:5]1[CH2:6]O");
    add("CO[C@@H:3]1[C@@H:2](O)[C@H:1](O)O[C@H:5]([CH2:6]O)[C@H:4]1O");
    add("CO[CH2:6][C@H:5]1O[C@@H:1](O)[C@H:2](O)[C@@H:3](O)[C@@H:4]1O");
    add("CO[C:1][C@@:2]1(O)O[C@H:5]([CH2:6]O)[C@@H:4](O)[C@@H:3]1O");
    add("CO[CH2:6][C@H:5]1O[C@H:1](O)[C@H:2](O)[C@@H:3](O)[C@@H:4]1O");
    add("CO[C@@H:2]1[C@@H:1](O)O[C@H:5]([CH2:6]O)[C@@H:4](O)[C@@H:3]1O");
    add("CO[C@@H:3]1[C@H:2](O)[C@@H:1](O)O[C@H:5]([CH2:6]O)[C@H:4]1O");
    add("CO[CH2:6][C@H:5]1O[C@H:1](O)[C@@H:2](O)[C@@H:3](O)[C@@H:4]1O");
    add("CO[C@@H:3]1[C@@H:2](O)[C@H:1](O)O[C@H:5]([CH2:6]O)[C@@H:4]1O");
    add("CO[C@@H:4]1[C@H:3](O)[C@@H:2](O)[C@H:1](O)O[C@@H:5]1[CH2:6]O");
    add("CO[CH2:6][C@H:5]1O[C@@H:1](O)[C@H:2](O)[C@@H:3](O)[C@H:4]1O");
  }};

  // Names for each structure, the first eight of which are based on the paper captions.
  public static final List<String> SACCHARIDE_HEADER_FIELDS = new ArrayList<String>() {{
    add("(1-->4)b-D-Glucopyranose");
    add("(1-->3)b-D-Glucopyranose");
    add("(1-->6)b-D-Glucopyranose");
    add("(2-->1)b-D-Fructofuranose");
    add("(1-->6)a-D-Glucopyranose");
    add("(1-->2)a-D-Mannopyranose");
    add("(1-->3)a-D-Mannopyranose");
    add("(1-->6)a-D-Mannopyranose");
    add("(1-->3)b-D-Galactopyranose");
    add("(1-->4)b-D-Galactopyranose");
    add("(1-->6)b-D-Galactopyranose");
  }};

  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  /*
  public static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    // SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL2);
    SEARCH_OPTIONS.setStereoModel(SearchConstants.STEREO_MODEL_COMPREHENSIVE);
    SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_EXACT);
  }*/

  private Map<String, MolSearch> saccharideSearches = new HashMap<>(SACCHARIDE_SMARTS.size());

  public void init() throws IOException, MolFormatException {
    for (String smarts : SACCHARIDE_SMARTS) {
      MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
      searchOptions.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
      searchOptions.setStereoSearchType(SearchConstants.STEREO_EXACT);
      MolSearch ms = new MolSearch();
      ms.setSearchOptions(searchOptions);
      ms.setQuery(new MolHandler(smarts, true).getMolecule());
      saccharideSearches.put(smarts, ms);
    }
  }

  public Map<String, Double> matchVague(Molecule target) throws Exception {
    Map<String, Double> results = new HashMap<>();
    for (Map.Entry<String, MolSearch> entry : saccharideSearches.entrySet()) {
      MolSearch searcher = entry.getValue();
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
      results.put(entry.getKey(), Integer.valueOf(longestHit).doubleValue() > 0.1 ? 1.0 : 0.0);
    }

    return results;
  }

  public static void main(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);

    TSVParser parser = new TSVParser();
    parser.parse(new File(args[1]));
    List<String> header = parser.getHeader();

    header.addAll(SACCHARIDE_HEADER_FIELDS);
    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(args[2]));

    try {
      SaccharideSearch matcher = new SaccharideSearch();
      matcher.init();
      int rowNum = 0;
      int ambiguousStereoChemistryCount = 0;
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

          boolean hasAmbiguousStereoChemistry = false;
          for (int i = 0; i < target.getAtomCount(); i++) {
            if (target.getChirality(i) == MoleculeGraph.PARITY_EITHER) {
              hasAmbiguousStereoChemistry = true;
              break;
            }
          }

          if (hasAmbiguousStereoChemistry) {
            ambiguousStereoChemistryCount++;
            System.err.format("Molecule %s has ambiguous stereochemistry, skipping\n", row.get("id"));
            continue;
          }

          Map<String, Double> results = matcher.matchVague(target);
          for (int i = 0; i < SACCHARIDE_SMARTS.size(); i++) {
            row.put(SACCHARIDE_HEADER_FIELDS.get(i), String.format("%.3f", results.get(SACCHARIDE_SMARTS.get(i))));
          }

          writer.append(row);
          writer.flush();
        } catch (Exception e) {
          System.err.format("Exception on input line %d\n", rowNum);
          throw e;
        }
      }

      System.err.format("Molecules with ambiguous stereochemistry: %d\n", ambiguousStereoChemistryCount);
    } finally {
      writer.close();
    }
  }
}
