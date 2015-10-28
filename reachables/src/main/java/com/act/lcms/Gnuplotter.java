package com.act.lcms;

import java.util.Scanner;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.lang.StringBuffer;

public class Gnuplotter {

  private Double fontScale = null;

  private String sanitize(String fname) {
    // Gnuplot assumes LaTeX style for text, so when we put
    // the file name in the label it get mathified. Escape _ 
    // so that they dont get interpretted as subscript ops
    return fname.replace("_", "\\\\_");
  }

  private enum Plot2DType { IMPULSES, LINES, OVERLAYED_LINES, HEATMAP };

  public void plotHeatmap(String dataFile, String outFile, String[] setNames, Double yrange, String fmt) {
    plot2DHelper(Plot2DType.HEATMAP, dataFile, outFile, setNames, null, null, yrange, null, true, fmt);
  }

  public void plotOverlayed2D(String dataFile, String outFile, String[] setNames, String xlabel, Double yrange, 
      String ylabel, String fmt) {
    // plotOverlayed2D produces the same graph as plot2D, except it collapses all datasets into a single plot
    plot2DHelper(Plot2DType.OVERLAYED_LINES, dataFile, outFile, setNames, null, xlabel, yrange, ylabel, true, fmt);
  }

  public void plot2D(String dataFile, String outFile, String[] setNames, String xlabel, Double yrange, 
      String ylabel, String fmt) {
    plot2DHelper(Plot2DType.LINES, dataFile, outFile, setNames, null, xlabel, yrange, ylabel, true, fmt);
  }

  public void plot2DImpulsesWithLabels(String dataFile, String outFile, String[] setNames, Double xrange, String xlabel,
      Double yrange, String ylabel, String fmt) {
    plot2DHelper(Plot2DType.IMPULSES, dataFile, outFile, setNames, xrange, xlabel, yrange, ylabel, true, fmt);
  }


  /*
     == null -> all graphs in the set have their own autoadjusted y ranges. can see maximum detail in each chart, but makes it difficult to compare across the set.
     != null -> all graphs are uniformly scaled to a yrange value. makes it easy to compare.
  */
  /**
   * Helps plot 2D data in a grid
   * 
   * @param plotTyp IMPULSES plots vertical lines from xaxis to data -- for sparse plots
   *                LINES plots a curve connecting points -- for dense plots
   *                HEATMAP plots a grayscale heatmap plot -- for quick comparisons across many
   * @param dataFile file with 2D (x,y) pair data, 2 NL separation between data sets
   * @param outFile  filename to write the output pdf or png image to
   * @param setNames labels for the different data sets in dataFile
   * @param xlabel   x-axis label
   * @param yrange   y-axis max; if null, all graphs in the set are autoadjusted to their
   *                 respective maximums; if !null, all graphs uniformly maxed to the provided
   * @param ylabel   y-axis label
   * @param showKey  does the graph show a legend for the plotted points
   * @param fmt      "png" or "pdf" (default)
   */
  private void plot2DHelper(Plot2DType plotTyp, String dataFile, String outFile, String[] setNames, Double xrange,
                            String xlabel, Double yrange, String ylabel, boolean showKey, String fmt) {
    int numDataSets = setNames.length;

    // layout 1 column
    int gridX = 1;
    // layout n columns, unless graphs merged together using overlayed plots
    int gridY = plotTyp.equals(Plot2DType.OVERLAYED_LINES) ? 1 : numDataSets; 

    // by default gnuplot plots pdfs to a XxY = 5x3 canvas (in inches)
    // we need about 1.5 inch for each plot on the y-axis, so if there are
    // more than 2 plots beings compared they tend to be squished.
    // So we better adjust the size to 1.5 x 5 inches x #grid cells reqd
    double sizeY = 1.5 * gridY;
    double sizeX = 5 * gridX;

    // fmt "pdf" or "png"
    if ("png".equals(fmt)) {
      // png format takes size in pixels, pdf takes it in inches
      sizeY *= 144; // 144 dpi
      sizeX *= 144; // 144 dpi
    }

    StringBuffer cmd = new StringBuffer();

    if (!showKey)
      cmd.append(" unset key;");

    String fontscale = this.fontScale == null ? "" : String.format(" fontscale %.2f", this.fontScale);

    if (plotTyp.equals(Plot2DType.HEATMAP)) {
      cmd.append(" set view map;");
      cmd.append(" set dgrid3d 2,1000;");

      // cmd.append(" set palette defined ( 0 0 0 0, 1 1 1 1 );"); // white peaks on black bg
      // cmd.append(" set palette defined ( 0 0 0 0, 1 1 0 0 );"); // red peaks on black bg
      // cmd.append(" set palette defined ( 1 1 1 1, 1 1 0 0 );"); // red peaks on white bg
      // cmd.append(" set palette defined ( 1 1 1 1, 1 0 0 0 );"); // black peaks on white bg
      // cmd.append(" set palette defined ( 1 1 1 1, 1 0 0 1 );"); // blue peaks on white bg

      // peak -> background = white, yellow, red, black
      cmd.append(" set palette defined ( 0 0 0 0, 10 1 0 0, 20 1 1 0, 30 1 1 1, 40 1 1 1 );");

      cmd.append(" unset ytics;"); // do not show the [1,2] proxy labels
    }

    if (xlabel != null)
      cmd.append(" set xlabel \"" + xlabel + "\";");

    if (ylabel != null)
      cmd.append(" set ylabel \"" + ylabel + "\";");

    cmd.append(" set terminal " + fmt + " size " + sizeX + "," + sizeY + fontscale + ";");
    cmd.append(" set output \"" + outFile + "\";");
    cmd.append(" set multiplot layout " + gridY + ", 1; ");

    if (!plotTyp.equals(Plot2DType.HEATMAP))
      cmd.append("set lmargin at screen 0.15; ");
    if (xrange != null)
      cmd.append("set xrange [0:" + xrange + "]; ");
    if (yrange != null) {
      if (!plotTyp.equals(Plot2DType.HEATMAP)) {
        cmd.append("set yrange [0:" + yrange + "]; ");
      } else {
        // when we are drawing heatmaps, we are drawing them as flattened versions
        // of 3D plots. The yrange there is a {0,1}. The z is the one with the real data
        cmd.append("set zrange [0:" + yrange + "]; ");
      }
    }

    for (int i = 0; i < numDataSets; i++) {

      switch (plotTyp) {
        case IMPULSES:
          // Plot cmd(s): "plot dataset; plot dataset; plot dataset;"
          cmd.append(" plot \"" + dataFile + "\" index " + i);
          cmd.append(" title \"" + sanitize(setNames[i]) + "\" with impulses, ");
          // to add labels we have to pretend to plot a different dataset
          // but instead specify labels; this is because "with" cannot
          // take both impulses and labels in the same plot
          cmd.append("'' index " + i);
          cmd.append(" using 1:2:1 notitle with labels right offset -0.5,0 font ',3'; ");
          break;

        case LINES:
          // Plot cmd(s): "plot dataset; plot dataset; plot dataset;"
          cmd.append(" plot \"" + dataFile + "\" index " + i);
          cmd.append(" title \"" + sanitize(setNames[i]) + "\" with lines;");
          break;

        case OVERLAYED_LINES:
          // Plot cmd: "plot dataset, dataset, dataset;"
          // The substantial difference between this case is that it plots a
          // single plot (therefore the single "plot" compared to an additional
          // "plot" for all iterations of the loop in other cases).
          if (i == 0) cmd.append(" plot");
          cmd.append(" \"" + dataFile + "\" index " + i);
          cmd.append(" title \"" + sanitize(setNames[i]) + "\" with lines");
          cmd.append(i == numDataSets - 1 ? ";" : ",");
          break;

        case HEATMAP:
          // Plot cmd(s): "splot dataset; splot dataset; splot dataset;"
          cmd.append(" splot \"" + dataFile + "\" index " + i);
          cmd.append(" title \"" + sanitize(setNames[i]) + "\" with pm3d;");
      }
    }

    cmd.append(" unset multiplot; set output;");

    String[] plotCompare2D = new String[] { "gnuplot", "-e", cmd.toString() };

    exec(plotCompare2D);
  }

  public void plot3D(String dataFile, String outFile, String srcNcFile, Double mz) {

    // Gnuplot assumes LaTeX style for text, so when we put
    // the file name in the label it get mathified. Escape _ 
    // so that they dont get interpretted as subscript ops
    String srcNcEsc = sanitize(srcNcFile);

    StringBuffer cmd = new StringBuffer();

    cmd.append(" set terminal pdf; set output \"" + outFile + "\";");
    cmd.append(" set hidden3d; set dgrid 200,200; set xlabel \"m/z\";");
    cmd.append(" set ylabel \"time in seconds\" offset -4,-1;");
    cmd.append(" set zlabel \"intensity\" offset 2,7;");
    cmd.append(" splot \"" + dataFile + "\" u 2:1:3 with lines");
    cmd.append(" title \"" + srcNcEsc + " around mass " + mz + "\";");

    String[] plot3DSurface = new String[] { "gnuplot", "-e", cmd.toString() };

    exec(plot3DSurface);
  }

  public void plotMulti3D(String dataFile, String outFile, String fmt, String[] dataset_names, double maxz) {
    int numDataSets = dataset_names.length;

    int gridY = 1, gridX = numDataSets; // landscape layout n columns, 1 row
    // So we better adjust the size to 5 inches x #grid cells reqd
    double sizeY = 5 * gridY;
    double sizeX = 5 * gridX;
    if ("png".equals(fmt)) { // can be pdf
      // png format takes size in pixels, pdf takes it in inches
      sizeY *= 144; // 144 dpi
      sizeX *= 144; // 144 dpi
    }
    StringBuffer cmd = new StringBuffer();

    cmd.append(" set terminal " + fmt + " size " + sizeX + "," + sizeY + ";");
    cmd.append(" set output \"" + outFile + "\";");
    cmd.append(" set multiplot layout " + gridY + ", " + gridX + "; ");

    for (int i = 0; i < numDataSets; i++) {
      cmd.append(" set hidden3d; set dgrid 50,50; ");
      cmd.append(" set xlabel \"m/z\";");
      cmd.append(" unset xtics;"); // remove the xaxis labelling
      cmd.append(" set ylabel \"time in seconds\";");
      cmd.append(" set zlabel \"intensity\" offset 0,-12;");
      if (maxz != -1) cmd.append(" set zrange [0:" + maxz + "]; ");
      cmd.append(" splot \"" + dataFile + "\" index " + i + " u 2:1:3 with lines title \"" + sanitize(dataset_names[i]) + "\"; ");
    }
    cmd.append(" unset multiplot; set output;");

    String[] plot3DMulti = new String[] { "gnuplot", "-e", cmd.toString() };

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


  public Gnuplotter() { }

  public Gnuplotter(Double fontScale) {
    this.fontScale = fontScale;
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
