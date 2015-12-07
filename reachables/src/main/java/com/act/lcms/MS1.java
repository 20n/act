package com.act.lcms;

import act.shared.helpers.P;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

public class MS1 {

  public static class YZ {
    Double mz;
    Double intensity;

    public YZ(Double mz, Double intensity) {
      this.mz = mz;
      this.intensity = intensity;
    }
  }

  public static class XZ {
    Double time;
    Double intensity;

    public XZ(Double t, Double i) {
      this.time = t;
      this.intensity = i;
    }
  }

  // In the MS1 case, we look for a very tight window 
  // because we do not noise to broaden our signal
  public static final Double MS1_MZ_TOLERANCE_FINE = 0.001;
  public static final Double MS1_MZ_TOLERANCE_COARSE = 0.01;
  public static final Double MS1_MZ_TOLERANCE_DEFAULT = MS1_MZ_TOLERANCE_COARSE;

  // when aggregating the MS1 signal, we do not expect
  // more than these number of measurements within the
  // mz window specified by the tolerance above
  static final Integer MAX_MZ_IN_WINDOW_FINE = 3;
  static final Integer MAX_MZ_IN_WINDOW_COARSE = 4;
  static final Integer MAX_MZ_IN_WINDOW_DEFAULT = MAX_MZ_IN_WINDOW_COARSE;

  // when using max intensity, the threshold is 20% of max
  static final Double THRESHOLD_PERCENT = 0.20d;

  // when using SNR, the threshold is 60 for snr x max_intensity/1M;
  static final boolean USE_SNR_FOR_THRESHOLD_DEFAULT = true;
  // we experimented and observed the following:
  // At logSNR > 10.0d: only VERY strong peaks are isolated, e.g., those above 10^6 straight up
  // At logSNR > 5.0d : a lot of junky smears appear
  // At logSNR > 6.0d : Midway point that is lenient in allowing for peaks; while eliminating full smears
  static final Double LOGSNR_THRESHOLD = 6.0d;
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

  private double extractMZ(double mzWanted, List<Pair<Double, Double>> intensities) {
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
      System.out.format("Only expected %d, but found %d in the mz range [%f, %f]\n", maxDetectionsInWindow, 
          numWithinPrecision, mzLowRange, mzHighRange);
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

  public MS1ScanResults getMS1(Map<String, Double> metlinMasses, String ms1File)
      throws Exception {
    return getMS1(metlinMasses, new LCMSNetCDFParser().getIterator(ms1File));
  }

  private MS1ScanResults getMS1(
      Map<String, Double> metlinMasses, Iterator<LCMSSpectrum> ms1File) {

    // create the map with placeholder empty lists for each ion
    // we will populate this later when we go through each timepoint
    MS1ScanResults scanResults = new MS1ScanResults();
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
      List<XZ> curve = scanResults.getIonsToSpectra().get(ionDesc);

      Integer sz = curve.size();
      Double maxIntensity = null;
      Double maxIntensityTime = null;

      for (XZ signal : curve) {
        Double intensity = signal.intensity;
        if (maxIntensity == null) {
          maxIntensity = intensity; maxIntensityTime = signal.time;
        } else {
          if (maxIntensity < intensity) {
            maxIntensity = intensity; maxIntensityTime = signal.time;
          }
        }
      }

      // split the curve into parts that are the signal (+- from peak), and ambient
      List<XZ> signalIntensities = new ArrayList<>();
      List<XZ> ambientIntensities = new ArrayList<>();
      for (XZ measured : curve) {
        if (measured.time > maxIntensityTime - PEAK_WIDTH/2.0d && measured.time < maxIntensityTime + PEAK_WIDTH/2.0d) {
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
        Double mean = avgIntensityAmbient;
        snr = sigmaSquared(signalIntensities, mean) / sigmaSquared(ambientIntensities, mean);
        logSNR = Math.log(snr);
      }

      scanResults.setIntegralForIon(ionDesc, getAreaUnder(curve));
      scanResults.setMaxIntensityForIon(ionDesc, maxIntensity);
      scanResults.setAvgIntensityForIon(ionDesc, avgIntensitySignal, avgIntensityAmbient);
      scanResults.setLogSNRForIon(ionDesc, logSNR);

      if (logSNR > -100.0d) {
        System.out.format("%10s: logSNR: %5.1f Max: %7.0f SignalAvg: %7.0f Ambient Avg: %7.0f %s\n", ionDesc, logSNR, maxIntensity, avgIntensitySignal, avgIntensityAmbient, isGoodPeak(scanResults, ionDesc) ? "INCLUDED" : ""); 
      }
    }

    // set the yaxis max: if intensity used as filtering function then intensity max, else snr max
    Double globalYAxis = 0.0d;
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
      globalYAxis = globalYAxis == 0.0d ? maxInt : Math.max(maxInt, globalYAxis);
    }
    scanResults.setMaxYAxis(globalYAxis);

    return scanResults;
  }

  boolean isGoodPeak(MS1ScanResults scans, String ion) {
    Double logSNR = scans.getLogSNRForIon(ion);
    return logSNR > LOGSNR_THRESHOLD;
  }

  public static class MS1ScanResults {
    private Map<String, List<XZ>> ionsToSpectra = new HashMap<>();
    private Map<String, Double> ionsToIntegral = new HashMap<>();
    private Map<String, Double> ionsToMax = new HashMap<>();
    private Map<String, Double> ionsToLogSNR = new HashMap<>();
    private Map<String, Double> ionsToAvgSignal = new HashMap<>();
    private Map<String, Double> ionsToAvgAmbient = new HashMap<>();
    private Double maxYAxis = 0.0d; // default to 0

    MS1ScanResults() { }

    public Double getMaxYAxis() {
      return maxYAxis;
    }

    public Double getMaxIntensityForIon(String ion) {
      return ionsToMax.get(ion);
    }

    /// privates: functions internal to MS1 (some setters and getters)

    private Map<String, List<XZ>> getIonsToSpectra() {
      return ionsToSpectra;
    }

    private void setMaxIntensityForIon(String ion, Double max) {
      this.ionsToMax.put(ion, max);
    }

    private Double getLogSNRForIon(String ion) {
      return ionsToLogSNR.get(ion);
    }

    private void setLogSNRForIon(String ion, Double logsnr) {
      this.ionsToLogSNR.put(ion, logsnr);
    }

    private Double getAvgSignalIntensityForIon(String ion) {
      return ionsToAvgSignal.get(ion);
    }

    private Double getAvgAmbientIntensityForIon(String ion) {
      return ionsToAvgAmbient.get(ion);
    }

    private void setAvgIntensityForIon(String ion, Double avgSignal, Double avgAmbient) {
      this.ionsToAvgSignal.put(ion, avgSignal);
      this.ionsToAvgAmbient.put(ion, avgAmbient);
    }

    private Double getIntegralForIon(String ion) {
      return ionsToIntegral.get(ion);
    }

    private void setIntegralForIon(String ion, Double area) {
      this.ionsToIntegral.put(ion, area);
    }

    private void setMaxYAxis(Double maxYAxis) {
      this.maxYAxis = maxYAxis;
    }
  }

  public enum IonMode { POS, NEG };

  static class MetlinIonMass {
    // colums in each row from METLIN data, as seen here: 
    // https://metlin.scripps.edu/mz_calc.php?mass=300.120902994

    IonMode mode; // POS or NEG
    String name; // M+H, M+K, etc
    Integer charge;
    Double mz;

    MetlinIonMass(IonMode mode, String name, Integer charge, Double mz) {
      this.mode = mode; this.name = name; this.charge = charge; this.mz = mz;
    }
  }

  static final MetlinIonMass[] ionDeltas = new MetlinIonMass[] {
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

  private List<MetlinIonMass> queryMetlin(Double mz, IonMode ionMode) throws IOException {
    List<MetlinIonMass> rows = new ArrayList<>();
    for (MetlinIonMass delta : ionDeltas) {
      // this delta specifies how to calculate the ionMz; except we need
      // to take care of the charge this ion acquires/looses
      Double ionMz = mz/delta.charge - delta.mz;
      rows.add(new MetlinIonMass(delta.mode, delta.name, delta.charge, ionMz));
    }
    return rows;
  }

  public Map<String, Double> getIonMasses(Double mz, IonMode ionMode) throws IOException {
    List<MetlinIonMass> rows = queryMetlin(mz, ionMode);
    Map<String, Double> ionMasses = new HashMap<>();
    for (MetlinIonMass metlinMass : rows) {
      ionMasses.put(metlinMass.name, metlinMass.mz);
    }
    return ionMasses;
  }

  private static boolean areNCFiles(String[] fnames) {
    for (String n : fnames) {
      System.out.println(".nc file = " + n);
      if (!n.endsWith(".nc"))
        return false;
    }
    return true;
  }

  private void plotTIC(List<XZ> tic, String outPrefix, String fmt) throws IOException {
    String outImg = outPrefix + "." + fmt;
    String outData = outPrefix + ".data";
    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outData));

    // print each time point + intensity to outDATA
    for (XZ xz : tic) {
      out.format("%.4f\t%.4f\n", xz.time, xz.intensity);
      out.flush();
    }

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    new Gnuplotter().plot2D(outData, outImg, new String[] { "TIC" }, "time", null, "intensity",
        fmt);
  }

  public void plotScan(List<YZ> scan, String outPrefix, String fmt) throws IOException {
    String outPDF = outPrefix + "." + fmt;
    String outDATA = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outDATA));

    // print out the spectra to outDATA
    for (YZ yz : scan) {
      out.format("%.4f\t%.4f\n", yz.mz, yz.intensity);
      out.flush();
    }

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    new Gnuplotter().plot2DImpulsesWithLabels(outDATA, outPDF, new String[] { "mz distribution at TIC max" }, 
        null, "mz", null, "intensity", fmt);
  } 

  private boolean lowSignalInEntireSpectrum(List<XZ> ms1, Double threshold) {
    XZ maxSignal = null;
    for (XZ xz : ms1) {
      if (maxSignal == null || xz.intensity > maxSignal.intensity) {
        maxSignal = xz;
      }
    }

    // check if the max is below the threshold
    return maxSignal.intensity < threshold;
  }

  public List<String> writeMS1Values(MS1ScanResults scans, Double maxIntensity, Map<String, Double> metlinMzs, OutputStream os, boolean heatmap) throws IOException {
    return writeMS1Values(scans, maxIntensity, metlinMzs, os, heatmap, true);
  }

  public List<String> writeMS1Values(MS1ScanResults scans, Double maxIntensity, Map<String, Double> metlinMzs,
                                     OutputStream os, boolean heatmap, boolean applyThreshold, Set<String> ionsToWrite)
      throws IOException {

    Map<String, List<XZ>> ms1s = scans.getIonsToSpectra();

    // Write data output to outfile
    PrintStream out = new PrintStream(os);

    List<String> plotID = new ArrayList<>(ms1s.size());
    for (Map.Entry<String, List<XZ>> ms1ForIon : ms1s.entrySet()) {
      String ion = ms1ForIon.getKey();
      // Skip ions not in the ionsToWrite set if that set is defined.
      if (ionsToWrite != null && !ionsToWrite.contains(ion)) {
        continue;
      }

      List<XZ> ms1 = ms1ForIon.getValue();

      if (applyThreshold) {
        boolean belowThreshold = false;
        if (useSNRForThreshold) {
          belowThreshold = !isGoodPeak(scans, ion);
        } else {
          belowThreshold = lowSignalInEntireSpectrum(ms1, maxIntensity * THRESHOLD_PERCENT);
        }

        // if there is no signal at this ion mass; skip plotting.
        if (belowThreshold) {
          continue;
        }
      }

      plotID.add(String.format("ion: %s, mz: %.5f", ion, metlinMzs.get(ion)));
      // print out the spectra to outDATA
      for (XZ xz : ms1) {
        if (heatmap) {
          /*
           * When we are building heatmaps, we use gnuplots pm3d package
           * along with `dgrid3d 2000,2` (which averages data into grids 
           * that are 2000 on the time axis and 2 in the y axis), and 
           * `view map` that flattens a 3D graphs into a 2D view.
           * We want time to be on the x-axis and intensity on the z-axis
           * (because that is the one that is mapped to heat colors)
           * but then we need an artificial y-axis. We create proxy y=1
           * and y=2 datapoints, and then dgrid3d averaging over 2 creates
           * a vertical "strip". 
          */
          out.format("%.4f\t1\t%.4f\n", xz.time, xz.intensity);
          out.format("%.4f\t2\t%.4f\n", xz.time, xz.intensity);
        } else {
          out.format("%.4f\t%.4f\n", xz.time, xz.intensity);
        }
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }

    return plotID;
  }

  public List<String> writeMS1Values(MS1ScanResults scans, Double maxIntensity, Map<String, Double> metlinMzs,
                                     OutputStream os, boolean heatmap, boolean applyThreshold) throws IOException {
    return writeMS1Values(scans, maxIntensity, metlinMzs, os, heatmap, applyThreshold, null);
  }

  public void plotSpectra(MS1ScanResults ms1Scans, Double maxIntensity, 
      Map<String, Double> metlinMzs, String outPrefix, String fmt, boolean makeHeatmap, boolean overlayPlots)
      throws IOException {

    String outImg = outPrefix + "." + fmt;
    String outData = outPrefix + ".data";

    // Write data output to outfile
    FileOutputStream out = new FileOutputStream(outData);

    List<String> plotID = writeMS1Values(ms1Scans, maxIntensity, metlinMzs, out, makeHeatmap);

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    Gnuplotter gp = new Gnuplotter();
    String[] plotNames = plotID.toArray(new String[plotID.size()]);

    if (makeHeatmap) {
      gp.plotHeatmap(outData, outImg, plotNames, maxIntensity, fmt);
    } else {
      if (!overlayPlots) {
        gp.plot2D(outData, outImg, plotNames, "time", maxIntensity, "intensity", fmt,
            null, null, null, outImg + ".gnuplot");
      } else {
        gp.plotOverlayed2D(outData, outImg, plotNames, "time", maxIntensity, "intensity", fmt, outImg + ".gnuplot");
      }
    }
  }

  class TIC_MzAtMax {
    List<XZ> tic;
    List<YZ> mzScanAtMaxIntensity;
  }

  public List<String> writeFeedMS1Values(List<Pair<Double, List<XZ>>> ms1s, Double maxIntensity,
      OutputStream os) throws IOException {
    // Write data output to outfile
    PrintStream out = new PrintStream(os);

    List<String> plotID = new ArrayList<>(ms1s.size());
    for (Pair<Double, List<XZ>> ms1ForFeed : ms1s) {
      Double feedingConcentration = ms1ForFeed.getLeft();
      List<XZ> ms1 = ms1ForFeed.getRight();

      plotID.add(String.format("concentration: %5e", feedingConcentration));
      // print out the spectra to outDATA
      for (XZ xz : ms1) {
        out.format("%.4f\t%.4f\n", xz.time, xz.intensity);
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }

    return plotID;
  }

  public void writeFeedMS1Values(List<Pair<Double, Double>> concentrationIntensity, OutputStream os) 
      throws IOException {
    PrintStream out = new PrintStream(os);
    for (Pair<Double, Double> ci : concentrationIntensity)
      out.format("%f\t%f\n", ci.getLeft(), ci.getRight());
    out.flush();
  }

  // input: list sorted on first field of pair of (concentration, ms1 spectra)
  //        the ion of relevance to compare across different spectra
  //        outPrefix for pdfs and data, and fmt (pdf or png) of output
  public void plotFeedings(List<Pair<Double, MS1ScanResults>> feedings, String ion, String outPrefix,
                           String fmt, String gnuplotFile)
      throws IOException {
    String outSpectraImg = outPrefix + "." + fmt;
    String outSpectraData = outPrefix + ".data";
    String outFeedingImg = outPrefix + ".fed." + fmt;
    String outFeedingData = outPrefix + ".fed.data";
    String feedingGnuplotFile = gnuplotFile + ".fed";

    boolean useMaxPeak = true;

    // maps that hold the values for across different concentrations
    List<Pair<Double, List<XZ>>> concSpectra = new ArrayList<>();
    List<Pair<Double, Double>> concAreaUnderSpectra = new ArrayList<>();
    List<Pair<Double, Double>> concMaxPeak = new ArrayList<>();

    // we will compute a running max of the intensity in the plot, and integral
    Double maxIntensity = 0.0d, maxAreaUnder = 0.0d;

    // now compute the maps { conc -> spectra } and { conc -> area under spectra }
    for (Pair<Double, MS1ScanResults> feedExpr : feedings) {
      Double concentration = feedExpr.getLeft();
      MS1ScanResults scan = feedExpr.getRight();

      // get the ms1 spectra for the selected ion, and the max for it as well
      List<XZ> ms1 = scan.getIonsToSpectra().get(ion);
      Double maxInThisSpectra = scan.getMaxIntensityForIon(ion);
      Double areaUnderSpectra = scan.getIntegralForIon(ion);

      // update the max intensity over all different spectra
      maxIntensity = Math.max(maxIntensity, maxInThisSpectra);
      maxAreaUnder = Math.max(maxAreaUnder, areaUnderSpectra);

      // install this concentration and spectra in map, to be dumped to file later
      concSpectra.add(Pair.of(concentration, ms1));
      concAreaUnderSpectra.add(Pair.of(concentration, areaUnderSpectra));
      concMaxPeak.add(Pair.of(concentration, maxInThisSpectra));
    }

    // Write data output to outfiles
    List<String> plotID = null;
    try (FileOutputStream outSpectra = new FileOutputStream(outSpectraData)) {
      plotID = writeFeedMS1Values(concSpectra, maxIntensity, outSpectra);
    }

    try (FileOutputStream outFeeding = new FileOutputStream(outFeedingData)) {
      writeFeedMS1Values(useMaxPeak ? concMaxPeak : concAreaUnderSpectra, outFeeding);
    }

    // render outDATA to outPDF using gnuplot
    Gnuplotter gp = new Gnuplotter();
    String[] plotNames = plotID.toArray(new String[plotID.size()]);
    gp.plotOverlayed2D(outSpectraData, outSpectraImg, plotNames, "time", maxIntensity, "intensity", fmt, gnuplotFile);
    gp.plot2D(outFeedingData, outFeedingImg, new String[] { "feeding ramp" }, "concentration",
        useMaxPeak ? maxIntensity : maxAreaUnder, "integrated area under spectra", fmt, null, null, null,
        feedingGnuplotFile);
  }

  public double getAreaUnder(List<XZ> curve) {
    Double timePrev = curve.get(0).time;
    Double areaTotal = 0.0d;

    for (XZ curveVal : curve) {
      Double height = curveVal.intensity;
      Double timeDelta = curveVal.time - timePrev;

      // compute the area occupied by the slice
      Double areaDelta = height * timeDelta;

      // add this slice to the area under the curve
      areaTotal += areaDelta;

      // move the left boundary of the time slice forward
      timePrev = curveVal.time;
    }

    return areaTotal;
  }

  public double average(List<XZ> curve) {
    Double avg = 0.0d;
    int sz = curve.size();
    for (XZ curveVal : curve) {
      Double intensity = curveVal.intensity;
      avg += intensity / sz;
    }
    return avg;
  }

  private double sigmaSquared(List<XZ> curve, Double mean) {
    // computes sigma squared, i.e., E[(X-mean)^2]
    Double exp = 0.0d;
    int sz = curve.size();
    for (XZ curveVal : curve) {
      Double X = curveVal.intensity;
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

    MS1 c = new MS1();
    Map<String, Double> metlinMasses = c.getIonMasses(mz, ionMode);

    MS1ScanResults ms1ScanResults;
    Map<String, List<XZ>> ms1s;
    switch (module) {
      case RAW_SPECTRA:
        for (String ms1File : ms1Files) {
          ms1ScanResults = c.getMS1(metlinMasses, ms1File);
          Double maxYAxis = ms1ScanResults.getMaxYAxis();
          c.plotSpectra(ms1ScanResults, maxYAxis, metlinMasses, outPrefix, fmt, makeHeatmap, overlayPlots);
        }
        break;

      case FEEDINGS:
        // for now we assume the concentrations are in log ramped up
        Double[] concVals = new Double[] { 0.0001d, 0.001d, 0.01d, 0.025d,  0.05d, 0.1d };
        int concIdx = 0;

        List<Pair<Double, MS1ScanResults>> rampUp = new ArrayList<>();
        for (String ms1File : ms1Files) {
          ms1ScanResults = c.getMS1(metlinMasses, ms1File);
          // until we read from the db, artificial values
          Double concentration = concIdx < concVals.length ? concVals[concIdx] : concVals[concVals.length - 1] * 10 * (concIdx - concVals.length + 1); 
          concIdx++;
          System.out.format("Well %s label concentration: %e\n", ms1File, concentration);
          rampUp.add(Pair.of(concentration, ms1ScanResults));
        }

        c.plotFeedings(rampUp, ion, outPrefix, fmt, outPrefix + ".gnuplot");
        break;

      case TIC:
        for (String ms1File : ms1Files) {
          // get and plot Total Ion Chromatogram
          TIC_MzAtMax totalChrom = c.getTIC(ms1File);
          c.plotTIC(totalChrom.tic, outPrefix + ".TIC", fmt);
          c.plotScan(totalChrom.mzScanAtMaxIntensity, outPrefix + ".MaxTICScan", fmt);
        }
        break;
    }
  }
}
