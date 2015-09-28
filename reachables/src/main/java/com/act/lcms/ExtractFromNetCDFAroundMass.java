package com.act.lcms;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ExtractFromNetCDFAroundMass {

  /**
   * Extracts a window around a particular m/z value within the spectra
   * @param file The netCDF file with the full LCMS (lockmass corrected) spectra
   * @param mz The m/z value +- 1 around which to extract data for
   * @param numTimepointsToExamine Since we stream the netCDF in, we can choose
   *                               to look at the first few timepoints or -1 for all
   */
  public List<Triple<Double, Double, Double>> get2DWindow(String file, double mz, int numTimepointsToExamine) 
    throws Exception {
    LCMSParser parser = new LCMSNetCDFParser();
    Iterator<LCMSSpectrum> iter = parser.getIterator(file);
    List<Triple<Double, Double, Double>> window = new ArrayList<>();
    int pulled = 0;
    Double mzTightL = mz - 0.1;
    Double mzTightR = mz + 0.1;
    Double mzMinus1Da = mz - 1;
    Double mzPlus1Da = mz + 1;

    // iterate through first few, or all if -1 given
    while (iter.hasNext() && (numTimepointsToExamine == -1 || pulled < numTimepointsToExamine)) {
      LCMSSpectrum timepoint = iter.next();

      List<Pair<Double, Double>> intensities = timepoint.getIntensities();

      // this time point is valid to look at if its max intensity is around
      // the mass we care about. So lets first get the max peak location
      Pair<Double, Double> max_peak = findMaxPeak(intensities);
      Double max_mz = max_peak.getLeft();

      // If the max_mz value is pretty close to our target mass, ie in [mzTightL, mzTightR]
      // Then this timepoint is a good timepoint to output... proceed, shall we.
      if (max_mz >= mzTightL && max_mz <= mzTightR) {

        // For this timepoint, output a window
        for (int k=0; k<intensities.size(); k++) {
          Double mz_here = intensities.get(k).getLeft();
          Double intensity = intensities.get(k).getRight();

          // The window not as tight, but +-1 Da around the target mass
          if (mz_here > mzMinus1Da && mz_here < mzPlus1Da) {
            window.add(Triple.of(timepoint.getTimeVal(), mz_here, intensity));
          }
        }
      }
      pulled++;
    }
    return window;
  }

  private Pair<Double, Double> findMaxPeak(List<Pair<Double, Double>> raw) {   
    // the intensity is the second value in the pairs
    // this finds the pair with the max intensity 

    Pair<Double, Double> max = null;   
    for (Pair<Double, Double> mz_int : raw) {    
      if (max == null || max.getRight() < mz_int.getRight())   
        max = mz_int;    
    }    
    return max;    
  }
  
  public static void runGnuplot(String dataFile, String pdfFile, String srcNcFile, Double mz) throws IOException {

    // Gnuplot assumes LaTeX style for text, so when we put
    // the file name in the label it get mathified. Escape _ 
    // so that they dont get interpretted as subscript ops
    String srcNcEsc = srcNcFile.replace("_", "\\\\_");

    String[] gpCmd = new String[] {
        "gnuplot",
        "-e",
        " set terminal pdf; set output \"" + pdfFile + "\";" +
        " set hidden3d; set dgrid 200,200; set xlabel \"m/z\";" +
        " set ylabel \"time in seconds\" offset -4,-1;" +
        " set zlabel \"intensity\" offset 2,7;" + 
        " splot \"" + dataFile + "\" u 2:1:3 with lines" +
        " title \"" + srcNcEsc + " around mass " + mz + "\";"
    };

    Process gnuplot = null;
    try {
      gnuplot = Runtime.getRuntime().exec(gpCmd);

      // read its input stream in case gnuplot reporting something
      Scanner gpSays = new Scanner(gnuplot.getInputStream());
      while (gpSays.hasNextLine()) {
        System.out.println(gpSays.nextLine());
      }
      gpSays.close();

      // read the error stream in case the plotting failed
      gpSays = new Scanner(gnuplot.getErrorStream());
      while (gpSays.hasNextLine()) {
        System.err.println("E: " + gpSays.nextLine());
      }
      gpSays.close();

      // wait for process to finish
      gnuplot.waitFor();

    } catch (IOException e) {
      System.err.println("ERROR: Cannot locate gnuplot.");
      System.err.println("ERROR: Rerun after installing: brew install gnuplot --with-qt --with-pdflib-lite");
      System.err.println("ERROR: ABORT!\n");
      throw new RuntimeException("No gnuplot in path");
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      if (gnuplot != null) {
        gnuplot.destroy();
      }
    }
  }
  

  public static void main(String[] args) throws Exception {
    if (args.length != 4 || !args[0].endsWith(".nc")) {
      throw new RuntimeException("Needs (1) NetCDF .nc file, " + 
          "(2) mass value, e.g., 132.0772 for debugging, " +
          "(3) how many timepoints to process (-1 for all), " +
          "(4) prefix for .data and rendered .pdf, '-' for stdout");
    }

    String netCDF = args[0];
    Double mz = Double.parseDouble(args[1]);
    Integer numSpectraToProcess = Integer.parseInt(args[2]);
    String outPrefix = args[3];
    String outPDF = outPrefix.equals("-") ? null : outPrefix + ".pdf";
    String outDATA = outPrefix.equals("-") ? null : outPrefix + ".data";

    ExtractFromNetCDFAroundMass e = new ExtractFromNetCDFAroundMass();
    List<Triple<Double, Double, Double>> window = e.get2DWindow(netCDF, mz, numSpectraToProcess);

    // Write data output to outfile
    PrintStream whereTo = outDATA == null ? System.out : new PrintStream(new FileOutputStream(outDATA));
    for (Triple<Double, Double, Double> xyz : window) {
      whereTo.format("%.4f\t%.4f\t%.4f\n", xyz.getLeft(), xyz.getMiddle(), xyz.getRight());
      whereTo.flush();
    }

    if (outDATA != null) {
      // if outDATA is != null, then we have written to .data file
      // now render the .data to the corresponding .pdf file

      // first close the .data
      whereTo.close();

      // render outDATA to outPDF using gnuplo
      runGnuplot(outDATA, outPDF, netCDF, mz);
    }
  }
}
