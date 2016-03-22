package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.MS1;
import com.act.lcms.db.io.DB;
import com.act.lcms.db.model.ChemicalOfInterest;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.act.lcms.XZ;
import org.joda.time.LocalDateTime;

public class AnalysisHelper {

  // This constant is the best score when a metlin ion is provided manually
  private static final Integer MANUAL_OVERRIDE_BEST_SCORE = 0;
  private static final Set<String> EMPTY_SET = Collections.unmodifiableSet(new HashSet<>(0));

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

          MS1ScanForWellAndMassCharge ms1ScanResults;

          List<ChemicalOfInterest> chemicalsOfInterest =
              ChemicalOfInterest.getInstance().getChemicalOfInterestByName(db, searchMZ.getLeft());

          // Check if in the input chemical is valid
          if (chemicalsOfInterest == null || chemicalsOfInterest.size() == 0) {
            MS1 ms1 = new MS1();
            ms1ScanResults = ms1.getMS1(metlinMasses, localScanFile.getAbsolutePath());
          } else {
            MS1ScanForWellAndMassCharge ms1ScanResultsCache = new MS1ScanForWellAndMassCharge();
            ms1ScanResults = ms1ScanResultsCache.getByPlateIdPlateRowPlateColUseSnrScanFileChemical(
                db, plate, well, true, sf, searchMZ.getLeft(), metlinMasses, localScanFile);
          }

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
   * This function filters out negative scan data, then categorizes the remaining based on dates, followed by finding
   * a set of scan data with the lowest noise. Based on this filtered set of data, it constructs a
   * ChemicalToMapOfMetlinIonsToIntensityTimeValues object that is a mapping of chemical to metlin ion to intensity/time
   * values for each ion.
   * @param db
   * @param lcmsDir - The directory where the LCMS scan data can be found.
   * @param searchMZs - A list of target M/Zs to search for in the scans (see API for {@link MS1}.
   * @param kind - The role of this well in this analysis (standard, positive sample, negative control)
   * @param plateCache - A hash of Plates already accessed from the DB.
   * @param samples - A list of wells to process.
   * @param useSNRForPeakIdentification - If true, signal-to-noise ratio will be used for peak identification.  If not, 
   *                                    peaks will be identified by intensity. 
   * @param targetChemical - A string associated with the chemical name.
   * @return - A mapping of chemical to metlin ion to intensity/time values.
   * @throws Exception
   */
  public static ChemicalToMapOfMetlinIonsToIntensityTimeValues readStandardWellScanData(
      DB db, File lcmsDir, List<Pair<String, Double>> searchMZs, ScanData.KIND kind, HashMap<Integer,
      Plate> plateCache, List<StandardWell> samples, boolean useFineGrainedMZTolerance, Set<String> includeIons,
      Set<String> excludeIons, boolean useSNRForPeakIdentification, String targetChemical) throws Exception {

    List<ScanData<StandardWell>> allScans = processScans(db, lcmsDir, searchMZs, kind, plateCache, samples,
        useFineGrainedMZTolerance, includeIons, excludeIons, useSNRForPeakIdentification).getLeft();

    // TODO: We only analyze positive scan files for now since we are not confident with the negative scan file results.
    // Since we can perform multiple scans on the same well, we need to categorize the data based on date.
    Map<LocalDateTime, List<ScanData<StandardWell>>> filteredScansCategorizedByDate = new HashMap<>();
    Map<LocalDateTime, List<ScanData<StandardWell>>> postFilteredScansCategorizedByDate = new HashMap<>();

    for (ScanData<StandardWell> scan : allScans) {
      if (!scan.scanFile.isNegativeScanFile()) {
        LocalDateTime scanDate = scan.scanFile.getDateFromScanFileTitle();
        List<ScanData<StandardWell>> scanDataForDate = filteredScansCategorizedByDate.get(scanDate);
        if (scanDataForDate == null) {
          scanDataForDate = new ArrayList<>();
        }
        scanDataForDate.add(scan);
        filteredScansCategorizedByDate.put(scanDate, scanDataForDate);
      }
    }

    // Filter out date categories that do not contain the target chemical
    for (Map.Entry<LocalDateTime, List<ScanData<StandardWell>>> entry : filteredScansCategorizedByDate.entrySet()) {
      Boolean containsTargetChemical = false;
      for (ScanData<StandardWell> scanData : entry.getValue()) {
        if (scanData.getWell().getChemical().equals(targetChemical)) {
          containsTargetChemical = true;
        }
      }

      if (containsTargetChemical) {
        postFilteredScansCategorizedByDate.put(entry.getKey(), entry.getValue());
      }
    }

    // Choose the date where the target chemical's scan file has the lowest noise across all ions.
    // TODO: Is there a better way of choosing between scanfiles categorized between dates?
    LocalDateTime bestDate = null;
    Double lowestNoise = Double.MAX_VALUE;
    for (Map.Entry<LocalDateTime, List<ScanData<StandardWell>>> entry : postFilteredScansCategorizedByDate.entrySet()) {
      for (ScanData<StandardWell> scanData : entry.getValue()) {
        if (scanData.getWell().getChemical().equals(targetChemical)) {
          if (WaveformAnalysis.maxNoiseOfSpectra(scanData.getMs1ScanResults().getIonsToSpectra()) < lowestNoise) {
            lowestNoise = WaveformAnalysis.maxNoiseOfSpectra(scanData.getMs1ScanResults().getIonsToSpectra());
            bestDate = entry.getKey();
          }
        }
      }
    }

    // At this point, we guarantee that each standard well chemical is run only once on a given day.
    List<ScanData<StandardWell>> representativeListOfScanFiles = postFilteredScansCategorizedByDate.get(bestDate);

    Set<String> setOfChemicals = new HashSet<>();
    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakData = new ChemicalToMapOfMetlinIonsToIntensityTimeValues();
    for (ScanData<StandardWell> scan : representativeListOfScanFiles) {

      if (!setOfChemicals.contains(scan.getWell().getChemical())) {
        setOfChemicals.add(scan.getWell().getChemical());
      } else {
        throw new RuntimeException(String.format("Found more than one instance of %s run on the same plate " +
            "on the same day.", scan.getWell().getChemical()));
      }

      // get all the scan results for each metlin mass combination for a given compound.
      MS1ScanForWellAndMassCharge ms1ScanResults = scan.getMs1ScanResults();
      Map<String, List<XZ>> ms1s = ms1ScanResults.getIonsToSpectra();

      // read intensity and time data for each metlin mass
      for (Map.Entry<String, List<XZ>> ms1ForIon : ms1s.entrySet()) {
        String ion = ms1ForIon.getKey();
        List<XZ> ms1 = ms1ForIon.getValue();
        peakData.addIonIntensityTimeValueToChemical(scan.getWell().getChemical(), ion, ms1);
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
   * @param curatedMetlinIons A map from standard ion result to the best curated ion that was manual inputted.
   * @param areOtherPositiveModeScansAvailable This boolean is used to post filter and pick a positive metlin ion if and
   *                                       only if positive ion mode scans are available.
   * @param areOtherNegativeModeScansAvailable This boolean is used to post filter and pick a negative metlin ion if and
   *                                       only if negative ion mode scans are available.
   * @return The best metlin ion or null if none can be found
   */
  public static String scoreAndReturnBestMetlinIonFromStandardIonResults(List<StandardIonResult> standardIonResults,
                                                                         Map<StandardIonResult, String> curatedMetlinIons,
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

    for (Map.Entry<StandardIonResult, String> resultToIon: curatedMetlinIons.entrySet()) {
      // Override all the scores of the manually curated standard ion result and set them to the highest rank.
      // Ideally, the user has been consistent for the best metlin ion across similar standard ion results, so
      // tie breakers will not happen. If a tie happen, it is broken arbitrarily.
      metlinScore.put(resultToIon.getValue(), MANUAL_OVERRIDE_BEST_SCORE);
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

  public static ScanData<StandardWell> getScanDataFromStandardIonResult(DB db, File lcmsDir,
                                                                        StandardWell well,
                                                                        String chemicalForMZValue,
                                                                        String targetChemical) throws Exception {
    Plate plate = Plate.getPlateById(db, well.getPlateId());
    List<ScanFile> positiveScanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
        db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
    ScanFile representativePositiveScanFile = positiveScanFiles.get(0);

    Double mzValue = Utils.extractMassForChemical(db, chemicalForMZValue);

    File localScanFile = new File(lcmsDir, representativePositiveScanFile.getFilename());
    if (!localScanFile.exists() && localScanFile.isFile()) {
      System.err.format("WARNING: could not find regular file at expected path: %s\n", localScanFile.getAbsolutePath());
      return null;
    }

    MS1 mm = new MS1();
    MS1.IonMode mode = MS1.IonMode.valueOf(representativePositiveScanFile.getMode().toString().toUpperCase());
    Map<String, Double> allMasses = mm.getIonMasses(mzValue, mode);
    Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, EMPTY_SET, EMPTY_SET);

    MS1ScanForWellAndMassCharge ms1ScanResultsCache = new MS1ScanForWellAndMassCharge();
    MS1ScanForWellAndMassCharge ms1ScanResultsForPositiveControl =
        ms1ScanResultsCache.getByPlateIdPlateRowPlateColUseSnrScanFileChemical(
            db, plate, well, true, representativePositiveScanFile, targetChemical,
            metlinMasses, localScanFile);

    ScanData<StandardWell> encapsulatedDataForPositiveControl =
        new ScanData<StandardWell>(ScanData.KIND.STANDARD, plate, well, representativePositiveScanFile,
            targetChemical, metlinMasses, ms1ScanResultsForPositiveControl);

    return encapsulatedDataForPositiveControl;
  }
}
