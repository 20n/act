package com.act.lcms;

import com.act.lcms.db.model.MS1ScanForWellAndMassCharge;
import com.act.lcms.plotter.WriteAndPlotMS1Results;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MS1 {

  public static class YZ {
    Double mz;
    Double intensity;

    public YZ(Double mz, Double intensity) {
      this.mz = mz;
      this.intensity = intensity;
    }

    public Double getMZ() {
      return mz;
    }

    public Double getIntensity() {
      return intensity;
    }
  }

  // In the MS1 case, we look for a very tight window 
  // because we do not noise to broaden our signal
  public static final Double MS1_MZ_TOLERANCE_FINE = 0.001;
  public static final Double MS1_MZ_TOLERANCE_COARSE = 0.01;
  public static final Double MS1_MZ_TOLERANCE_DEFAULT = MS1_MZ_TOLERANCE_COARSE;
  private static final Logger LOGGER = LogManager.getFormatterLogger(MS1.class);

  // when aggregating the MS1 signal, we do not expect
  // more than these number of measurements within the
  // mz window specified by the tolerance above
  static final Integer MAX_MZ_IN_WINDOW_FINE = 3;
  static final Integer MAX_MZ_IN_WINDOW_COARSE = 4;
  static final Integer MAX_MZ_IN_WINDOW_DEFAULT = MAX_MZ_IN_WINDOW_FINE;

  // when using max intensity, the threshold is 20% of max
  static final Double THRESHOLD_PERCENT = 0.20d;

  // when using SNR, the threshold is 60 for snr x max_intensity/1M;
  static final boolean USE_SNR_FOR_THRESHOLD_DEFAULT = true;
  // we experimented and observed the following:
  // At logSNR > 10.0d: only VERY strong peaks are isolated, e.g., those above 10^6 straight up
  // At logSNR > 5.0d : a lot of junky smears appear
  // At logSNR > 6.0d : Midway point that is lenient in allowing for peaks; while eliminating full smears
  // At logSNR > 3.5d : Since we are using the more thorough standard ion analysis to compare peaks, this,
  // threshold was good to compare positive vs negative control peaks.
  static final Double LOGSNR_THRESHOLD = 3.5d;
  // because SNR is sigma(signal)^2/sigma(ambient)^2, when ambient is close to 0 (which could happen
  // because we are not looking at statistics across multiple runs, but in the same spectra), we get
  // very high SNR. Therefore, we want to eliminate spectra where less than a 1000 ions are detected
  static final Double NONTRIVIAL_SIGNAL = 1e3d;
  // good clean signals show within about +-3-5 seconds of the peak.
  static final Double PEAK_WIDTH = 10.0d; // seconds

  private Double mzTolerance = MS1_MZ_TOLERANCE_DEFAULT;
  private Integer maxDetectionsInWindow = MAX_MZ_IN_WINDOW_DEFAULT;
  private boolean useSNRForThreshold = USE_SNR_FOR_THRESHOLD_DEFAULT;

  public MS1() { }

  public MS1(boolean useFineGrainedMZTolerance, boolean useSNR) {
    mzTolerance = useFineGrainedMZTolerance ? MS1_MZ_TOLERANCE_FINE : MS1_MZ_TOLERANCE_COARSE;
    maxDetectionsInWindow = useFineGrainedMZTolerance ? MAX_MZ_IN_WINDOW_FINE : MAX_MZ_IN_WINDOW_COARSE;
    useSNRForThreshold = useSNR;
  }

  public double extractMZ(double mzWanted, List<Pair<Double, Double>> intensities) {
    double intensityFound = 0;
    int numWithinPrecision = 0;
    double mzLowRange = mzWanted - this.mzTolerance;
    double mzHighRange = mzWanted + this.mzTolerance;
    // we expect there to be pretty much only one intensity value in the precision
    // range we are looking at. But if a lot of masses show up then complain
    for (Pair<Double, Double> mz_int : intensities) {
      double mz = mz_int.getLeft();
      double intensity = mz_int.getRight();

      if (mz >= mzLowRange && mz <= mzHighRange) {
        intensityFound += intensity;
        numWithinPrecision++;
      }
    }

    if (numWithinPrecision > maxDetectionsInWindow) {
      LOGGER.warn("Only expected %d, but found %d in the mz range [%f, %f]",
          maxDetectionsInWindow, numWithinPrecision, mzLowRange, mzHighRange);
    }

    return intensityFound;
  }

  private List<YZ> toYZSpectra(List<Pair<Double, Double>> intensities) {
    List<YZ> scan = new ArrayList<>();
    for (Pair<Double, Double> detected : intensities) {
      // L is mz
      // R is intensity
      scan.add(new YZ(detected.getLeft(), detected.getRight()));
    }
    return scan;
  }

  private TIC_MzAtMax getTIC(String ms1File) throws Exception {
    Iterator<LCMSSpectrum> ms1Iter = new LCMSNetCDFParser().getIterator(ms1File);

    Double maxTI = null;
    List<YZ> scanAtMax = null;
    List<XZ> tic = new ArrayList<>();

    while (ms1Iter.hasNext()) {
      LCMSSpectrum timepoint = ms1Iter.next();

      // what is the total intensity (across all mz) at this timepoint?
      Double ti = timepoint.getTotalIntensity();

      // add the data point to the TIC chromatogram
      tic.add(new XZ(timepoint.getTimeVal(), ti));
      
      // get all (mz, intensity) at this timepoint
      List<Pair<Double, Double>> intensities = timepoint.getIntensities();

      // update the max total intensity if it is
      if (maxTI == null || maxTI < ti) {
        maxTI = ti;
        scanAtMax = toYZSpectra(intensities);
      }

    }

    TIC_MzAtMax chrom = new TIC_MzAtMax();
    chrom.tic = tic;
    chrom.mzScanAtMaxIntensity = scanAtMax;

    return chrom;
  }

  public MS1ScanForWellAndMassCharge getMS1(Map<String, Double> metlinMasses, String ms1File)
      throws Exception {
    return getMS1(metlinMasses, new LCMSNetCDFParser().getIterator(ms1File));
  }

  private MS1ScanForWellAndMassCharge getMS1(
      Map<String, Double> metlinMasses, Iterator<LCMSSpectrum> ms1File) {

    // create the map with placeholder empty lists for each ion
    // we will populate this later when we go through each timepoint
    MS1ScanForWellAndMassCharge scanResults = new MS1ScanForWellAndMassCharge();
    for (String ionDesc : metlinMasses.keySet()) {
      List<XZ> ms1 = new ArrayList<>();
      scanResults.getIonsToSpectra().put(ionDesc, ms1);
    }

    while (ms1File.hasNext()) {
      LCMSSpectrum timepoint = ms1File.next();
      // get all (mz, intensity) at this timepoint
      List<Pair<Double, Double>> intensities = timepoint.getIntensities();

      // for this timepoint, extract each of the ion masses from the METLIN set
      for (Map.Entry<String, Double> metlinMass : metlinMasses.entrySet()) {
        String ionDesc = metlinMass.getKey();
        Double ionMz = metlinMass.getValue();

        // this time point is valid to look at if its max intensity is around
        // the mass we care about. So lets first get the max peak location
        double intensityForMz = extractMZ(ionMz, intensities);

        // the above is Pair(mz_extracted, intensity), where mz_extracted = mz
        // we now add the timepoint val and the intensity to the output
        XZ intensityAtThisTime = new XZ(timepoint.getTimeVal(), intensityForMz);
        scanResults.getIonsToSpectra().get(ionDesc).add(intensityAtThisTime);
      }
    }

    // populate statistics about the curve for each ion curve
    for (String ionDesc : metlinMasses.keySet()) {
      computeAndStorePeakProfile(scanResults, ionDesc);
    }

    // set the yaxis max: if intensity used as filtering function then intensity max, else snr max
    Double globalYAxis = 0.0d;
    Map<String, Double> individualYMax = new HashMap<String, Double>();
    for (String ionDesc : metlinMasses.keySet()) {
      if (useSNRForThreshold && !isGoodPeak(scanResults, ionDesc)) {
        // if we are using SNR for thresholding scans (see code in writeMS1Values)
        // then for scans that have low SNR, their max is irrelevant. So it might
        // be the case that the max is 100k; but SNR is 1.5, which case it will be
        // a lower value signal compared to a max of 10k, and SNR 20.
        continue;
      }
      // this scan will be included in the plots, so its max should be taken into account
      Double maxInt = scanResults.getMaxIntensityForIon(ionDesc);
      individualYMax.put(ionDesc, maxInt);
      globalYAxis = globalYAxis == 0.0d ? maxInt : Math.max(maxInt, globalYAxis);
    }
    scanResults.setMaxYAxis(globalYAxis);
    scanResults.setIndividualMaxIntensities(individualYMax);

    return scanResults;
  }

  /* DO NOT change this function while `getMS1` depends on it.  This approach has been thorougly manually vetted; any
   * changes will eliminate our ability to compare optimized results to a consistent baseline. */
  /**
   * Computes the SNR, max intensity, and peak time for a given ion and the trace available in an MS1Scan object.
   * Returns the retention time of that peak since there doesn't seem to be a good place to store that now...
   *
   * @param scanResults An object containing the trace to analyze.
   * @param ionDesc The ion key to use when performing the trace analysis.
   * @return
   */
  public Double computeAndStorePeakProfile(MS1ScanForWellAndMassCharge scanResults, String ionDesc) {
    List<XZ> curve = scanResults.getIonsToSpectra().get(ionDesc);

    Integer sz = curve.size();
    Double maxIntensity = null;
    Double maxIntensityTime = null;

    for (XZ signal : curve) {
      Double intensity = signal.getIntensity();
      if (maxIntensity == null) {
        maxIntensity = intensity;
        maxIntensityTime = signal.getTime();
      } else {
        if (maxIntensity < intensity) {
          maxIntensity = intensity;
          maxIntensityTime = signal.getTime();
        }
      }
    }

    // split the curve into parts that are the signal (+- from peak), and ambient
    List<XZ> signalIntensities = new ArrayList<>();
    List<XZ> ambientIntensities = new ArrayList<>();
    for (XZ measured : curve) {
      if (measured.getTime() > maxIntensityTime - PEAK_WIDTH / 2.0d &&
          measured.getTime() < maxIntensityTime + PEAK_WIDTH / 2.0d) {
        signalIntensities.add(measured);
      } else {
        ambientIntensities.add(measured);
      }
    }

    Double avgIntensitySignal = average(signalIntensities);
    Double avgIntensityAmbient = average(ambientIntensities);

    Double logSNR = -100.0d, snr = null;
    // only set SNR to real value if the signal is non-trivial
    if (maxIntensity > NONTRIVIAL_SIGNAL) {
      // snr = sigma_signal^2 / sigma_ambient^2
      // where sigma = sqrt(E[(X-mean)^2])
      // The above equation is a simplistic attempt at evaluating the power around the max_peak compared to the rest of
      // the spectra. So we chop out the window into a signal, calculate its deviation from the full_mean
      // (hopefully close to 0) and compare that against the deviation of the rest of the spectra from (again) the
      // full_mean.
      Double mean = avgIntensityAmbient;
      snr = sigmaSquared(signalIntensities, mean) / sigmaSquared(ambientIntensities, mean);
      logSNR = Math.log(snr);
    }

    scanResults.setIntegralForIon(ionDesc, getAreaUnder(curve));
    scanResults.setMaxIntensityForIon(ionDesc, maxIntensity);
    scanResults.setAvgIntensityForIon(ionDesc, avgIntensitySignal, avgIntensityAmbient);
    scanResults.setLogSNRForIon(ionDesc, logSNR);

    if (logSNR > -100.0d) {
      LOGGER.info("%10s: logSNR: %5.1f Max: %7.0f SignalAvg: %7.0f Ambient Avg: %7.0f %s\n", ionDesc, logSNR, maxIntensity, avgIntensitySignal, avgIntensityAmbient, isGoodPeak(scanResults, ionDesc) ? "INCLUDED" : "");
    }

    // TODO: there must be a better way to pass this around...
    return maxIntensityTime;
  }

  public Map<Pair<String, Double>, MS1ScanForWellAndMassCharge> getMultipleMS1s(
      Set<Pair<String, Double>> metlinMasses, String ms1File)
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
        double intensityForMz = extractMZ(ionMz, intensities);

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
      computeAndStorePeakProfile(result, label);

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

  boolean isGoodPeak(MS1ScanForWellAndMassCharge scans, String ion) {
    Double logSNR = scans.getLogSNRForIon(ion);
    return logSNR > LOGSNR_THRESHOLD;
  }

  public enum IonMode { POS, NEG };

  public static class MetlinIonMass {
    // colums in each row from METLIN data, as seen here: 
    // https://metlin.scripps.edu/mz_calc.php?mass=300.120902994

    private IonMode mode; // POS or NEG
    private String name; // M+H, M+K, etc
    private Integer charge;
    private Double mz;

    MetlinIonMass(IonMode mode, String name, Integer charge, Double mz) {
      this.mode = mode; this.name = name; this.charge = charge; this.mz = mz;
    }

    public IonMode getMode() {
      return mode;
    }

    public String getName() {
      return name;
    }
  }

  public static final MetlinIonMass[] ionDeltas = new MetlinIonMass[] {
    new MetlinIonMass(IonMode.POS,   "M+H-2H2O",  1,  35.0128),
    new MetlinIonMass(IonMode.POS,    "M+H-H2O",  1,  17.0028),
    new MetlinIonMass(IonMode.POS,        "M-H",  1,   1.0073),
    new MetlinIonMass(IonMode.POS,  "M-H2O+NH4",  1,  -0.0227),
    new MetlinIonMass(IonMode.POS,        "M+H",  1,  -1.0073),
    new MetlinIonMass(IonMode.POS,       "M+Li",  1,  -7.0160),
    new MetlinIonMass(IonMode.POS,      "M+NH4",  1, -18.0338),
    new MetlinIonMass(IonMode.POS,       "M+Na",  1, -22.9892),
    new MetlinIonMass(IonMode.POS,  "M+CH3OH+H",  1, -33.0335),
    new MetlinIonMass(IonMode.POS,        "M+K",  1, -38.9631),
    new MetlinIonMass(IonMode.POS,    "M+ACN+H",  1, -42.0338),
    new MetlinIonMass(IonMode.POS,    "M+2Na-H",  1, -44.9711),
    new MetlinIonMass(IonMode.POS,   "M+ACN+Na",  1, -64.0157),
    new MetlinIonMass(IonMode.POS,       "M+2H",  2,  -1.0073),
    new MetlinIonMass(IonMode.POS,     "M+H+Na",  2, -11.9982),
    new MetlinIonMass(IonMode.POS,      "M+2Na",  2, -22.9892),
    new MetlinIonMass(IonMode.POS,       "M+3H",  3,  -1.0072),
    new MetlinIonMass(IonMode.POS,    "M+2H+Na",  3,  -8.3346),
    new MetlinIonMass(IonMode.POS,    "M+2Na+H",  3, -15.6619),
    new MetlinIonMass(IonMode.NEG,    "M-H2O-H",  1,  19.0184),
    new MetlinIonMass(IonMode.NEG,        "M-H",  1,   1.0073),
    new MetlinIonMass(IonMode.NEG,        "M+F",  1, -18.9984),
    new MetlinIonMass(IonMode.NEG,    "M+Na-2H",  1, -20.9746),
    new MetlinIonMass(IonMode.NEG,       "M+Cl",  1, -34.9694),
    new MetlinIonMass(IonMode.NEG,     "M+K-2H",  1, -36.9486),
    new MetlinIonMass(IonMode.NEG,     "M+FA-H",  1, -44.9982),
    new MetlinIonMass(IonMode.NEG,   "M+CH3COO",  1, -59.0138),
    new MetlinIonMass(IonMode.NEG,       "M-2H",  2,   1.0073),
    new MetlinIonMass(IonMode.NEG,       "M-3H",  3,   1.0073),
  };

  public static final Set<String> VALID_MS1_IONS;
  static {
    HashSet<String> names = new HashSet<>(ionDeltas.length);
    for (MetlinIonMass mim : ionDeltas) {
      names.add(mim.name);
    }
    VALID_MS1_IONS = Collections.unmodifiableSet(names);
  }

  /**
   * This function takes a mass charge and mode as input and returns a list of metlin ion masses
   * @param mz Mass charge
   * @param ionMode Ion mode to search from
   * @return A list of metlin ion masses
   * @throws IOException
   */
  private static List<MetlinIonMass> queryMetlin(Double mz, IonMode ionMode) throws IOException {
    List<MetlinIonMass> rows = new ArrayList<>();
    for (MetlinIonMass delta : ionDeltas) {

      // only pick ions that are the right mode, else skip
      if (delta.mode != ionMode)
        continue;

      // this delta specifies how to calculate the ionMz; except we need
      // to take care of the charge this ion acquires/looses
      Double ionMz = mz/delta.charge - delta.mz;
      rows.add(new MetlinIonMass(delta.mode, delta.name, delta.charge, ionMz));
    }
    return rows;
  }

  /**
   * This function takes as input a mass charge and ionmode and outputs a map of ion names to mass charge
   * @param mz Mass charge
   * @param ionMode Ion mode
   * @return A map of ion names to their mass charge
   * @throws IOException
   */
  public static Map<String, Double> getIonMasses(Double mz, IonMode ionMode) throws IOException {
    List<MetlinIonMass> rows = queryMetlin(mz, ionMode);
    Map<String, Double> ionMasses = new HashMap<>();
    for (MetlinIonMass metlinMass : rows) {
      ionMasses.put(metlinMass.name, metlinMass.mz);
    }
    return ionMasses;
  }

  /**
   * This function returns the ion mode of a given ion. There is a weird case were M-H is both in the positive
   * and negative ion mode, even if it is a negative ion, so bias towards positive (since our results are more
   * accurate in that mode) for such cases.
   * @param ion The specific query ion
   * @return The ionMode of the query ion
   */
  public static IonMode getIonModeOfIon(String ion) {
    if (ion.equals("M-H")) {
      return IonMode.POS;
    }

    for (MetlinIonMass mass : ionDeltas) {
      if (mass.getName().equals(ion)) {
        return mass.getMode();
      }
    }
    return null;
  }

  private static boolean areNCFiles(String[] fnames) {
    for (String n : fnames) {
      LOGGER.debug(".nc file = %s", n);
      if (!n.endsWith(".nc"))
        return false;
    }
    return true;
  }

  private boolean lowSignalInEntireSpectrum(List<XZ> ms1, Double threshold) {
    XZ maxSignal = null;
    for (XZ xz : ms1) {
      if (maxSignal == null || xz.getIntensity() > maxSignal.getIntensity()) {
        maxSignal = xz;
      }
    }

    // check if the max is below the threshold
    return maxSignal.getIntensity() < threshold;
  }

  class TIC_MzAtMax {
    List<XZ> tic;
    List<YZ> mzScanAtMaxIntensity;
  }

  public double getAreaUnder(List<XZ> curve) {
    Double timePrev = curve.get(0).getTime();
    Double areaTotal = 0.0d;

    for (XZ curveVal : curve) {
      Double height = curveVal.getIntensity();
      Double timeDelta = curveVal.getTime() - timePrev;

      // compute the area occupied by the slice
      Double areaDelta = height * timeDelta;

      // add this slice to the area under the curve
      areaTotal += areaDelta;

      // move the left boundary of the time slice forward
      timePrev = curveVal.getTime();
    }

    return areaTotal;
  }

  public double average(List<XZ> curve) {
    Double avg = 0.0d;
    int sz = curve.size();
    for (XZ curveVal : curve) {
      Double intensity = curveVal.getIntensity();
      avg += intensity / sz;
    }
    return avg;
  }

  private double sigmaSquared(List<XZ> curve, Double mean) {
    // computes sigma squared, i.e., E[(X-mean)^2]
    Double exp = 0.0d;
    int sz = curve.size();
    for (XZ curveVal : curve) {
      Double X = curveVal.getIntensity();
      Double devSqrd = (X - mean) * (X - mean);
      exp += devSqrd / sz;
    }
    return exp;
  }

  private enum PlotModule { RAW_SPECTRA, TIC, FEEDINGS };

  public static void main(String[] args) throws Exception {
    if (args.length < 8 || !areNCFiles(Arrays.copyOfRange(args, 7, args.length))) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mz for main product, e.g., 431.1341983 (ononin) \n" +
          "(2) ion mode = POS OR NEG \n" +
          "(3) ion = M+H or M+Na etc \n" +
          "(4) prefix for .data and rendered .pdf \n" +
          "(5) {heatmap, default=no heatmap, i.e., 2d} \n" +
          "(6) {overlay, default=separate plots} \n" +
          "(7) {" + StringUtils.join(PlotModule.values(), ", ") + "} \n" +
          "(8,9..) NetCDF .nc file 01.nc from MS1 run \n"
          );
    }

    String fmt = "pdf";
    Double mz = Double.parseDouble(args[0]);
    IonMode ionMode = IonMode.valueOf(args[1]);
    String ion = args[2];
    String outPrefix = args[3];
    boolean makeHeatmap = args[4].equals("heatmap");
    boolean overlayPlots = args[5].equals("overlay");
    PlotModule module = PlotModule.valueOf(args[6]);
    String[] ms1Files = Arrays.copyOfRange(args, 7, args.length);
    boolean plotsHaveIndependentYAxis = true;
    WriteAndPlotMS1Results plottingUtil = new WriteAndPlotMS1Results();

    MS1 c = new MS1();
    Map<String, Double> metlinMasses = c.getIonMasses(mz, ionMode);

    MS1ScanForWellAndMassCharge ms1ScanResults;
    Map<String, List<XZ>> ms1s;
    switch (module) {
      case RAW_SPECTRA:
        for (String ms1File : ms1Files) {
          ms1ScanResults = c.getMS1(metlinMasses, ms1File);
          Double maxYAxis = ms1ScanResults.getMaxYAxis();
          Map<String, Double> individualMaxIntensities = null; 
          // if we wish each plot to have an independent y-range (to show internal structure, as opposed
          // to compare across different spectra), then we set the individual maxes
          if (plotsHaveIndependentYAxis) {
            individualMaxIntensities = ms1ScanResults.getIndividualMaxYAxis();
          }

          plottingUtil.plotSpectra(ms1ScanResults.getIonsToSpectra(), maxYAxis, individualMaxIntensities, metlinMasses,
              outPrefix, fmt, makeHeatmap, overlayPlots);
        }
        break;

      case FEEDINGS:
        // for now we assume the concentrations are in log ramped up
        Double[] concVals = new Double[] { 0.0001d, 0.001d, 0.01d, 0.025d,  0.05d, 0.1d };
        int concIdx = 0;

        List<Pair<Double, MS1ScanForWellAndMassCharge>> rampUp = new ArrayList<>();
        for (String ms1File : ms1Files) {
          ms1ScanResults = c.getMS1(metlinMasses, ms1File);
          // until we read from the db, artificial values
          Double concentration = concIdx < concVals.length ? concVals[concIdx] : concVals[concVals.length - 1] * 10 * (concIdx - concVals.length + 1); 
          concIdx++;
          LOGGER.info("Well %s label concentration: %e", ms1File, concentration);
          rampUp.add(Pair.of(concentration, ms1ScanResults));
        }

        plottingUtil.plotFeedings(rampUp, ion, outPrefix, fmt, outPrefix + ".gnuplot");
        break;

      case TIC:
        for (String ms1File : ms1Files) {
          // get and plot Total Ion Chromatogram
          TIC_MzAtMax totalChrom = c.getTIC(ms1File);
          plottingUtil.plotTIC(totalChrom.tic, outPrefix + ".TIC", fmt);
          plottingUtil.plotScan(totalChrom.mzScanAtMaxIntensity, outPrefix + ".MaxTICScan", fmt);
        }
        break;
    }
  }
}
