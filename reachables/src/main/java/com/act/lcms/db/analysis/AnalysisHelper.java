package com.act.lcms.db.analysis;

import com.act.lcms.Gnuplotter;
import com.act.lcms.LCMSNetCDFParser;
import com.act.lcms.LCMSSpectrum;
import com.act.lcms.MS1;
import com.act.lcms.XZ;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.LocalDateTime;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class AnalysisHelper {

  // This constant is the best score when a metlin ion is provided manually
  private static final Double MANUAL_OVERRIDE_BEST_SCORE = 0.0d;
  private static final Integer REPRESENTATIVE_INDEX = 0;
  private static final Set<String> EMPTY_SET = Collections.unmodifiableSet(new HashSet<>(0));
  private static final Logger LOGGER = LogManager.getFormatterLogger(AnalysisHelper.class);

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

      LOGGER.info("Processing LCMS well %s %s", plate.getBarcode(), well.getCoordinatesString());

      List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
          db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());
      if (scanFiles == null || scanFiles.size() == 0) {
        LOGGER.error("WARNING: No scan lcms available for %s %s",
            plate.getBarcode(), well.getCoordinatesString());
        continue;
      }

      for (ScanFile sf : scanFiles) {
        if (sf.getFileType() != ScanFile.SCAN_FILE_TYPE.NC) {
          // TODO: Migrate sysem.err to LOGGER framework
          LOGGER.error("Skipping scan file with non-NetCDF format: %s", sf.getFilename());
          continue;
        }
        File localScanFile = new File(lcmsDir, sf.getFilename());
        if (!localScanFile.exists() && localScanFile.isFile()) {
          LOGGER.error("WARNING: could not find regular file at expected path: %s",
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

          LOGGER.info("Max intensity for target %s (%f) in %s is %f",
              searchMZ.getLeft(), searchMZ.getRight(), sf.getFilename(), ms1ScanResults.getMaxYAxis());

          // TODO: purge the MS1 spectra from ms1ScanResults if this ends up hogging too much memory.
          allScans.add(new ScanData<T>(kind, plate, well, sf, searchMZ.getLeft(), metlinMasses, ms1ScanResults));
        }
      }
    }
    return Pair.of(allScans, maxIntensity);
  }

  /**
   * This function gets the intensity-time values for each mass charge in a scan file and packages that up into a mapping
   * * between the mass charge pair and ScanData.
   * @param db The db to query scan lcms from
   * @param lcmsDir The lcms dir where the lcms lcms are found
   * @param searchMZs The pair of chemical and mass charge pairs
   * @param kind The kind of plate the lcms was run over
   * @param plateCache The plate cache
   * @param scanFile The scan file being examined
   * @param well The well being analyzed
   * @param useFineGrainedMZTolerance boolean for MZ tolerance
   * @param useSNRForPeakIdentification If true, signal-to-noise ratio will be used for peak identification.  If not, 
   *                                    peaks will be identified by intensity. 
   * @param <T> The platewell abstraction
   * @return A mapping of mass charge to scandata
   * @throws Exception
   */
  public static <T extends PlateWell<T>> Map<Pair<String, Double>, ScanData<T>> getIntensityTimeValuesForEachMassChargeInScanFile(
      DB db, File lcmsDir, Set<Pair<String, Double>> searchMZs, ScanData.KIND kind, HashMap<Integer, Plate> plateCache,
      ScanFile scanFile, T well, boolean useFineGrainedMZTolerance, boolean useSNRForPeakIdentification)
      throws ParserConfigurationException, IOException, XMLStreamException, SQLException {

    // The foreign key constraint on wells ensure that plate will be non-null.
    Plate plate = plateCache.get(well.getPlateId());
    if (plate == null) {
      plate = Plate.getPlateById(db, well.getPlateId());
      plateCache.put(plate.getId(), plate);
    }

    if (scanFile.getFileType() != ScanFile.SCAN_FILE_TYPE.NC) {
      LOGGER.error("Skipping scan file with non-NetCDF format: %s", scanFile.getFilename());
      return null;
    }

    File localScanFile = new File(lcmsDir, scanFile.getFilename());
    if (!localScanFile.exists() && localScanFile.isFile()) {
      LOGGER.error("WARNING: could not find regular file at expected path: %s", localScanFile.getAbsolutePath());
      return null;
    }

    Map<Pair<String, Double>, ScanData<T>> result = new HashMap<>();
    MS1 mm = new MS1(useFineGrainedMZTolerance, useSNRForPeakIdentification);

    Map<Pair<String, Double>, MS1ScanForWellAndMassCharge> massChargeToMS1Results =
        getMultipleMS1s(mm, searchMZs, localScanFile.getAbsolutePath());

    for (Map.Entry<Pair<String, Double>, MS1ScanForWellAndMassCharge> entry : massChargeToMS1Results.entrySet()) {
      String chemicalName = entry.getKey().getLeft();
      Double massCharge = entry.getKey().getRight();
      MS1ScanForWellAndMassCharge ms1ScanForWellAndMassCharge = entry.getValue();

      Map<String, Double> singletonMass = Collections.singletonMap(chemicalName, massCharge);
      result.put(entry.getKey(), new ScanData<T>(kind, plate, well, scanFile, chemicalName, singletonMass, ms1ScanForWellAndMassCharge));
    }

    return result;
  }

  private static Map<Pair<String, Double>, MS1ScanForWellAndMassCharge> getMultipleMS1s(
      MS1 ms1, Set<Pair<String, Double>> metlinMasses, String ms1File)
      throws ParserConfigurationException, IOException, XMLStreamException {

    // In order for this to sit well with the data model we'll need to ensure the keys are all unique.
    Set<String> uniqueKeys = new HashSet<>();
    metlinMasses.stream().map(Pair::getLeft).forEach(x -> {
      if (uniqueKeys.contains(x)) {
        throw new RuntimeException(String.format("Assumption violation: found duplicate metlin mass keys: %s", x));
      }
      uniqueKeys.add(x);
    });

    Iterator<LCMSSpectrum> ms1Iterator = new LCMSNetCDFParser().getIterator(ms1File);

    Map<Double, List<XZ>> scanLists = new HashMap<>(metlinMasses.size());
    // Initialize reading buffers for all of the target masses.
    metlinMasses.forEach(x -> {
      if (!scanLists.containsKey(x.getRight())) {
        scanLists.put(x.getRight(), new ArrayList<>());
      }
    });
    // De-dupe by mass in case we have exact duplicates, sort for well-ordered extractions.
    List<Double> sortedMasses = new ArrayList<>(scanLists.keySet());

    /* Note: this operation is O(n * m) where n is the number of (mass, intensity) readings from the scan
     * and m is the number of mass targets specified.  We might be able to get this down to O(m log n), but
     * we'll save that for once we get this working at all. */

    while (ms1Iterator.hasNext()) {
      LCMSSpectrum timepoint = ms1Iterator.next();

      // get all (mz, intensity) at this timepoint
      List<Pair<Double, Double>> intensities = timepoint.getIntensities();

      // for this timepoint, extract each of the ion masses from the METLIN set
      for (Double ionMz : sortedMasses) {
        // this time point is valid to look at if its max intensity is around
        // the mass we care about. So lets first get the max peak location
        double intensityForMz = ms1.extractMZ(ionMz, intensities);

        // the above is Pair(mz_extracted, intensity), where mz_extracted = mz
        // we now add the timepoint val and the intensity to the output
        XZ intensityAtThisTime = new XZ(timepoint.getTimeVal(), intensityForMz);
        scanLists.get(ionMz).add(intensityAtThisTime);
      }
    }

    Map<Pair<String, Double>, MS1ScanForWellAndMassCharge> finalResults =
        new HashMap<>(metlinMasses.size());

    /* Note: we might be able to squeeze more performance out of this by computing the
     * stats once per trace and then storing them.  But the time to compute will probably
     * be dwarfed by the time to extract the data (assuming deduplication was done ahead
     * of time), so we'll leave it as is for now. */
    for (Pair<String, Double> pair : metlinMasses) {
      String label = pair.getLeft();
      Double mz = pair.getRight();
      MS1ScanForWellAndMassCharge result = new MS1ScanForWellAndMassCharge();

      result.setMetlinIons(Collections.singletonList(label));
      result.getIonsToSpectra().put(label, scanLists.get(mz));
      ms1.computeAndStorePeakProfile(result, label);

      // DO NOT use isGoodPeak here.  We want positive and negative results alike.

      // There's only one ion in this scan, so just use its max.
      Double maxIntensity = result.getMaxIntensityForIon(label);
      result.setMaxYAxis(maxIntensity);
      // How/why is this not IonsToMax?  Just set it as such for this.
      result.setIndividualMaxIntensities(Collections.singletonMap(label, maxIntensity));

      finalResults.put(pair, result);
    }

    return finalResults;
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

    LOGGER.info("Scan for target %s has ion labels: %s", scanData.getTargetChemicalName(),
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

        LOGGER.info("Adding graph w/ label %s", l);
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
        LOGGER.info("Adding graph w/ label %s", l);
        graphLabels.add(l);
      }
    } else {
      throw new RuntimeException(
          String.format("Graph request for well type %s", scanData.getWell().getClass().getCanonicalName()));
    }

    LOGGER.info("Done processing file at %s", localScanFile.getAbsolutePath());
    return graphLabels;
  }

  /**
   * This function picks the best scan file based on two critereon: a) The scan file has to be a positive scan file
   * b) The scan file has to be of the latest lcms run for the well.
   * @param db The db to query scan lcms from
   * @param well The well being used for the analysis
   * @param <T> The platewell type abstraction
   * @return The best ScanFile
   * @throws Exception
   */
  public static <T extends PlateWell<T>> ScanFile pickBestScanFileForWell(DB db, T well) throws Exception {
    List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
        db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());

    // TODO: We only analyze positive scan lcms for now since we are not confident with the negative scan file results.
    // Since we perform multiple scans on the same well, we need to categorize the data based on date.
    ScanFile latestScanFiles = null;
    LocalDateTime latestDateTime = null;

    for (ScanFile scanFile : scanFiles) {
      if (!scanFile.isNegativeScanFile()) {
        LocalDateTime scanDate = scanFile.getDateFromScanFileTitle();

        // Pick the newest scan lcms
        if (latestDateTime == null || scanDate.isAfter(latestDateTime)) {
          latestScanFiles = scanFile;
          latestDateTime = scanDate;
        }
      }
    }

    return latestScanFiles;
  }

  public static String constructChemicalAndScanTypeName(String name, ScanData.KIND kind) {
    return kind.equals(ScanData.KIND.POS_SAMPLE) ? name + "_Positive" : name + "_Negative";
  }

  /**
   * This function constructs a ChemicalToMapOfMetlinIonsToIntensityTimeValues object from the scan data per mass charge
   * name and value.
   * @param massChargePairToScanDataResult A mapping for mass charge to scan data
   * @param kind The kind of well
   * @param <T>
   * @return A ChemicalToMapOfMetlinIonsToIntensityTimeValues object.
   */
  public static <T extends PlateWell<T>> ChemicalToMapOfMetlinIonsToIntensityTimeValues
      constructChemicalToMapOfMetlinIonsToIntensityTimeValuesFromMassChargeData(
      Map<Pair<String, Double>, ScanData<T>> massChargePairToScanDataResult, ScanData.KIND kind) {

    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakData = new ChemicalToMapOfMetlinIonsToIntensityTimeValues();

    for (Map.Entry<Pair<String, Double>, ScanData<T>> entry : massChargePairToScanDataResult.entrySet()) {
      String chemicalName = entry.getKey().getLeft();
      ScanData<T> scan = entry.getValue();

      // get all the scan results for each metlin mass combination for a given compound.
      Map<String, List<XZ>> ms1s = scan.getMs1ScanResults().getIonsToSpectra();

      String plotName = constructChemicalAndScanTypeName(chemicalName, kind);

      // Read intensity and time data for each metlin mass. We only expect one mass charge pair per ms1ScanResults
      // since we are extracting traces from the scan lcms via getMultipleMS1s.
      for (Map.Entry<String, List<XZ>> ms1ForIon : ms1s.entrySet()) {
        String ion = ms1ForIon.getKey();
        List<XZ> ms1 = ms1ForIon.getValue();
        peakData.addIonIntensityTimeValueToChemical(plotName, ion, ms1);
      }
    }

    return peakData;
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

    // If there are no scans found, the client should handle this situation. So we return null.
    if (allScans.size() == 0) {
      LOGGER.error("WARNING: No scans were found.");
      return null;
    }

    // TODO: We only analyze positive scan lcms for now since we are not confident with the negative scan file results.
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

    // We use this below container to hold the scandata of a particular chemical with the highest hash code among
    // all scandata of the given chemical. We do so that if we find two standard wells of the same chemical run on the
    // same day, we consistently pick the same well over multiple runs.
    Map<String, ScanData<StandardWell>> chemicalToHighestScanDataHashCode = new HashMap<>();

    for (ScanData<StandardWell> scan : representativeListOfScanFiles) {
      ScanData<StandardWell> result = chemicalToHighestScanDataHashCode.get(scan.getWell().getChemical());
      if (result == null) {
        result = scan;
      } else {
        if (scan.hashCode() > result.hashCode()) {
          result = scan;
        }
      }
      chemicalToHighestScanDataHashCode.put(scan.getWell().getChemical(), result);
    }

    ChemicalToMapOfMetlinIonsToIntensityTimeValues peakData = new ChemicalToMapOfMetlinIonsToIntensityTimeValues();

    for (Map.Entry<String, ScanData<StandardWell>> chemicalToScanDataWithHighestHashCode :
        chemicalToHighestScanDataHashCode.entrySet()) {

      ScanData<StandardWell> scan = chemicalToScanDataWithHighestHashCode.getValue();

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
   * are better than the larger magnitude summations. Then, we add another feature, in this case, the normalized SNR/maxSNR
   * but multiplying the positional score with the normalized SNR. The exact calculation is as follows:
   * score = positional_score * (1 - SNR(i)/maxSNR). We have to do the (1 - rel_snr) since we choose the lowest score,
   * so if the rel_snr is huge (ie a good signal), the overall magnitude of score will reduce, which makes that a better
   * ranking for the ion. We then do a post filtering on these scores based on if we have only positive/negative scans
   * from the scan lcms which exist in the context of the caller.
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
    if (standardIonResults == null) {
      return null;
    }

    // We find the maximum SNR values for each standard ion result so that we can normalize individual SNR scores
    // during scoring.
    HashMap<StandardIonResult, Double> resultToMaxSNR = new HashMap<>();
    for (StandardIonResult result : standardIonResults) {
      Double maxSNR = 0.0d;
      for (Map.Entry<String, XZ> resultoDoublePair : result.getAnalysisResults().entrySet()) {
        if (resultoDoublePair.getValue().getIntensity() > maxSNR) {
          maxSNR = resultoDoublePair.getValue().getIntensity();
        }
      }
      resultToMaxSNR.put(result, maxSNR);
    }

    Map<String, Double> metlinScore = new HashMap<>();
    Set<String> ions = standardIonResults.get(0).getAnalysisResults().keySet();

    // For each ion, iterate through all the ion results to find the position of that ion in each result set (since the
    // ions are sorted) and then multiply that by a normalized value of the SNR.
    for (String ion : ions) {
      for (StandardIonResult result : standardIonResults) {
        Integer counter = 0;
        for (String localIon : result.getAnalysisResults().keySet()) {
          counter++;
          if (localIon.equals(ion)) {
            Double ionScore = metlinScore.get(ion);
            if (ionScore == null) {
              // Normalize the sample's SNR by dividing it by the maxSNR. Then we multiple a variant of it to the counter
              // score so that if the total magnitude of the score is lower, the ion is ranked higher.
              ionScore = (1.0 * counter) * (1 - (result.getAnalysisResults().get(ion).getIntensity() / resultToMaxSNR.get(result)));
            } else {
              ionScore += (1.0 * counter) * (1 - (result.getAnalysisResults().get(ion).getIntensity() / resultToMaxSNR.get(result)));
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

    TreeMap<Double, List<String>> sortedScores = new TreeMap<>();
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
      LOGGER.error("Could not find any ions corresponding to the positive and negative scan mode conditionals");
      return null;
    } else {
      List<String> topMetlinIons = sortedScores.get(sortedScores.keySet().iterator().next());
      // In cases of a tie breaker, simply choose the first ion.
      return topMetlinIons.get(0);
    }
  }

  /**
   * This function takes a well as input, finds all the scan lcms associated with that well, then picks a representative
   * scan file, in this case, the first scan file which has the NC file format. It then extracts the ms1 scan results
   * corresponding to that scan file and packages it up into a ScanData container.
   * @param db - The db from which the data is extracteds
   * @param lcmsDir - The dir were scan lcms are present
   * @param well - The well based on which the scan file is founds
   * @param chemicalForMZValue - This is chemical from which the mz values that are needed from the ms1 analysis is extracted.
   * @param targetChemical - This is the target chemical for the analysis, ie find all chemicalForMZValue's mz variates
   *                       within targetChemical's ion profile.
   * @return ScanData - The resultant scan data.
   * @throws Exception
   */
  public static ScanData<StandardWell> getScanDataForWell(DB db, File lcmsDir,
                                                          StandardWell well,
                                                          String chemicalForMZValue,
                                                          String targetChemical) throws Exception {
    Plate plate = Plate.getPlateById(db, well.getPlateId());
    List<ScanFile> scanFiles = ScanFile.getScanFileByPlateIDRowAndColumn(
        db, well.getPlateId(), well.getPlateRow(), well.getPlateColumn());

    ScanFile representativeScanFile = null;

    for (ScanFile scanFile : scanFiles) {
      if (scanFile.getFileType() == ScanFile.SCAN_FILE_TYPE.NC) {
        representativeScanFile = scanFile;
        break;
      }
    }

    if (representativeScanFile == null) {
      throw new RuntimeException("None of the scan lcms are of the NC format");
    }

    File localScanFile = new File(lcmsDir, representativeScanFile.getFilename());
    if (!localScanFile.exists() && localScanFile.isFile()) {
      LOGGER.warn("Could not find regular file at expected path: %s", localScanFile.getAbsolutePath());
      return null;
    }

    Pair<String, Double> mzValue = Utils.extractMassFromString(db, chemicalForMZValue);
    MS1 mm = new MS1();

    // TODO: Unify these enums.
    MS1.IonMode mode = MS1.IonMode.valueOf(representativeScanFile.getMode().toString().toUpperCase());
    Map<String, Double> allMasses = mm.getIonMasses(mzValue.getRight(), mode);
    Map<String, Double> metlinMasses = Utils.filterMasses(allMasses, EMPTY_SET, EMPTY_SET);

    MS1ScanForWellAndMassCharge ms1ScanResultsCache = new MS1ScanForWellAndMassCharge();
    MS1ScanForWellAndMassCharge ms1ScanResultsForPositiveControl = ms1ScanResultsCache.
        getByPlateIdPlateRowPlateColUseSnrScanFileChemical(db, plate, well, true, representativeScanFile, targetChemical,
            metlinMasses, localScanFile);

    ScanData<StandardWell> encapsulatedDataForPositiveControl =
        new ScanData<StandardWell>(ScanData.KIND.STANDARD, plate, well, representativeScanFile,
            targetChemical, metlinMasses, ms1ScanResultsForPositiveControl);

    return encapsulatedDataForPositiveControl;
  }
}
