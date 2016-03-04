package com.act.analysis.similarity;

import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.struc.Molecule;
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
 * is hard-coded to find molecules that might contribute to umami-enhancement behavior.
 *
 * TODO: abstract the common parts of this and FattyAcidSearch into a shared base class.
 */
public class TriaminobenzeneSearch {

  // These live as lists to keep them ordered.
  /*
  public static final List<String> TRIAMINOBENZENE_SMILES = new ArrayList<String>() {{
    add("InChI=1S/C6H9N3/c7-4-1-2-5(8)6(9)3-4/h1-3H,7-9H2");

  }};
*/
  public static final List<String> TRIAMINOBENZENE_SMILES = new ArrayList<String>() {{
    add("c1cc(c(cc1N)N)N");
    add("c1cc(ccc1N)N");
    add("c1cc(cc(c1)N)N");
    add("c1ccccc1N");
  }};

  // Names for each structure, the first eight of which are based on the paper captions.
  public static final List<String> TRIAMINOBENZENE_HEADER_FIELDS = new ArrayList<String>() {{
    add("1,2,4-triaminobenzene");
    add("1,2,4-triaminobenzene-2");
    add("1,2,4-triaminobenzene-3");
    add("1,2,4-triaminobenzene-4");
  }};

  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  public static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL3);
    //SEARCH_OPTIONS.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_DISABLED);
    //SEARCH_OPTIONS.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON);
    //SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_IGNORE);
  }

  private Map<String, Molecule> tabMolecules = new HashMap<>(TRIAMINOBENZENE_SMILES.size());
  private List<String> tabSmiles = new ArrayList<String>();
  private Map<String, MolSearch> tabSearches = new HashMap<>(TRIAMINOBENZENE_SMILES.size());

  public Molecule findLargestFragment(Molecule[] molecules) {
    Molecule largest = null;
    for (Molecule m : molecules) {
      if (largest == null || largest.getAtomCount() < m.getAtomCount()) {
        largest = m;
      }
    }
    return largest;
  }

  public void init() throws IOException, MolFormatException {
    /*
    for (String inchi : TRIAMINOBENZENE_SMILES) {
      Molecule mol = MolImporter.importMol(inchi);
      Molecule largestFragment = findLargestFragment(mol.convertToFrags());
      tabMolecules.put(inchi, largestFragment);
      String smiles = null;
      smiles = MolExporter.exportToFormat(largestFragment, "smiles");
      tabSmiles.add(smiles);
      MolSearch ms = new MolSearch();
      ms.setSearchOptions(SEARCH_OPTIONS);
      ms.setQuery(new MolHandler(smiles, true).getMolecule());
      tabSearches.put(inchi, ms);
    }
    */
    for (String smiles : TRIAMINOBENZENE_SMILES) {
      Molecule mol = MolImporter.importMol(smiles);
      Molecule largestFragment = findLargestFragment(mol.convertToFrags());
      tabMolecules.put(smiles, largestFragment);
      tabSmiles.add(smiles);
      MolSearch ms = new MolSearch();

      ms.setSearchOptions(SEARCH_OPTIONS);
      MolHandler mh = new MolHandler(smiles, false);
      mh.aromatize();
      ms.setQuery(mh.getMolecule());
      tabSearches.put(smiles, ms);
    }
  }

  public Map<String, Double> matchVague(Molecule target) throws Exception {
    Map<String, Double> results = new HashMap<>();
    for (Map.Entry<String, MolSearch> entry : tabSearches.entrySet()) {
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
      results.put(entry.getKey(), Integer.valueOf(longestHit).doubleValue() /
          Integer.valueOf(tabMolecules.get(entry.getKey()).getAtomCount()).doubleValue());
    }

    return results;
  }

  public static void main(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);

    TSVParser parser = new TSVParser();
    parser.parse(new File(args[1]));
    List<String> header = parser.getHeader();

    header.addAll(TRIAMINOBENZENE_HEADER_FIELDS);
    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(args[2]));

    try {
      TriaminobenzeneSearch matcher = new TriaminobenzeneSearch();
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
          target.aromatize();
          Map<String, Double> results = matcher.matchVague(target);
          for (int i = 0; i < TRIAMINOBENZENE_SMILES.size(); i++) {
            row.put(TRIAMINOBENZENE_HEADER_FIELDS.get(i),
                String.format("%.3f", results.get(TRIAMINOBENZENE_SMILES.get(i))));
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
