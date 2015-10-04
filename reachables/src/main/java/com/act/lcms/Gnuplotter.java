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
      if (yrange != -1.0) cmd += "set yrange [0:" + yrange + "]; ";
      cmd += "plot \"" + dataFile + "\" index " + i + " title \"" + sanitize(dataset_names[i]) + "\" with lines;";
    }
    cmd += " unset multiplot; set output;";

    String[] plotCompare2D = new String[] { "gnuplot", "-e", cmd };

    exec(plotCompare2D);

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

    exec(plot3DSurface);
  }

  public void plotMulti3D(String dataFile, String pdfFile, String fmt, String[] dataset_names, double maxz) {
    int numDataSets = dataset_names.length;

    int gridY = 1, gridX = numDataSets; // landscape layout n columns, 1 row
    // So we better adjust the size to 5 inches x #grid cells reqd
    double sizeY = 5 * gridY;
    double sizeX = 5 * gridX;
    if (fmt.equals("png")) { // can be pdf
      // png format takes size in pixels, pdf takes it in inches
      sizeY *= 144; // 144 dpi
      sizeX *= 144; // 144 dpi
    }
    String cmd = 
      " set terminal " + fmt + " size " + sizeX + "," + sizeY + ";" +
      " set output \"" + pdfFile + "\";" +
      " set multiplot layout " + gridY + ", " + gridX + "; " ;
    for (int i = 0; i < numDataSets; i++) {
      cmd += " set hidden3d; set dgrid 50,50; ";
      cmd += " set xlabel \"m/z\";";
      cmd += " unset xtics;"; // remove the xaxis labelling
      cmd += " set ylabel \"time in seconds\";";
      cmd += " set zlabel \"intensity\" offset 0,-12;";
      if (maxz != -1) cmd += " set zrange [0:" + maxz + "]; ";
      cmd += " splot \"" + dataFile + "\" index " + i + " u 2:1:3 with lines title \"" + sanitize(dataset_names[i]) + "\"; ";
    }
    cmd += " unset multiplot; set output;";

    String[] plot3DMulti = new String[] { "gnuplot", "-e", cmd };

    exec(plot3DMulti);

  }

  public void makeAnimatedGIF(String frames, String gifFile) {

    // run the imagemagick convert utility to convert this into a animated GIF
    // delay is specified in /100 of a second, so 20 is 0.2 seconds
    String[] animatedGif = new String[] { "convert", 
      "-delay", "80", 
      "-loop", "1", 
      "-dispose", "previous",
      frames, 
      gifFile
    };

    exec(animatedGif);
  }

  private void exec(String[] cmd) {

    Process proc = null;
    try {
      proc = Runtime.getRuntime().exec(cmd);

      // read its input stream in case the process reports something
      Scanner procSays = new Scanner(proc.getInputStream());
      while (procSays.hasNextLine()) {
        System.out.println(procSays.nextLine());
      }
      procSays.close();

      // read the error stream in case the plotting failed
      procSays = new Scanner(proc.getErrorStream());
      while (procSays.hasNextLine()) {
        System.err.println("E: " + procSays.nextLine());
      }
      procSays.close();

      // wait for process to finish
      proc.waitFor();

    } catch (IOException e) {
      System.err.println("ERROR: Cannot locate executable for " + cmd[0]);
      System.err.println("ERROR: Rerun after installing: ");
      System.err.println("If gnuplot you need: brew install gnuplot --with-qt --with-pdflib-lite");
      System.err.println("If convert you need: brew install ghostscript; brew install imagemagick");
      System.err.println("ERROR: ABORT!\n");
      throw new RuntimeException("Required " + cmd[0] + " not in path");
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      if (proc != null) {
        proc.destroy();
      }
    }
  }
  
}
