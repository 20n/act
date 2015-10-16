package com.act.lcms;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.Comparator;
import java.io.IOException;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import org.apache.commons.lang3.tuple.Pair;

public class MS1MetlinMasses {

  class YZ {
    Double mz;
    Double intensity;

    public YZ(Double mz, Double intensity) {
      this.mz = mz;
      this.intensity = intensity;
    }
  }

  class XZ {
    Double time;
    Double intensity;

    public XZ(Double t, Double i) {
      this.time = t;
      this.intensity = i;
    }
  }

  // In the MS1 case, we look for a very tight window 
  // because we do not noise to broaden our signal
  final static Double MS1_MZ_TOLERANCE = 0.001;

  // when aggregating the MS1 signal, we do not expect
  // more than these number of measurements within the
  // mz window specified by the tolerance above
  static final Integer MAX_MZ_IN_WINDOW = 3;

  static final Double THRESHOLD_PERCENT = 0.20;

  private double extractMZ(double mzWanted, List<Pair<Double, Double>> intensities) {
    double intensityFound = 0;
    int numWithinPrecision = 0;
    double mzLowRange = mzWanted - MS1_MZ_TOLERANCE;
    double mzHighRange = mzWanted + MS1_MZ_TOLERANCE;
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

    if (numWithinPrecision > MAX_MZ_IN_WINDOW) {
      System.out.format("Only expected %d, but found %d in the mz range [%f, %f]\n", MAX_MZ_IN_WINDOW, 
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

      // get all (mz, intensity) at this timepoint
      List<Pair<Double, Double>> intensities = timepoint.getIntensities();

      // what is the total intensity (across all mz) at this timepoint?
      Double ti = timepoint.getTotalIntensity();

      // add the data point to the TIC chromatogram
      tic.add(new XZ(timepoint.getTimeVal(), ti));
      
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

  private Pair<Map<String, List<XZ>>, Double> getMS1(Map<String, Double> metlinMasses, String ms1File) throws Exception {
    return getMS1(metlinMasses, new LCMSNetCDFParser().getIterator(ms1File));
  }

  private Pair<Map<String, List<XZ>>, Double> getMS1(Map<String, Double> metlinMasses, Iterator<LCMSSpectrum> ms1File) {

    // create the map with placeholder empty lists for each ion
    // we will populate this later when we go through each timepoint
    Map<String, List<XZ>> ms1AtVariousMasses = new HashMap<>();
    for (String ionDesc : metlinMasses.keySet()) {
      List<XZ> ms1 = new ArrayList<>();
      ms1AtVariousMasses.put(ionDesc, ms1);
    }

    Double maxIntensity = null;

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

        maxIntensity = maxIntensity == null ? intensityForMz : Math.max(intensityForMz, maxIntensity);

        // the above is Pair(mz_extracted, intensity), where mz_extracted = mz
        // we now add the timepoint val and the intensity to the output
        XZ intensityAtThisTime = new XZ(timepoint.getTimeVal(), intensityForMz);
        ms1AtVariousMasses.get(ionDesc).add(intensityAtThisTime);
      }
    }

    return Pair.of(ms1AtVariousMasses, maxIntensity);
  }

  static class MetlinIonMass {
    // colums in each row from METLIN data, as seen here: 
    // https://metlin.scripps.edu/mz_calc.php?mass=300.120902994

    String mode; // pos or neg
    String name; // M+H, M+K, etc
    Integer charge;
    Double mz;

    MetlinIonMass(String mode, String name, Integer charge, Double mz) {
      this.mode = mode; this.name = name; this.charge = charge; this.mz = mz;
    }
  }

  static final MetlinIonMass[] ionDeltas = new MetlinIonMass[] {
    new MetlinIonMass("pos",   "M+H-2H2O",  1,  35.0128),
    new MetlinIonMass("pos",    "M+H-H2O",  1,  17.0028),
    new MetlinIonMass("pos",        "M-H",  1,   1.0073),
    new MetlinIonMass("pos",  "M-H2O+NH4",  1,  -0.0227),
    new MetlinIonMass("pos",        "M+H",  1,  -1.0073),
    new MetlinIonMass("pos",       "M+Li",  1,  -7.0160),
    new MetlinIonMass("pos",      "M+NH4",  1, -18.0338),
    new MetlinIonMass("pos",       "M+Na",  1, -22.9892),
    new MetlinIonMass("pos",  "M+CH3OH+H",  1, -33.0335),
    new MetlinIonMass("pos",        "M+K",  1, -38.9631),
    new MetlinIonMass("pos",    "M+ACN+H",  1, -42.0338),
    new MetlinIonMass("pos",    "M+2Na-H",  1, -44.9711),
    new MetlinIonMass("pos",   "M+ACN+Na",  1, -64.0157),
    new MetlinIonMass("pos",       "M+2H",  2,  -1.0073),
    new MetlinIonMass("pos",     "M+H+Na",  2, -11.9982),
    new MetlinIonMass("pos",      "M+2Na",  2, -22.9892),
    new MetlinIonMass("pos",       "M+3H",  3,  -1.0072),
    new MetlinIonMass("pos",    "M+2H+Na",  3,  -8.3346),
    new MetlinIonMass("pos",    "M+2Na+H",  3, -15.6619),
    new MetlinIonMass("neg",    "M-H2O-H",  1,  19.0184),
    new MetlinIonMass("neg",        "M-H",  1,   1.0073),
    new MetlinIonMass("neg",        "M+F",  1, -18.9984),
    new MetlinIonMass("neg",    "M+Na-2H",  1, -20.9746),
    new MetlinIonMass("neg",       "M+Cl",  1, -34.9694),
    new MetlinIonMass("neg",     "M+K-2H",  1, -36.9486),
    new MetlinIonMass("neg",     "M+FA-H",  1, -44.9982),
    new MetlinIonMass("neg",   "M+CH3COO",  1, -59.0138),
    new MetlinIonMass("neg",       "M-2H",  2,   1.0073),
    new MetlinIonMass("neg",       "M-3H",  3,   1.0073),
  };

  private List<MetlinIonMass> queryMetlin(Double mz) throws IOException {
    List<MetlinIonMass> rows = new ArrayList<>();
    for (MetlinIonMass delta : ionDeltas) {
      // this delta specifies how to calculate the ionMz; except we need
      // to take care of the charge this ion acquires/looses
      Double ionMz = mz/delta.charge - delta.mz;
      rows.add(new MetlinIonMass(delta.mode, delta.name, delta.charge, ionMz));

      System.out.format("%s\t%10s\t%d\t%8.4f\t%8.4f\n", delta.mode, delta.name, delta.charge, delta.mz, ionMz);
    }
    return rows;
  }

  private List<MetlinIonMass> queryMetlinOLD(Double mz) throws IOException {
    String query = "https://metlin.scripps.edu/mz_calc.php?mass=" + mz;
    
    URL metlin = new URL(query);
    BufferedReader in = new BufferedReader(new InputStreamReader(metlin.openStream()));

    String prefix = "<table border=1>";
    String suffix = "</table>";
    String ln, dataLine = null;
    while ((ln = in.readLine()) != null) {
      if (!ln.startsWith(prefix))
        continue;
      dataLine = ln; 
      if (!dataLine.endsWith(suffix)) {
        dataLine = null;
      } else {
        // remove the prefix and suffix
        dataLine = dataLine.substring(prefix.length(), dataLine.length() - suffix.length());
      }
      break;
    }
    in.close();

    // split dataLine into rows of the table
    List<MetlinIonMass> rows = new ArrayList<>();
    String[] rs = dataLine.split("</tr>");
    // we iterate from [1:n] assuming the first row is the `th` row with 
    // column headers MODE NAME CHARGE m/z
    for (int i = 1; i < rs.length; i++) {
      // remove the row opening tag (the closing tag already removed by split
      String r = rs[i].substring("<tr>".length());
      String[] rowspl = r.split("</td>");
      List<String> row = new ArrayList<>();
      for (String td : rowspl) {
        // remove some HTML junk that is present
        td = td.replace("&nbsp;", ""); 
        td = td.replace(" align=left", "");
        row.add(td.substring("<td>".length()));
      }
      if (row.size() != 4)
        throw new RuntimeException("Table format unexpected. Expecting 4 col row but recvd: " + r);

      String mode = row.get(0);
      String name = row.get(1);
      Integer charge = Integer.parseInt(row.get(2));
      Double rowMz = Double.parseDouble(row.get(3));

      // this delta shows up consistent across different runs with different mz values
      // therefore, to compute rowMz from mz the formula is: mz/charge - delta
      // where charge, and delta are hardcoded values we gather from a run of this
      // function that scrapes METLIN
      Double delta = (mz/charge) - rowMz;
      System.out.format("%s\t%10s\t%d\t%8.4f\t%8.4f\n", mode, name, charge, delta, rowMz);

      rows.add(new MetlinIonMass(mode, name, charge, rowMz));
    }

    return rows;
  }

  private Map<String, Double> scrapeMETLINForMainMass(Double mz, String ionMode) throws IOException {
    List<MetlinIonMass> rows = queryMetlin(mz);
    Map<String, Double> scraped = new HashMap<>();
    for (MetlinIonMass metlinMass : rows) {
      scraped.put(metlinMass.name, metlinMass.mz);
    }
    return scraped;
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

  private void plotScan(List<YZ> scan, String outPrefix, String fmt) throws IOException {
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

  private void plot(Map<String, List<XZ>> ms1s, Double maxIntensity, Map<String, Double> metlinMzs, String outPrefix, String fmt) 
    throws IOException {

    String outImg = outPrefix + "." + fmt;
    String outData = outPrefix + ".data";

    // Write data output to outfile
    PrintStream out = new PrintStream(new FileOutputStream(outData));

    List<String> plotID = new ArrayList<>(ms1s.size());
    for (Map.Entry<String, List<XZ>> ms1ForIon : ms1s.entrySet()) {
      String ion = ms1ForIon.getKey();
      List<XZ> ms1 = ms1ForIon.getValue();

      if (lowSignalInEntireSpectrum(ms1, maxIntensity * THRESHOLD_PERCENT)) {
        // there is really no signal at this ion mass; so skip plotting
        continue;
      }

      plotID.add(String.format("ion: %s, mz: %.5f", ion, metlinMzs.get(ion)));
      // print out the spectra to outDATA
      for (XZ xz : ms1) {
        out.format("%.4f\t%.4f\n", xz.time, xz.intensity);
        out.flush();
      }
      // delimit this dataset from the rest
      out.print("\n\n");
    }

    // close the .data
    out.close();

    // render outDATA to outPDF using gnuplot
    new Gnuplotter().plot2D(outData, outImg, plotID.toArray(new String[plotID.size()]), "time", maxIntensity, "intensity",
        fmt);
  }

  class TIC_MzAtMax {
    List<XZ> tic;
    List<YZ> mzScanAtMaxIntensity;
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 4 || !areNCFiles(new String[] {args[3]})) {
      throw new RuntimeException("Needs: \n" + 
          "(1) mz for main product, e.g., 431.1341983 (ononin) \n" +
          "(2) ion mode = pos OR neg \n" +
          "(3) prefix for .data and rendered .pdf \n" +
          "(4) NetCDF .nc file 01.nc from MS1 run \n"
          );
    }

    String fmt = "pdf";
    Double mz = Double.parseDouble(args[0]);
    String ionMode = args[1];
    String outPrefix = args[2];
    String ms1File = args[3];

    MS1MetlinMasses c = new MS1MetlinMasses();
    Map<String, Double> metlinMasses = c.scrapeMETLINForMainMass(mz, ionMode);
    Pair<Map<String, List<XZ>>, Double> ms1s_max = c.getMS1(metlinMasses, ms1File);
    Map<String, List<XZ>> ms1s = ms1s_max.getLeft();
    Double maxIntensity = ms1s_max.getRight();
    c.plot(ms1s, maxIntensity, metlinMasses, outPrefix, fmt);

    // get and plot Total Ion Chromatogram
    TIC_MzAtMax totalChrom = c.getTIC(ms1File);
    c.plotTIC(totalChrom.tic, outPrefix + ".TIC", fmt);
    c.plotScan(totalChrom.mzScanAtMaxIntensity, outPrefix + ".MaxTICScan", fmt);

  }
}
