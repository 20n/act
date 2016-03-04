package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.MS1ScanForWellAndMassCharge;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PlateWell;
import com.act.lcms.db.model.ScanFile;
import com.act.lcms.db.model.StandardIonResult;
import com.act.lcms.db.model.StandardWell;
import com.act.lcms.plotter.WriteAndPlotMS1Results;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.act.lcms.XZ;

public class AnalysisHelper {

  private static <A,B> Pair<List<A>, List<B>> split(List<Pair<A, B>> lpairs) {
    List<A> a = new ArrayList<>();
    List<B> b = new ArrayList<>();
    for (Pair<A, B> p : lpairs) {
      a.add(p.getLeft());
      b.add(p.getRight());
    }
    return Pair.of(a, b);
  }

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
          MS1.IonMode mode = MS1.IonMode.valueOf(sf.getMode().toString().toUpperCase());
          Map<String, Double> allMasses = mm.getIonMasses(searchMZ.getRight(), mode);
          Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, includeIons, excludeIons);

          MS1ScanForWellAndMassCharge ms1ScanResultsCache = new MS1ScanForWellAndMassCharge();
          MS1ScanForWellAndMassCharge ms1ScanResults = ms1ScanResultsCache.getByPlateIdPlateRowPlateColIonMzUseSnrScanFile(
              db, plate, well, searchMZ.getRight(), true, localScanFile.getAbsolutePath(), metlinMasses);

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
   * @return A list of graph labels for each LCMS file in the scan.
   * @throws Exception
   */
  public static List<String> writeScanData(FileOutputStream fos, File lcmsDir, Double maxIntensity, ScanData scanData,
                                           boolean makeHeatmaps, boolean applyThreshold, Set<String> ionsToWrite)
      throws Exception {
    if (ScanData.KIND.BLANK == scanData.getKind()) {
      return Collections.singletonList(Gnuplotter.DRAW_SEPARATOR);
    }

    Plate plate = scanData.getPlate();
    ScanFile sf = scanData.getScanFile();
    Map<String, Double> metlinMasses = scanData.getMetlinMasses();
    File localScanFile = new File(lcmsDir, sf.getFilename());

    MS1ScanForWellAndMassCharge ms1ScanResults = scanData.getMs1ScanResults();

    WriteAndPlotMS1Results plottingUtil = new WriteAndPlotMS1Results();
    List<Pair<String, String>> ionsAndLabels = plottingUtil.writeMS1Values(
        ms1ScanResults.getIonsToSpectra(), maxIntensity, metlinMasses, fos, makeHeatmaps, applyThreshold, ionsToWrite);
    List<String> ionLabels = split(ionsAndLabels).getRight();

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

  /**
   * This function reads scan data based on sample information and constructs a mapping of chemical to metlin ion to
   * intensity/time values for each ion.
   * @param db
   * @param lcmsDir - The directory where the LCMS scan data can be found.
   * @param searchMZs - A list of target M/Zs to search for in the scans (see API for {@link MS1}.
   * @param kind - The role of this well in this analysis (standard, positive sample, negative control)
   * @param plateCache - A hash of Plates already accessed from the DB.
   * @param samples - A list of wells to process.
   * @param useSNRForPeakIdentification - If true, signal-to-noise ratio will be used for peak identification.  If not, 
   *                                    peaks will be identified by intensity. 
   * @param <T> - The PlateWell type whose scans to process.
   * @return - A mapping of chemical to metlin ion to intensity/time values.
   * @throws Exception
   */
  public static <T extends PlateWell<T>> ChemicalToMapOfMetlinIonsToIntensityTimeValues readScanData(
      DB db, File lcmsDir, List<Pair<String, Double>> searchMZs, ScanData.KIND kind, HashMap<Integer,
      Plate> plateCache, List<T> samples, boolean useFineGrainedMZTolerance, Set<String> includeIons, Set<String> excludeIons,
      boolean useSNRForPeakIdentification) throws Exception {

    List<ScanData<T>> allScans = processScans(db, lcmsDir, searchMZs, kind, plateCache, samples,
        useFineGrainedMZTolerance, includeIons, excludeIons, useSNRForPeakIdentification).getLeft();

    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakData = new ChemicalToMapOfMetlinIonsToIntensityTimeValues();
    for (ScanData scan : allScans) {
      // get all the scan results for each metlin mass combination for a given compound.
      MS1ScanForWellAndMassCharge ms1ScanResults = scan.getMs1ScanResults();
      Map<String, List<XZ>> ms1s = ms1ScanResults.getIonsToSpectra();

      // read intensity and time data for each metlin mass
      for (Map.Entry<String, List<XZ>> ms1ForIon : ms1s.entrySet()) {
        String ion = ms1ForIon.getKey();
        List<XZ> ms1 = ms1ForIon.getValue();
        if (scan.getWell() instanceof StandardWell) {
          // peakData is organized as follows: STANDARD -> Metlin Ion #1 -> (A bunch of intensity/time graphs)
          //                                   NEG_CONTROL1 -> Metlin Ion #1 -> (A bunch of intensity/time graphs) etc.
          StandardWell well = (StandardWell) scan.getWell();
          peakData.addIonIntensityTimeValueToChemical(well.getChemical(), ion, ms1);
        }
      }
    }

    return peakData;
  }

  /**
   * This function does a naive scoring algorithm where it just picks the first element of the sorted hashed map as
   * the best metlin ion.
   * @param sortedIonList - This is sorted map of ion to best intensity,time values.
   * @return The lowest score ion, which is the best prediction.
   */
  public static String getBestMetlinIonFromPossibleMappings(LinkedHashMap<String, XZ> sortedIonList) {
    String result = "";
    for (Map.Entry<String, XZ> metlinIonToData : sortedIonList.entrySet()) {
      // Get the first value from the input since it is already sorted.
      result = metlinIonToData.getKey();
      break;
    }
    return result;
  }

  public static List<String> writeScanData(FileOutputStream fos, File lcmsDir, Double maxIntensity,
                                           ScanData scanData, boolean makeHeatmaps, boolean applyThreshold)
      throws Exception {
    return writeScanData(
        fos, lcmsDir, maxIntensity, scanData, makeHeatmaps, applyThreshold, null);
  }

  /**
   * This function scores the various metlin ions from different standard ion results, sorts them and picks the
   * best ion. This is done by adding up the indexed positions of the ion in each sorted entry of the list of
   * standard ion results. Since the entries in the standard ion results are sorted, the lower magnitude summation ions
   * are better than the larger magnitude summations. We do a post filtering on these scores based on if we have only
   * positive/negative scans from the scan files which exist in the context of the caller.
   * @param standardIonResults The list of standard ion results
   * @param areOtherPositiveModeScansAvailable This boolean is used to post filter and pick a positive metlin ion if and
   *                                       only if positive ion mode scans are available.
   * @param areOtherNegativeModeScansAvailable This boolean is used to post filter and pick a negative metlin ion if and
   *                                       only if negative ion mode scans are available.
   * @return The best metlin ion or null if none can be found
   */
  public static String scoreAndReturnBestMetlinIonFromStandardIonResults(List<StandardIonResult> standardIonResults,
                                                                         boolean areOtherPositiveModeScansAvailable,
                                                                         boolean areOtherNegativeModeScansAvailable) {
    Map<String, Integer> metlinScore = new HashMap<>();
    Set<String> ions = standardIonResults.get(0).getAnalysisResults().keySet();
    for (String ion : ions) {
      for (StandardIonResult result : standardIonResults) {
        Integer counter = 0;
        for (String localIon : result.getAnalysisResults().keySet()) {
          counter++;
          if (localIon.equals(ion)) {
            Integer ionScore = metlinScore.get(ion);
            if (ionScore == null) {
              ionScore = counter;
            } else {
              ionScore += counter;
            }
            metlinScore.put(ion, ionScore);
            break;
          }
        }
      }
    }

    TreeMap<Integer, List<String>> sortedScores = new TreeMap<>();
    for (String ion : metlinScore.keySet()) {
      if (MS1.getIonModeOfIon(ion) != null) {
        if ((MS1.getIonModeOfIon(ion).equals(MS1.IonMode.POS) && areOtherPositiveModeScansAvailable) ||
            (MS1.getIonModeOfIon(ion).equals(MS1.IonMode.NEG) && areOtherNegativeModeScansAvailable)) {
          List<String> ionBucket = sortedScores.get(metlinScore.get(ion));
          if (ionBucket == null) {
            ionBucket = new ArrayList<>();
          }
          ionBucket.add(ion);
          sortedScores.put(metlinScore.get(ion), ionBucket);
        }
      }
    }

    if (sortedScores.size() == 0) {
      System.err.format("Could not find any ions corresponding to the positive and negative scan mode conditionals");
      return null;
    } else {
      List<String> topMetlinIons = sortedScores.get(sortedScores.keySet().iterator().next());
      // In cases of a tie breaker, simply choose the first ion.
      return topMetlinIons.get(0);
    }
  }
}
