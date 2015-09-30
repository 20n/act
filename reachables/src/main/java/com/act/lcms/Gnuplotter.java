package com.act.lcms;

import java.util.Scanner;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class Gnuplotter {

  private String sanitize(String fname) {
    // Gnuplot assumes LaTeX style for text, so when we put
    // the file name in the label it get mathified. Escape _ 
    // so that they dont get interpretted as subscript ops
    return fname.replace("_", "\\\\_");
  }

  public void plot2D(String dataFile, String pdfFile, String[] dataset_names, Double mz, Double yrange) {
    int numDataSets = dataset_names.length;

    // by default gnuplot plots pdfs to a XxY = 5x3 canvas (in inches)
    // we need about 1.5 inch for each plot on the y-axis, so if there are
    // more than 2 plots beings compared they tend to be squished.
    // So we better adjust the size to 1.5 inches x numDataSets
    double sizeY = 1.5 * numDataSets;
    String cmd = 
      " set terminal pdf size 5," + sizeY + ";" +
      " set output \"" + pdfFile + "\";" +
      " set xlabel \"time in seconds\";" +
      " set ylabel \"intensity\";" +
      " set multiplot layout " + numDataSets + ", 1; " ;
    for (int i = 0; i < numDataSets; i++) {
      cmd += "set lmargin at screen 0.15; ";
      cmd += "set yrange [0:" + yrange + "]; ";
      cmd += "plot \"" + dataFile + "\" index " + i + " title \"" + sanitize(dataset_names[i]) + "\" with lines;";
    }
    cmd += " unset multiplot; set output;";

    String[] plotCompare2D = new String[] { "gnuplot", "-e", cmd };

    plot(plotCompare2D);

  }

  public void plot3D(String dataFile, String pdfFile, String srcNcFile, Double mz) {

    // Gnuplot assumes LaTeX style for text, so when we put
    // the file name in the label it get mathified. Escape _ 
    // so that they dont get interpretted as subscript ops
    String srcNcEsc = sanitize(srcNcFile);

    String cmd = 
      " set terminal pdf; set output \"" + pdfFile + "\";" +
      " set hidden3d; set dgrid 200,200; set xlabel \"m/z\";" +
      " set ylabel \"time in seconds\" offset -4,-1;" +
      " set zlabel \"intensity\" offset 2,7;" + 
      " splot \"" + dataFile + "\" u 2:1:3 with lines" +
      " title \"" + srcNcEsc + " around mass " + mz + "\";";

    String[] plot3DSurface = new String[] { "gnuplot", "-e", cmd };

    plot(plot3DSurface);
  }

  public void plotMulti3D(String dataFile, String pdfFile, String[] dataset_names, double maxz) {
    int numDataSets = dataset_names.length;

    // So we better adjust the size to 1.5 inches x dataset_names.length
    double sizeY = 5 * numDataSets;
    String cmd = 
      " set terminal pdf size 5," + sizeY + ";" +
      " set output \"" + pdfFile + "\";" +
      " set multiplot layout " + numDataSets + ", 1; " ;
    for (int i = 0; i < numDataSets; i++) {
      cmd += " set hidden3d; set dgrid 50,50; ";
      cmd += " set xlabel \"m/z\";";
      cmd += " set ylabel \"time in seconds\";";
      cmd += " set zlabel \"intensity\";";
      cmd += " set zrange [0:" + maxz + "]; ";
      cmd += " splot \"" + dataFile + "\" index " + i + " u 2:1:3 with lines title \"" + sanitize(dataset_names[i]) + "\"; ";
    }
    cmd += " unset multiplot; set output;";

    String[] plot3DMulti = new String[] { "gnuplot", "-e", cmd };

    plot(plot3DMulti);

    // may want to run:
    // convert -delay 20 -loop 0 -alpha off animate* animated.gif
    // delay is specified in /100 of a second, so 20 is 0.2 seconds
    // Install `convert` using: brew install ghostscript; brew install imagemagick
  }

  private void plot(String[] gpCmd) {

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
  
}
