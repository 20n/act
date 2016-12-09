package com.twentyn.search.substructure;


import chemaxon.formats.MolFormatException;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.sss.search.SearchException;
import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


public class SubstructureSearch {
  // TODO: are these options sufficient?  Are there others we might want to use?
  /* Chemaxon exposes a very non-uniform means of configuring substructure search.  Hence the mess of lambdas below.
   * Consumer solves the Function<T, void> problem. */
  private static final Map<String, Consumer<MolSearchOptions>> SEARCH_OPTION_ENABLERS =
      Collections.unmodifiableMap(new HashMap<String, Consumer<MolSearchOptions>>() {{
        put("CHARGE_MATCHING_EXACT", (so -> so.setChargeMatching(SearchConstants.CHARGE_MATCHING_EXACT)));
        put("CHARGE_MATCHING_IGNORE", (so -> so.setChargeMatching(SearchConstants.CHARGE_MATCHING_IGNORE)));
        put("IMPLICIT_H_MATCHING_ENABLED", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_ENABLED)));
        put("IMPLICIT_H_MATCHING_DISABLED", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_DISABLED)));
        put("IMPLICIT_H_MATCHING_IGNORE", (so -> so.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_IGNORE)));
        put("STEREO_EXACT", (so -> so.setStereoSearchType(SearchConstants.STEREO_EXACT)));
        put("STEREO_IGNORE", (so -> so.setStereoSearchType(SearchConstants.STEREO_IGNORE)));
        put("STEREO_MODEL_COMPREHENSIVE", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_COMPREHENSIVE)));
        put("STEREO_MODEL_GLOBAL", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_GLOBAL)));
        put("STEREO_MODEL_LOCAL", (so -> so.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL)));
        put("TAUTOMER_SEARCH_ON", (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON)));
        put("TAUTOMER_SEARCH_OFF", (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_OFF)));
        put("TAUTOMER_SEARCH_ON_IGNORE_TAUTOMERSTEREO",
            (so -> so.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_ON_IGNORE_TAUTOMERSTEREO)));
        put("VAGUE_BOND_OFF", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_OFF)));
        put("VAGUE_BOND_LEVEL_HALF", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL_HALF)));
        put("VAGUE_BOND_LEVEL1", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL1)));
        put("VAGUE_BOND_LEVEL2", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL2)));
        put("VAGUE_BOND_LEVEL3", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL3)));
        put("VAGUE_BOND_LEVEL4", (so -> so.setVagueBondLevel(SearchConstants.VAGUE_BOND_LEVEL4)));
      }});

  private static final MolSearchOptions DEFAULT_SEARCH_OPTIONS = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
  static {
    DEFAULT_SEARCH_OPTIONS.setImplicitHMatching(SearchConstants.IMPLICIT_H_MATCHING_DEFAULT);
    DEFAULT_SEARCH_OPTIONS.setVagueBondLevel(SearchConstants.VAGUE_BOND_DEFAULT);
    DEFAULT_SEARCH_OPTIONS.setTautomerSearch(SearchConstants.TAUTOMER_SEARCH_DEFAULT);
    DEFAULT_SEARCH_OPTIONS.setStereoSearchType(SearchConstants.STEREO_IGNORE); // TODO: is this preferable?
  }

  public SubstructureSearch() {

  }

  public MolSearch constructSearch(String smiles, List<String> extraOpts) throws MolFormatException {
    // Process any custom options.
    MolSearchOptions searchOptions;
    if (extraOpts == null || extraOpts.size() == 0) {
      searchOptions = DEFAULT_SEARCH_OPTIONS;
    } else {
      searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
      // Apply all the specified extra search options using the key -> function mapping above.
      for (String opt : extraOpts) {
        if (!SEARCH_OPTION_ENABLERS.containsKey(opt)) {
          throw new IllegalArgumentException(String.format("Unrecognized search option: %s", opt));
        }
        SEARCH_OPTION_ENABLERS.get(opt).accept(searchOptions);
      }

    }

    // Import the query and set it + the specified or default search options.
    MolSearch ms = new MolSearch();
    ms.setSearchOptions(searchOptions);
    Molecule query = new MolHandler(smiles, true).getMolecule();
    ms.setQuery(query);
    return ms;
  }

  public boolean matchSubstructure(Molecule target, MolSearch search) throws SearchException {
    search.setTarget(target);
    /* hits are arrays of atom ids in the target that matched the query.  If multiple sites in the target matched,
     * then there should be multiple arrays of atom ids (but we don't care since we're just looking for any match). */
    int[][] hits = search.findAll();
    if (hits != null) {
      for (int i = 0; i < hits.length; i++) {
        if (hits[i].length > 0) {
          return true;
        }
      }
    }
    return false;
  }
}
