package com.act.analysis.similarity;

import act.server.DBIterator;
import act.server.MongoDB;
import act.shared.Chemical;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.sss.SearchConstants;
import chemaxon.sss.search.MolSearch;
import chemaxon.sss.search.MolSearchOptions;
import chemaxon.standardizer.Standardizer;
import chemaxon.standardizer.actions.AromatizeAction;
import chemaxon.standardizer.configuration.StandardizerConfiguration;
import chemaxon.struc.Molecule;
import chemaxon.util.MolHandler;
import com.act.utils.TSVParser;
import com.act.utils.TSVWriter;
import com.mongodb.BasicDBObject;
import nu.xom.xinclude.BadEncodingAttributeException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is based on Chris's substructure search from com/act/biointerpretation/operators (see commit
 * e7fc12d7d8017949d83c42aca276bcf1b76fa802).  The list of substructures
 * is hard-coded to find molecules that look like saccharides.
 *
 * TODO: abstract the common parts of this and FattyAcidSearch into a shared base class.
 */
public class BenzeneSearch {

  // These live as lists to keep them ordered.
  public static final List<String> BENZENE_SMARTS = new ArrayList<String>() {{
    add("c1ccccc1");
    add("C1Oc2ccccc2O1");
    add("C1C=Cc2ccccc12");
    add("C1N=Cc2ccccc12");
    add("c1ccc2ccccc2c1");
    add("[C,c]1[C,c][C,c][C,c]2[C,c][C,c][C,c][C,c][C,c]2[C,c]1");
  }};

  // Names for each structure, the first eight of which are based on the paper captions.
  public static final List<String> BENZENE_HEADER_FIELDS = new ArrayList<String>() {{
    add("Aromatized Benzene");
    add("Heterocycles");
    add("Five membered rings");
    add("Imides");
    add("Naptha");
    add("Any double rings");
  }};

  // Note: to relax these search criteria, see https://docs.chemaxon.com/display/jchembase/Bond+specific+search+options.

  private Map<String, MolSearch> benzeneSearches = new HashMap<>(BENZENE_SMARTS.size());

  public void init() throws IOException, MolFormatException {
    for (String smarts : BENZENE_SMARTS) {
      MolSearchOptions searchOptions = new MolSearchOptions(SearchConstants.SUBSTRUCTURE);
      searchOptions.setStereoModel(SearchConstants.STEREO_MODEL_LOCAL);
      searchOptions.setStereoSearchType(SearchConstants.STEREO_EXACT);
      MolSearch ms = new MolSearch();
      ms.setSearchOptions(searchOptions);
      ms.setQuery(new MolHandler(smarts, true).getMolecule());
      benzeneSearches.put(smarts, ms);
    }
  }

  public Map<String, Double> matchVague(Molecule target) throws Exception {
    Map<String, Double> results = new HashMap<>();
    for (Map.Entry<String, MolSearch> entry : benzeneSearches.entrySet()) {
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
      // Normalize all non-zero values to 1.0, and all zero-ish values to 0.0.
      results.put(entry.getKey(), Integer.valueOf(longestHit).doubleValue() > 0.1 ? 1.0 : 0.0);
    }

    return results;
  }

  public static void mainOld(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);

    TSVParser parser = new TSVParser();
    parser.parse(new File(args[1]));
    List<String> header = parser.getHeader();

    header.addAll(BENZENE_HEADER_FIELDS);
    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(args[2]));

    StandardizerConfiguration configuration = new StandardizerConfiguration();
    configuration.addAction(new AromatizeAction(Collections.emptyMap()));
    Standardizer standardizer = new Standardizer(configuration);

    try {
      BenzeneSearch matcher = new BenzeneSearch();
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

          standardizer.standardize(target);

          Map<String, Double> results = matcher.matchVague(target);
          for (int i = 0; i < BENZENE_SMARTS.size(); i++) {
            row.put(BENZENE_HEADER_FIELDS.get(i), String.format("%.3f", results.get(BENZENE_SMARTS.get(i))));
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

  public static void main(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);

    MongoDB db = new MongoDB("localhost", 27017, "marvin");
    DBIterator iter = db.getIteratorOverChemicals("xref.DRUGBANK", new BasicDBObject("$exists", true));

    TSVParser parser = new TSVParser();
    parser.parse(new File(args[1]));
    List<String> header = new ArrayList<String>() {{
      add("_id");
      add("InChI");
      addAll(BENZENE_HEADER_FIELDS);
    }};

    TSVWriter<String, String> writer = new TSVWriter<>(header);
    writer.open(new File(args[2]));

    StandardizerConfiguration configuration = new StandardizerConfiguration();
    configuration.addAction(new AromatizeAction(Collections.emptyMap()));
    Standardizer standardizer = new Standardizer(configuration);

    try {
      BenzeneSearch matcher = new BenzeneSearch();
      matcher.init();
      int ambiguousStereoChemistryCount = 0;
      while (iter.hasNext()) {
        Chemical c = db.getNextChemical(iter);
        try {
          String inchi = c.getInChI();
          Molecule target = null;
          try {
            target = MolImporter.importMol(inchi);
          } catch (Exception e) {
            System.err.format("Skipping molecule %d due to exception: %s\n", c.getUuid(), e.getMessage());
            continue;
          }

          standardizer.standardize(target);

          Map<String, String> row = new HashMap<String, String>() {{
            put("_id", c.getUuid().toString());
            put("InChI", c.getInChI());
          }};

          Map<String, Double> results = matcher.matchVague(target);
          for (int i = 0; i < BENZENE_SMARTS.size(); i++) {
            row.put(BENZENE_HEADER_FIELDS.get(i), String.format("%.3f", results.get(BENZENE_SMARTS.get(i))));
          }

          writer.append(row);
          writer.flush();
        } catch (Exception e) {
          System.err.format("Exception on chemical id %d\n", c.getUuid());
          throw e;
        }
      }

      System.err.format("Molecules with ambiguous stereochemistry: %d\n", ambiguousStereoChemistryCount);
    } finally {
      db.close();
      writer.close();
    }
  }
}
