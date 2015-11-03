package com.act.lcms.db.analysis;

import com.act.lcms.MS1;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.model.CuratedChemical;
import com.act.lcms.db.model.FeedingLCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.ScanFile;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeedingAnalysis {
  private static void performFeedingAnalysis(DB db, String lcmsDir,
                                             Set<String> searchIons, String searchMassStr, String plateBarcode,
                                             String strainOrConstruct, String extract, String feedingCondition,
                                             String outPrefix, String fmt)
      throws SQLException, Exception {
    Plate p = Plate.getPlateByBarcode(db, plateBarcode);
    if (p == null) {
      throw new RuntimeException(String.format("Unable to find plate with barcode %s", plateBarcode));
    }
    if (p.getContentType() != Plate.CONTENT_TYPE.FEEDING_LCMS) {
      throw new RuntimeException(String.format("Plate with barcode %s is not a feeding plate (%s)",
          plateBarcode, p.getContentType()));
    }
    List<FeedingLCMSWell> allPlateWells = FeedingLCMSWell.getInstance().getFeedingLCMSWellByPlateId(db, p.getId());
    if (allPlateWells == null || allPlateWells.size() == 0) {
      throw new RuntimeException(String.format("No feeding LCMS wells available for plate %s", p.getBarcode()));
    }

    List<FeedingLCMSWell> relevantWells = new ArrayList<>();
    for (FeedingLCMSWell well : allPlateWells) {
      if (!well.getMsid().equals(strainOrConstruct) && !well.getComposition().equals(strainOrConstruct)) {
        // Ignore wells that don't have the right strain/construct (though we assume the whole plate shares one).
        continue;
      }

      if (!well.getExtract().equals(extract)) {
        // Filter by extract.
        continue;
      }

      if (!well.getChemical().equals(feedingCondition)) {
        // Filter by fed chemical.
        continue;
      }

      relevantWells.add(well);
    }

    Collections.sort(relevantWells, new Comparator<FeedingLCMSWell>() {
      @Override
      public int compare(FeedingLCMSWell o1, FeedingLCMSWell o2) {
        // Assume concentration is never null.
        return o1.getConcentration().compareTo(o2.getConcentration());
      }
    });

    Map<FeedingLCMSWell, ScanFile> wellsToScanFiles = new HashMap<>();
    Set<String> constructs = new HashSet<>(1);
    for (FeedingLCMSWell well : relevantWells) {
      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      if (scanFiles == null || scanFiles.size() == 0) {
        System.err.format("WARNING: no scan files for well at %s %s\n", p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      if (scanFiles.size() > 1) {
        System.err.format("WARNING: found multiple scan files for %s %s, using first\n",
            p.getBarcode(), well.getCoordinatesString());
      }
      while (scanFiles.size() > 0 && scanFiles.get(0).getFileType() != ScanFile.SCAN_FILE_TYPE.NC) {
        scanFiles.remove(0);
      }
      if (scanFiles.size() == 0) {
        System.err.format("WARNING: no scan files with valid format for %s %s\n",
            p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      // All of the extracted wells should be unique, so there should be no collisions here.
      wellsToScanFiles.put(well, scanFiles.get(0));
      constructs.add(well.getComposition());
    }

    Pair<String, Double> searchMass = null;
    if (searchMassStr != null) {
      searchMass = Utils.extractMassFromString(db, searchMassStr);
    }
    if (searchMass == null) {
      if (constructs.size() != 1) {
        throw new RuntimeException(String.format(
            "Found multiple growth targets for feeding analysis when no mass specified: %s",
            StringUtils.join(constructs, ", ")));
      }
      String constructName = constructs.iterator().next();
      CuratedChemical cc = Utils.extractTargetForConstruct(db, constructName);
      if (cc == null) {
        throw new RuntimeException(String.format("Unable to find curated chemical for construct %s", constructName));
      }
      System.out.format("Using target %s of construct %s as search mass (%f)\n",
          cc.getName(), constructName, cc.getMass());
      searchMass = Pair.of(cc.getName(), cc.getMass());
    }

    MS1 c = new MS1();
    // TODO: use configurable or scan-file derived ion mode.
    Map<String, Double> metlinMasses = c.getIonMasses(searchMass.getValue(), "pos");

    if (searchIons == null || searchIons.size() == 0) {
      System.err.format("WARNING: no search ion defined, defaulting to M+H\n");
      searchIons = Collections.singleton("M+H");
    } else if (searchIons.size() > 1) {
      throw new RuntimeException("Multiple ions specified for feeding experiment, only one is allowed");
    }
    String searchIon = searchIons.iterator().next();

    List<Pair<Double, MS1.MS1ScanResults>> rampUp = new ArrayList<>();
    for (FeedingLCMSWell well : relevantWells) {
      ScanFile scanFile = wellsToScanFiles.get(well);
      if (scanFile == null) {
        System.err.format("WARNING: no scan file available for %s %s", p.getBarcode(), well.getCoordinatesString());
        continue;
      }
      File localScanFile = new File(lcmsDir, scanFile.getFilename());
      if (!localScanFile.exists() && localScanFile.isFile()) {
        System.err.format("WARNING: could not find regular file at expected path: %s\n",
            localScanFile.getAbsolutePath());
        continue;
      }
      System.out.format("Processing scan data at %s\n", localScanFile.getAbsolutePath());
      MS1.MS1ScanResults ms1ScanResults = c.getMS1(metlinMasses, localScanFile.getAbsolutePath());
      Double concentration = well.getConcentration();
      rampUp.add(Pair.of(concentration, ms1ScanResults));
    }

    c.plotFeedings(rampUp, searchIon, outPrefix, fmt);
  }

}
