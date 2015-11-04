package com.act.lcms.db.analysis;

import com.act.lcms.MassCalculator;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.model.ChemicalAssociatedWithPathway;
import com.act.lcms.db.model.ChemicalOfInterest;
import com.act.lcms.db.model.ConstructEntry;
import com.act.lcms.db.model.CuratedChemical;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.StandardWell;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Utils {

  public static String[] ensureNonNull(String[] val) {
    return val == null ? new String[0] : val;
  }

  public static CuratedChemical extractTargetForConstruct(DB db, String compositionId) throws SQLException {
    ConstructEntry cme =
        ConstructEntry.getCompositionMapEntryByCompositionId(db, compositionId);
    if (cme == null) {
      System.err.format("WARNING: No construct -> chemical mapping for %s\n", compositionId);
      return null;
    }
    CuratedChemical cc = CuratedChemical.getCuratedChemicalByName(db, cme.getTarget());
    if (cc == null) {
      System.err.format("WARNING: No curated chemical entry for %s/%s\n", cme.getCompositionId(), cme.getTarget());
      return null;
    }
    if (cc.getMass() <= 0.0d) {
      System.err.format("WARNING: Invalid mass for chemical %s/%s (%f)\n",
          cme.getCompositionId(), cc.getName(), cc.getMass());
      return null;
    }
    return cc;
  }

  /**
   * Finds the target chemical for a given set of wells, assuming there will be exactly one shared for all positive
   * wells in the list.
   * @param db The database in which to look up constructs/chemicals.
   * @param positiveWells The list of wells whose standards to find.
   * @return An object representing the target chemical for the specified wells.
   * @throws SQLException
   */
  public static Set<CuratedChemical> extractTargetsForWells(DB db, List<LCMSWell> positiveWells) throws SQLException {
    Set<CuratedChemical> chemicals = new HashSet<>();
    for (LCMSWell well : positiveWells) {
      CuratedChemical cc = extractTargetForConstruct(db, well.getComposition());
      if (cc != null) {
        chemicals.add(cc);
      }
    }
    return chemicals;
  }

  /**
   * Finds all chemical targets for a set of LCMS wells.  Throws an IllegalArgumentException if more than one targets
   * are shared by the wells.
   * @param db The DB to query for information about the wells/targets.
   * @param wells A set of wells whose targets to scan.
   * @return The single shared target of all the wells, or null.
   * @throws SQLException
   * @throws IllegalArgumentException Thrown when the wells share more than one target chemical.
   */
  public static CuratedChemical requireOneTarget(DB db, List<LCMSWell> wells)
      throws SQLException, IllegalArgumentException {
    Set<CuratedChemical> chemicals = extractTargetsForWells(db, wells);
    if (chemicals.size() > 1) {
      // TODO: is there a foreach approach that we can use here that won't break backwards compatibility?
      List<String> chemicalNames = new ArrayList<>(chemicals.size());
      for (CuratedChemical chemical : chemicals) {
        chemicalNames.add(chemical.getName());
      }
      throw new IllegalArgumentException(
          String.format("Found multiple target chemicals where one required: %s", StringUtils.join(chemicalNames, ", "))
      );
    } else if (chemicals.size() < 1) {
      return null;
    }
    return chemicals.iterator().next();
  }

  /**
   * Filters a set of metlin masses by include/exclude ion names.
   * @param metlinMassesPreFilter A map of ion names to masses.
   * @param includeIons A set of ion names to include (all others will be excluded).
   * @param excludeIons A set of ion names to exclude.  Exclusion takes priority over inclusion.
   * @return A map of ion names to masses filtered by the include/exclude sets.
   */
  public static Map<String, Double> filterMasses(Map<String, Double> metlinMassesPreFilter,
                                                  Set<String> includeIons, Set<String> excludeIons) {
    // Don't filter if there's nothing by which to filter.
    if ((includeIons == null || includeIons.size() == 0) && (excludeIons == null || excludeIons.size() == 0)) {
      return metlinMassesPreFilter;
    }
    // Create a fresh map and add from the old one as we go.  (Could also copy and remove, but that seems weird.)
    Map<String, Double> metlinMasses = new HashMap<>(metlinMassesPreFilter.size());
    /* Iterate over the old copy to reduce the risk of concurrent modification exceptions.
     * Note: this is not thread safe. */
    for (Map.Entry<String, Double> entry : metlinMassesPreFilter.entrySet()) {
      // Skip all exclude values immediately.
      if (excludeIons != null && excludeIons.contains(entry.getKey())) {
        continue;
      }
      // If includeIons is defined, only keep those
      if (includeIons == null || includeIons.contains(entry.getKey())) {
        metlinMasses.put(entry.getKey(), entry.getValue());
      }
    }

    return metlinMasses;
  }

  /**
   * Find a well containing the specified chemical in the plate with a given barcode.
   * @param db A DB containing plate/well data.
   * @param standardPlateBarcode The barcode of the plate in which to search.
   * @param standardName The name of the chemical to find.
   * @return The StandardWell in the specified plate that contains the specified chemical.
   * @throws SQLException
   * @throws IllegalArgumentException thrown when the plate is invalid or the chemical cannot be found therein.
   */
  public static StandardWell extractStandardWellFromPlate(DB db, String standardPlateBarcode, String standardName)
      throws SQLException, IllegalArgumentException {
    Plate standardPlate = Plate.getPlateByBarcode(db, standardPlateBarcode);
    if (standardPlate == null) {
      throw new IllegalArgumentException(
          String.format("Unable to find standard plate with barcode %s", standardPlateBarcode));
    }
    if (standardPlate.getContentType() != Plate.CONTENT_TYPE.STANDARD) {
      throw new IllegalArgumentException(
          String.format("Plate with barcode %s has content type %s, expected %s",
              standardPlateBarcode, standardPlate.getContentType(), Plate.CONTENT_TYPE.STANDARD)
      );
    }
    List<StandardWell> standardWells = StandardWell.getInstance().getByPlateId(db, standardPlate.getId());
    for (StandardWell well : standardWells) {
      if (standardName.equals(well.getChemical())) {
        System.out.format("Found matching standard well at %s (%s)\n", well.getCoordinatesString(), well.getChemical());
        return well;
      }
    }
    throw new IllegalArgumentException(
      String.format("Unable to find standard chemical %s in plate %s", standardName, standardPlateBarcode)
    );
  }


  /**
   * Parses a mass value from a string (like 123.456), or searches for a chemical by name and computs the mass.
   * @param db A DB to query for chemicals if massStr does not contain a number.
   * @param massStr A numeric mass value or a chemical name whose mass to find.
   * @return A pair containing a textual description of the value used and a mass value.
   * @throws SQLException
   * @throws IllegalArgumentException Thrown when the massStr can't be parsed or found in the DB.
   */
  public static Pair<String, Double> extractMassFromString(DB db, String massStr)
      throws SQLException, IllegalArgumentException {
    Pair<String, Double> searchMZ;
    try {
      Double mz = Double.parseDouble(massStr);
      return Pair.of("raw-m/z", mz);
    } catch (IllegalArgumentException e) {
      CuratedChemical targetChemical = CuratedChemical.getCuratedChemicalByName(db, massStr);
      if (targetChemical != null) {
        Double mz = targetChemical.getMass();
        return Pair.of(massStr, mz);
      }

      List<ChemicalOfInterest> chemicalsOfInterest =
          ChemicalOfInterest.getInstance().getChemicalOfInterestByName(db, massStr);
      if (chemicalsOfInterest == null || chemicalsOfInterest.size() == 0) {
        throw new IllegalArgumentException(
            String.format("Unable to parse or find chemical name for string: %s", massStr));
      }
      if (chemicalsOfInterest.size() != 1) {
        System.err.format("WARNING: found multiple chemicals of interest for name '%s', using first\n", massStr);
      }
      ChemicalOfInterest chem = chemicalsOfInterest.get(0);
      Double mz = MassCalculator.calculateMass(chem.getInchi());
      System.out.format("Using reference M/Z for specified chemical %s (%f)\n", chem.getName(), mz);
      return Pair.of(massStr, mz);
    }
  }

  /**
   * Produces an ordered list of chemicals and their masses that represent the intermediate and side-reaction products
   * of the pathway encoded in a particular construct.  These are returned as a list rather than a hash to keep them in
   * pathway order (from last/highest to first/lowest intermediate or side-reaction).
   * @param db The database in which to search for chemicals associated with the specific construct.
   * @param constructId The construct whose products to search for.
   * @return A pathway-ordered list of produced chemicals and their masses.
   * @throws SQLException
   */
  public static List<Pair<ChemicalAssociatedWithPathway, Double>> extractMassesForChemicalsAssociatedWithConstruct(
      DB db, String constructId) throws SQLException {
    List<Pair<ChemicalAssociatedWithPathway, Double>> results = new ArrayList<>();
    // Assumes the chems come back in index-sorted order, which should be guaranteed by the query that this call runs.
    List<ChemicalAssociatedWithPathway> products =
        ChemicalAssociatedWithPathway.getInstance().getChemicalsAssociatedWithPathwayByConstructId(db, constructId);
    for (ChemicalAssociatedWithPathway product : products) {
      String chemName = product.getChemical();
      CuratedChemical curatedChemical = CuratedChemical.getCuratedChemicalByName(db, chemName);
      // Attempt to find the product in the list of curated chemicals, then fall back to mass computation by InChI.
      if (curatedChemical != null) {
        results.add(Pair.of(product, curatedChemical.getMass()));
        continue;
      }

      Double mass = ChemicalOfInterest.getInstance().getAnyAvailableMassByName(db, chemName);
      if (mass == null) {
        System.err.format("ERROR: no usable chemical entries found for %s, skipping\n", chemName);
        continue;
      }

      results.add(Pair.of(product, mass));
    }
    return results;
  }



  /**
   * Given arrays of strain and/or construct ids, find all LCMS wells matching those strains/constructs.  If a set of
   * plates is specified, only wells in plates that are in that set will be considered.
   * @param db The DB to query for well information.
   * @param searchStrains A list of strain ids (MSIDs) for which to search.
   * @param searchConstructs A list of construct ids for which to search.
   * @param restrictToPlateIds An optional set of plates on which to filter wells.
   * @return A list of LCMS wells containing the specified strains/constructs, and the set of plate ids for those wells.
   * @throws SQLException
   */
  public static Pair<List<LCMSWell>, Set<Integer>> extractWellsAndPlateIds(
      DB db, String[] searchStrains, String[] searchConstructs, Set<Integer> restrictToPlateIds) throws SQLException {
    String[] strains = ensureNonNull(searchStrains);
    String[] constructs = ensureNonNull(searchConstructs);

    List<LCMSWell> matchingWells = new ArrayList<>();
    Set<Integer> seenWellIds = new HashSet<>();
    Set<Integer> seenPlateIds = new HashSet<>();
    for (String s : strains) {
      List<LCMSWell> res = LCMSWell.getInstance().getByStrain(db, s);
      for (LCMSWell well : res) {
        if (!restrictToPlateIds.contains(well.getPlateId())) {
          continue;
        }
        if (!seenWellIds.contains(well.getId())) {
          matchingWells.add(well);
          seenWellIds.add(well.getId());
          seenPlateIds.add(well.getPlateId());
        }
      }
    }
    for (String c : constructs) {
      List<LCMSWell> res = LCMSWell.getInstance().getByConstructID(db, c);
      for (LCMSWell well : res) {
        if (!restrictToPlateIds.contains(well.getPlateId())) {
          continue;
        }
        if (!seenWellIds.contains(well.getId())) {
          matchingWells.add(well);
          seenWellIds.add(well.getId());
          seenPlateIds.add(well.getPlateId());
        }
      }
    }
    return Pair.of(matchingWells, seenPlateIds);
  }
}
