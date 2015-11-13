package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.db.analysis.ScanData;
import com.act.lcms.db.analysis.Utils;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.ScanFile;
import com.act.lcms.db.model.StandardWell;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisHelper {
  /**
   * Process a list of wells (LCMS or Standard), producing a list of scan objects that encapsulate the plate,
   * scan file, and masses for that well.
   * @param db The DB from which to extract plate data.
   * @param lcmsDir The directory where the LCMS scans live.
   * @param searchMZs A list of target M/Zs to search for in the scans (see API for {@link MS1}.
   * @param kind The role of this well in this analysis (standard, positive sample, negative control).
   * @param plateCache A hash of Plates already accessed from the DB.
   * @param samples A list of wells to process.
   * @param useSNRForPeakIdentification If true, signal-to-noise ratio will be used for peak identification.  If not,
   *                                    peaks will be identified by intensity.
   * @param <T> The PlateWell type whose scans to process.
   * @return A list of ScanData objects that wraps the objects required to produce a graph for each specified well.
   * @throws Exception
   */
  public static <T extends PlateWell<T>> Pair<List<ScanData<T>>, Double> processScans(
      DB db, File lcmsDir, List<Pair<String, Double>> searchMZs, ScanData.KIND kind, HashMap<Integer, Plate> plateCache,
      List<T> samples, boolean useFineGrainedMZTolerance, Set<String> includeIons, Set<String> excludeIons,
      boolean useSNRForPeakIdentification)
      throws Exception {
    Double maxIntensity = 0.0d;
    List<ScanData<T>> allScans = new ArrayList<>(samples.size());
    for (T well : samples) {
      // The foreign key constraint on wells ensure that plate will be non-null.
      Plate plate = plateCache.get(well.getPlateId());
      if (plate == null) {
        plate = Plate.getPlateById(db, well.getPlateId());
        plateCache.put(plate.getId(), plate);
      }
      System.out.format("Processing LCMS well %s %s\n", plate.getBarcode(), well.getCoordinatesString());

      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      if (scanFiles == null || scanFiles.size() == 0) {
        System.err.format("WARNING: No scan files available for %s %s\n",
            plate.getBarcode(), well.getCoordinatesString());
        continue;
      }

      for (ScanFile sf : scanFiles) {
        if (sf.getFileType() != ScanFile.SCAN_FILE_TYPE.NC) {
          System.err.format("Skipping scan file with non-NetCDF format: %s\n", sf.getFilename());
          continue;
        }
        File localScanFile = new File(lcmsDir, sf.getFilename());
        if (!localScanFile.exists() && localScanFile.isFile()) {
          System.err.format("WARNING: could not find regular file at expected path: %s\n",
              localScanFile.getAbsolutePath());
          continue;
        }

        MS1 mm = new MS1(useFineGrainedMZTolerance, useSNRForPeakIdentification);
        for (Pair<String, Double> searchMZ : searchMZs) {
          Map<String, Double> metlinMasses =
              Utils.filterMasses(mm.getIonMasses(searchMZ.getRight(), sf.getMode().toString().toLowerCase()),
                  includeIons, excludeIons);
          MS1.MS1ScanResults ms1ScanResults = mm.getMS1(metlinMasses, localScanFile.getAbsolutePath());
          maxIntensity = Math.max(ms1ScanResults.getMaxYAxis(), maxIntensity);
          System.out.format("Max intensity for target %s (%f) in %s is %f\n",
              searchMZ.getLeft(), searchMZ.getRight(), sf.getFilename(), ms1ScanResults.getMaxYAxis());
          // TODO: purge the MS1 spectra from ms1ScanResults if this ends up hogging too much memory.
          allScans.add(new ScanData<T>(kind, plate, well, sf, searchMZ.getLeft(), metlinMasses, ms1ScanResults));
        }
      }
    }
    return Pair.of(allScans, maxIntensity);
  }


  /**
   * Write the time/intensity data for a given scan to an output stream.
   *
   * Note that the signature of ScanData is intentionally weakened to allow us to conditionally handle LCMSWell or
   * StandardWell objects contained in scanData.
   *
   * @param fos The output stream to which to write the time/intensity data.
   * @param lcmsDir The directory where the LCMS scan data can be found.
   * @param maxIntensity The maximum intensity for all scans in the ultimate graph to be produced.
   * @param scanData The scan data whose values will be written.
   * @param ionsToWrite A set of ions to write; all available ions are written if this is null.
   * @param useSNRForPeakIdentification If true, signal-to-noise ratio will be used for peak filtering.  If not,
   *                                    peaks will be filtered by intensity.
   * @return A list of graph labels for each LCMS file in the scan.
   * @throws Exception
   */
  public static List<String> writeScanData(FileOutputStream fos, File lcmsDir, Double maxIntensity,
                                           ScanData scanData, boolean useFineGrainedMZTolerance,
                                           boolean makeHeatmaps, boolean applyThreshold,
                                           boolean useSNRForPeakIdentification, Set<String> ionsToWrite)
      throws Exception {
    if (ScanData.KIND.BLANK == scanData.getKind()) {
      return Collections.singletonList(Gnuplotter.DRAW_SEPARATOR);
    }

    Plate plate = scanData.getPlate();
    ScanFile sf = scanData.getScanFile();
    Map<String, Double> metlinMasses = scanData.getMetlinMasses();

    MS1 mm = new MS1(useFineGrainedMZTolerance, useSNRForPeakIdentification);
    File localScanFile = new File(lcmsDir, sf.getFilename());

    MS1.MS1ScanResults ms1ScanResults = mm.getMS1(metlinMasses, localScanFile.getAbsolutePath());
    List<String> ionLabels = mm.writeMS1Values(ms1ScanResults, maxIntensity, metlinMasses, fos,
        makeHeatmaps, applyThreshold, ionsToWrite);
    System.out.format("Scan for target %s has ion labels: %s\n", scanData.getTargetChemicalName(),
        StringUtils.join(ionLabels, ", "));

    List<String> graphLabels = new ArrayList<>(ionLabels.size());
    if (scanData.getWell() instanceof LCMSWell) {
      for (String label : ionLabels) {
        LCMSWell well = (LCMSWell)scanData.getWell();
        String l = String.format("%s (%s fed %s) @ %s %s %s, %s %s",
            well.getComposition(), well.getMsid(),
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            plate.getBarcode(),
            well.getCoordinatesString(),
            sf.getMode().toString().toLowerCase(),
            scanData.getTargetChemicalName(),
            label
        );
        System.out.format("Adding graph w/ label %s\n", l);
        graphLabels.add(l);
      }
    } else if (scanData.getWell() instanceof StandardWell) {
      for (String label : ionLabels) {
        StandardWell well = (StandardWell)scanData.getWell();
        String l = String.format("Standard %s @ %s %s %s, %s %s",
            well.getChemical() == null || well.getChemical().isEmpty() ? "nothing" : well.getChemical(),
            plate.getBarcode(),
            well.getCoordinatesString(),
            sf.getMode().toString().toLowerCase(),
            scanData.getTargetChemicalName(),
            label
        );
        System.out.format("Adding graph w/ label %s\n", l);
        graphLabels.add(l);
      }
    } else {
      throw new RuntimeException(
          String.format("Graph request for well type %s", scanData.getWell().getClass().getCanonicalName()));
    }

    System.out.format("Done processing file at %s\n", localScanFile.getAbsolutePath());
    return graphLabels;
  }

  public static List<String> writeScanData(FileOutputStream fos, File lcmsDir, Double maxIntensity,
                                           ScanData scanData, boolean useFineGrainedMZTolerance,
                                           boolean makeHeatmaps, boolean applyThreshold, boolean useSNR)
      throws Exception {
    return writeScanData(
        fos, lcmsDir, maxIntensity, scanData, useFineGrainedMZTolerance, makeHeatmaps, applyThreshold, useSNR, null);
  }

}
