package com.act.analysis.similarity;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;
import com.act.analysis.logp.TSVWriter;
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
public class UmamiSearch {

  // These live as lists to keep them ordered.
  public static final List<String> UMAMI_INCHIS = new ArrayList<String>() {{
    // These structures are from http://dx.doi.org/10.1016/B978-0-12-384947-2.00297-X.
    add("InChI=1/C5H9NO4/c6-3(5(9)10)1-2-4(7)8/h3H,1-2,6H2,(H,7,8)(H,9,10)");
    add("InChI=1/C5H9NO4.Na/c6-3(5(9)10)1-2-4(7)8;/h3H,1-2,6H2,(H,7,8)(H,9,10);/q;+1/p-1");
    add("InChI=1/C5H9NO4.Na/c6-3(5(9)10)1-2-4(7)8;/h3H,1-2,6H2,(H,7,8)(H,9,10);/q;+1/p-2");
    add("InChI=1/C5H9NO5/c6-4(5(10)11)2(7)1-3(8)9/h2,4,7H,1,6H2,(H,8,9)(H,10,11)");
    add("InChI=1/C4H9NO5S.Na/c5-3(4(6)7)1-2-11(8,9)10;/h3H,1-2,5H2,(H,6,7)(H,8,9,10);/q;+1/p-1");
    add("InChI=1/C4H7NO4.Na/c5-2(4(8)9)1-3(6)7;/h2H,1,5H2,(H,6,7)(H,8,9);/q;+1/p-1");
    add("InChI=1/C5H8N2O4.Na/c6-4(5(9)10)2-1-3(8)7-11-2;/h2,4H,1,6H2,(H,7,8)(H,9,10);/q;+1/p-1");
    add("InChI=1/C5H6N2O4.Na/c6-4(5(9)10)2-1-3(8)7-11-2;/h1,4H,6H2,(H,7,8)(H,9,10);/q;+1/p-1");
    add("InChI=1S/C5H9NO4/c6-3(5(9)10)1-2-4(7)8/h3H,1-2,6H2,(H,7,8)(H,9,10)");
    add("InChI=1S/C5H9NO4.Na/c6-3(5(9)10)1-2-4(7)8;/h3H,1-2,6H2,(H,7,8)(H,9,10);/q;+1/p-1/t3-;/m0./s1");
    add("InChI=1S/C5H9NO4.Na/c6-3(5(9)10)1-2-4(7)8;/h3H,1-2,6H2,(H,7,8)(H,9,10);/q;+1/p-1/t3-;/m0./s1");
    add("InChI=1S/2C5H9NO4.Ca/c2*6-3(5(9)10)1-2-4(7)8;/h2*3H,1-2,6H2,(H,7,8)(H,9,10);/q;;+2/p-2/t2*3-;/m00./s1");
    add("InChI=1S/C5H9NO4.H3N/c6-3(5(9)10)1-2-4(7)8;/h3H,1-2,6H2,(H,7,8)(H,9,10);1H3");
    add("InChI=1S/2C5H9NO4.Mg/c2*6-3(5(9)10)1-2-4(7)8;/h2*3H,1-2,6H2,(H,7,8)(H,9,10);/q;;+2/p-2");
    add("InChI=1S/C10H14N5O8P/c11-10-13-7-4(8(18)14-10)12-2-15(7)9-6(17)5(16)3(23-9)1-22-24(19,20)21/h2-3,5-6,9,16-17H,1H2,(H2,19,20,21)(H3,11,13,14,18)/t3-,5-,6-,9-/m1/s1");
    add("InChI=1S/C10H14N5O8P.2Na/c11-10-13-7-4(8(18)14-10)12-2-15(7)9-6(17)5(16)3(23-9)1-22-24(19,20)21;;/h2-3,5-6,9,16-17H,1H2,(H2,19,20,21)(H3,11,13,14,18);;/q;2*+1/p-2/t3-,5-,6-,9-;;/m1../s1");
    add("InChI=1S/C10H14N5O8P.2K/c11-10-13-7-4(8(18)14-10)12-2-15(7)9-6(17)5(16)3(23-9)1-22-24(19,20)21;;/h2-3,5-6,9,16-17H,1H2,(H2,19,20,21)(H3,11,13,14,18);;/q;2*+1/p-2/t3-,5-,6-,9-;;/m1../s1");
    add("InChI=1S/C10H14N5O8P.Ca/c11-10-13-7-4(8(18)14-10)12-2-15(7)9-6(17)5(16)3(23-9)1-22-24(19,20)21;/h2-3,5-6,9,16-17H,1H2,(H2,19,20,21)(H3,11,13,14,18);/q;+2/p-2/t3-,5-,6-,9-;/m1./s1");
    add("InChI=1S/C10H13N4O8P/c15-6-4(1-21-23(18,19)20)22-10(7(6)16)14-3-13-5-8(14)11-2-12-9(5)17/h2-4,6-7,10,15-16H,1H2,(H,11,12,17)(H2,18,19,20)/t4-,6-,7-,10-/m1/s1");
    add("InChI=1S/C10H13N4O8P.2Na/c15-6-4(1-21-23(18,19)20)22-10(7(6)16)14-3-13-5-8(14)11-2-12-9(5)17;;/h2-4,6-7,10,15-16H,1H2,(H,11,12,17)(H2,18,19,20);;/q;2*+1/p-2/t4-,6-,7-,10-;;/m1../s1");
    add("InChI=1S/C10H13N4O8P.Ca/c15-6-4(1-21-23(18,19)20)22-10(7(6)16)14-3-13-5-8(14)11-2-12-9(5)17;/h2-4,6-7,10,15-16H,1H2,(H,11,12,17)(H2,18,19,20);/q;+2/p-2/t4-,6-,7-,10-;/m1./s1");
    add("InChI=1S/C4H8O3/c5-3-1-2-4(6)7/h5H,1-3H2,(H,6,7)");
    add("InChI=1S/C5H10O3/c6-4-2-1-3-5(7)8/h6H,1-4H2,(H,7,8)");
    add("InChI=1S/C4H10O2S/c5-3-1-2-4-7-6/h5,7H,1-4H2");
    add("InChI=1S/C3H8O2S/c4-2-1-3-6-5/h4,6H,1-3H2");
  }};

  // Hack to fix InChI canonicalization messing with smiles.
  public static final Map<String, String> UMAMI_OVERRIDE_SMILES = new HashMap<String, String>() {{
    put("InChI=1/C5H8N2O4.Na/c6-4(5(9)10)2-1-3(8)7-11-2;/h2,4H,1,6H2,(H,7,8)(H,9,10);/q;+1/p-1", "NC(C1CC(=O)NO1)C([O-])=O");
    put("InChI=1/C5H6N2O4.Na/c6-4(5(9)10)2-1-3(8)7-11-2;/h1,4H,6H2,(H,7,8)(H,9,10);/q;+1/p-1", "NC(C([O-])=O)C1=CC(O)=NO1");
    put("InChI=1S/C4H8O3/c5-3-1-2-4(6)7/h5H,1-3H2,(H,6,7)", "OCCCC(=O)[OH]");
    put("InChI=1S/C5H10O3/c6-4-2-1-3-5(7)8/h6H,1-4H2,(H,7,8)", "OCCCCC(=O)[OH]");
    put("InChI=1S/C4H10O2S/c5-3-1-2-4-7-6/h5,7H,1-4H2", "OCCCCS(=O)");
      put("InChI=1S/C3H8O2S/c4-2-1-3-6-5/h4,6H,1-3H2", "OCCCS(=O)");
  }};

  // Names for each structure, the first eight of which are based on the paper captions.
  public static final List<String> UMAMI_HEADER_FIELDS = new ArrayList<String>() {{
    add("example_a");
    add("example_b");
    add("example_c");
    add("example_d");
    add("example_e");
    add("example_f");
    add("example_g");
    add("example_h");
    add("glutamic_acid");
    add("msg");
    add("monopotassium_glutamate");
    add("calcium_diglutamate");
    add("monoammonium_glutamate");
    add("magnesium_diglutamate");
    add("guanosine_monophosphate");
    add("disodium_guanylate");
    add("dipotassium_guanylate");
    add("calcium_guanylate");
    add("inosinic_acid");
    add("disodium_inosinate");
    add("calcium_inosinate");
    add("chris_example_1");
    add("chris_example_2");
    add("chris_example_3");
    add("chris_example_4");
  }};

  // From https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.
  public static final MolSearchOptions SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);
  }

  private Map<String, Molecule> umamiMolecules = new HashMap<>(UMAMI_INCHIS.size());
  private List<String> umamiSmiles = new ArrayList<String>();
  private Map<String, MolSearch> umamiSearches = new HashMap<>(UMAMI_INCHIS.size());

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
    for (String inchi : UMAMI_INCHIS) {
      Molecule mol = MolImporter.importMol(inchi);
      Molecule largestFragment = findLargestFragment(mol.convertToFrags());
      umamiMolecules.put(inchi, largestFragment);
      String smiles = null;
      if (UMAMI_OVERRIDE_SMILES.containsKey(inchi)) {
        System.err.format("Using override smiles for %s\n", inchi);
        smiles = UMAMI_OVERRIDE_SMILES.get(inchi);
      } else {
        smiles = MolExporter.exportToFormat(largestFragment, "smiles");
      }
      umamiSmiles.add(smiles);
      MolSearch ms = new MolSearch();
      ms.setSearchOptions(SEARCH_OPTIONS);
      ms.setQuery(new MolHandler(smiles, true).getMolecule());
      umamiSearches.put(inchi, ms);
    }
  }

  public Map<String, Double> matchVague(Molecule target) throws Exception {
    MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
    searchOptions.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4);

    Map<String, Double> results = new HashMap<>();
    for (Map.Entry<String, MolSearch> entry : umamiSearches.entrySet()) {
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
          Integer.valueOf(umamiMolecules.get(entry.getKey()).getAtomCount()).doubleValue());
    }

    return results;
  }

  public static void main(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);

    TSVParser parser = new TSVParser();
    parser.parse(new File(args[1]));
    List<String> header = parser.getHeader();

    header.addAll(UMAMI_HEADER_FIELDS);
    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(args[2]));

    try {
      UmamiSearch matcher = new UmamiSearch();
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
          for (int i = 0; i < UMAMI_INCHIS.size(); i++) {
            row.put(UMAMI_HEADER_FIELDS.get(i), String.format("%.3f", results.get(UMAMI_INCHIS.get(i))));
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
