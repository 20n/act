package com.act.lcms;

import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.lang.StringBuffer;

public class Gnuplotter {

  public static final String DRAW_SEPARATOR = "(separator)";

  private Double fontScale = null;

  public static class PlotConfiguration {
    public static final String DRAW_SEPARATOR = "(separator)";

    public enum KIND {
      HEADER,
      SEPARATOR,
      GRAPH,
    }
    private KIND kind;
    private String label;
    private Integer dataSetIndex;
    private Double yRange;

    public PlotConfiguration(KIND kind, String label, Integer dataSetIndex, Double yRange) {
      this.kind = kind;
      this.label = label;
      this.dataSetIndex = dataSetIndex;
      this.yRange = yRange;
    }

    public KIND getKind() {
      return kind;
    }

    public void setKind(KIND kind) {
      this.kind = kind;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public Integer getDataSetIndex() {
      return dataSetIndex;
    }

    public void setDataSetIndex(Integer dataSetIndex) {
      this.dataSetIndex = dataSetIndex;
    }

    public Double getYRange() {
      return yRange;
    }

    public void setyRange(Double yRange) {
      this.yRange = yRange;
    }

    protected static PlotConfiguration fromOldParameters(String name, Integer dataSetIndex, Double yMax) {
      if (DRAW_SEPARATOR.equals(name)) {
        return new PlotConfiguration(KIND.SEPARATOR, "", null, null);
      }
      // Headers aren't available via this method.
      return new PlotConfiguration(KIND.GRAPH, name, dataSetIndex, yMax);
    }
  }

  private List<PlotConfiguration> oldParamsToPlotConfigs(String[] setNames, Double yrange, Double[] yMaxes) {
    if (yMaxes != null && setNames.length != yMaxes.length) {
      throw new RuntimeException(String.format("Number of data sets (%d) must match number of yRanges (%d)",
          setNames.length, yMaxes.length));
    }

    List<PlotConfiguration> configurations = new ArrayList<>(setNames.length);
    int dataSetIndex = 0;
    for (int i = 0; i < setNames.length; i++) {
      PlotConfiguration config =
          PlotConfiguration.fromOldParameters(setNames[i], dataSetIndex, yMaxes == null ? yrange : yMaxes[i]);
      if (config.getKind() == PlotConfiguration.KIND.GRAPH) {
        dataSetIndex++;
      }
      configurations.add(config);
    }
    return configurations;
  }

  private String sanitize(String fname) {
    // Gnuplot assumes LaTeX style for text, so when we put
    // the file name in the label it get mathified. Escape _ 
    // so that they dont get interpretted as subscript ops
    return fname.replace("_", "\\\\_");
  }

  private enum Plot2DType { IMPULSES, LINES, OVERLAYED_LINES, HEATMAP };

  public void plotHeatmap(String dataFile, String outFile, String[] setNames, Double yrange, String fmt) {
    List<PlotConfiguration> configurations = oldParamsToPlotConfigs(setNames, yrange, null);
    plot2DHelper(Plot2DType.HEATMAP, dataFile, outFile, null, null, null, true, configurations, fmt);
  }

  public void plotHeatmap(String dataFile, String outFile, String[] setNames, Double yrange, String fmt,
                          Double sizeX, Double sizeY, Double[] yMaxes, String outputFile) {
    List<PlotConfiguration> configurations = oldParamsToPlotConfigs(setNames, yrange, yMaxes);
    plot2DHelper(Plot2DType.HEATMAP, dataFile, outFile, null, null, null, true, fmt, sizeX, sizeY,
        configurations, outputFile);
  }

  public void plotHeatmap(String dataFile, String outFile, String fmt, Double sizeX, Double sizeY,
                          List<PlotConfiguration> configurations, String outputFile) {
    plot2DHelper(Plot2DType.HEATMAP, dataFile, outFile, null, null, null, true, fmt, sizeX, sizeY,
        configurations, outputFile);
  }


  public void plotOverlayed2D(String dataFile, String outFile, String[] setNames, String xlabel, Double yrange, 
      String ylabel, String fmt, String gnuplotFile) {
    List<PlotConfiguration> configurations = oldParamsToPlotConfigs(setNames, yrange, null);
    // plotOverlayed2D produces the same graph as plot2D, except it collapses all datasets into a single plot
    plot2DHelper(Plot2DType.OVERLAYED_LINES, dataFile, outFile, null, xlabel, ylabel, true, fmt, null, null,
        configurations, gnuplotFile);
  }

  public void plot2D(String dataFile, String outFile, String[] setNames, String xlabel, Double yrange, 
      String ylabel, String fmt) {
    List<PlotConfiguration> configurations = oldParamsToPlotConfigs(setNames, yrange, null);
    plot2DHelper(Plot2DType.LINES, dataFile, outFile, null, xlabel, ylabel, true, configurations, fmt);
  }

  public void plot2DImpulsesWithLabels(String dataFile, String outFile, String[] setNames, Double xrange, String xlabel,
      Double yrange, String ylabel, String fmt) {
    List<PlotConfiguration> configurations = oldParamsToPlotConfigs(setNames, yrange, null);
    plot2DHelper(Plot2DType.IMPULSES, dataFile, outFile, xrange, xlabel, ylabel, true, configurations, fmt);
  }

  public void plot2D(String dataFile, String outFile, String[] setNames, String xlabel, Double yrange, String ylabel,
                     String fmt, Double sizeX, Double sizeY, Double[] yMaxes, String outputFile) {
    List<PlotConfiguration> configurations = oldParamsToPlotConfigs(setNames, yrange, yMaxes);
    plot2DHelper(Plot2DType.LINES, dataFile, outFile, sizeX, xlabel, ylabel, true, fmt, sizeX, sizeY,
        configurations, outputFile);
  }

  public void plot2D(String dataFile, String outFile, String xlabel, String ylabel, String fmt,
                     Double sizeX, Double sizeY, List<PlotConfiguration> configurations, String gnuplotOutputFile) {
    plot2DHelper(Plot2DType.LINES, dataFile, outFile, sizeX, xlabel, ylabel, true, fmt, sizeX, sizeY,
        configurations, gnuplotOutputFile);
  }

  private void plot2DHelper(Plot2DType plotTyp, String dataFile, String outFile, Double xrange, String xlabel,
                            String ylabel, boolean showKey, List<PlotConfiguration> configurations, String fmt) {
    plot2DHelper(plotTyp, dataFile, outFile, xrange, xlabel, ylabel, showKey, fmt, null, null, configurations, null);
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
   * @param xlabel   x-axis label
   * @param ylabel   y-axis label
   * @param showKey  does the graph show a legend for the plotted points
   * @param fmt      "png" or "pdf" (default)
   * @param explicitSizeX An X dimension size to use (rather than one computed on the plot configurations)
   * @param explicitSizeY An Y dimension size to use (rather than one computed on the plot configurations)
   * @param plotConfigurations A list of configurations for the plots to produce
   * @param cmdFile An output file to which to write the gnuplot command produced by this function
   */
  private void plot2DHelper(Plot2DType plotTyp, String dataFile, String outFile, Double xrange, String xlabel,
                            String ylabel, boolean showKey, String fmt, Double explicitSizeX, Double explicitSizeY,
                            List<PlotConfiguration> plotConfigurations, String cmdFile) {
    int numDataSets = plotConfigurations.size();

    // layout 1 column
    int gridX = 1;
    // layout n columns, unless graphs merged together using overlayed plots
    int gridY = plotTyp.equals(Plot2DType.OVERLAYED_LINES) ? 1 : numDataSets; 

    // by default gnuplot plots pdfs to a XxY = 5x3 canvas (in inches)
    // we need about 1.5 inch for each plot on the y-axis, so if there are
    // more than 2 plots beings compared they tend to be squished.
    // So we better adjust the size to 1.5 x 5 inches x #grid cells reqd
    double sizeY = explicitSizeY != null ? explicitSizeY : (plotTyp == Plot2DType.HEATMAP ? 0.5 : 1.5) * gridY;
    double sizeX = explicitSizeX != null ? explicitSizeX : 5 * gridX;

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

    String scale = plotTyp.equals(Plot2DType.HEATMAP) ? " scale 1,1.4" : "";
    cmd.append(" set multiplot layout " + gridY + ", 1" + scale + "; ");

    if (!plotTyp.equals(Plot2DType.HEATMAP))
      cmd.append("set lmargin at screen 0.15; ");
    if (xrange != null)
      cmd.append("set xrange [0:" + xrange + "]; ");

    // Overlayed lines need to all share a single y range, so we'll take the max.
    if (plotTyp.equals(Plot2DType.OVERLAYED_LINES)) {
      Double maxY = 0.0;
      for (PlotConfiguration config : plotConfigurations) {
        maxY = Math.max(maxY, config.getYRange());
      }
      cmd.append("set yrange [0:" + maxY + "]; ");
    }

    if (plotTyp.equals(Plot2DType.HEATMAP)) {
      // Crazy heatmap config parameters, found via experimentation.
      cmd.append(" set pm3d at b;");
      cmd.append(" unset colorbox;");
      // With help from http://objectmix.com/graphics/778659-setting-textcolor-legend.html.
      cmd.append(" set key tc rgb \"green\";");
      cmd.append(" set key right;");
      cmd.append(" unset tics;");
      cmd.append(" set tmargin 0;");
      cmd.append(" set bmargin 0;");
    }

    for (PlotConfiguration config : plotConfigurations) {
      if (config.getKind() == PlotConfiguration.KIND.SEPARATOR || config.getKind() == PlotConfiguration.KIND.HEADER) {
        cmd.append(" unset tics; unset border;  plot (y = 0) ");
        if (config.getKind() == PlotConfiguration.KIND.HEADER) {
          cmd.append(" title \"" + config.getLabel() + "\";");
        } else {
          cmd.append(" notitle;");
        }
        cmd.append(" set border; ");
        if (!plotTyp.equals(Plot2DType.HEATMAP)) {
          cmd.append(" set tics;");
        }
        continue;
      }

      // If per-graph maxes are defined, set the y or z range before calling `plot`.
      if (!plotTyp.equals(Plot2DType.OVERLAYED_LINES)) {
        if (plotTyp.equals(Plot2DType.HEATMAP)) {
          cmd.append(" set cbrange [0:" + config.getYRange() + "]; ");
        } else {
          // when we are drawing heatmaps, we are drawing them as flattened versions
          // of 3D plots. The yrange there is a {0,1}. The z is the one with the real data
          cmd.append(" set yrange [0:" + config.getYRange() + "]; ");
        }
      }

      switch (plotTyp) {
        case IMPULSES:
          // Plot cmd(s): "plot dataset; plot dataset; plot dataset;"
          cmd.append(" plot \"" + dataFile + "\" index " + config.getDataSetIndex());
          cmd.append(" title \"" + sanitize(config.getLabel()) + "\" with impulses, ");
          // to add labels we have to pretend to plot a different dataset
          // but instead specify labels; this is because "with" cannot
          // take both impulses and labels in the same plot
          cmd.append("'' index " + config.getDataSetIndex());
          cmd.append(" using 1:2:1 notitle with labels right offset -0.5,0 font ',3'; ");
          break;

        case LINES:
          // Plot cmd(s): "plot dataset; plot dataset; plot dataset;"
          cmd.append(" plot \"" + dataFile + "\" index " + config.getDataSetIndex());
          cmd.append(" title \"" + sanitize(config.getLabel()) + "\" with lines;");
          break;

        case OVERLAYED_LINES:
          // Plot cmd: "plot dataset, dataset, dataset;"
          // The substantial difference between this case is that it plots a
          // single plot (therefore the single "plot" compared to an additional
          // "plot" for all iterations of the loop in other cases).
          if (config.getDataSetIndex() == 0) cmd.append(" plot");
          cmd.append(" \"" + dataFile + "\" index " + config.getDataSetIndex());
          cmd.append(" title \"" + sanitize(config.getLabel()) + "\" with lines");
          cmd.append(config.getDataSetIndex() == numDataSets - 1 ? ";" : ",");
          break;

        case HEATMAP:
          // Plot cmd(s): "splot dataset; splot dataset; splot dataset;"
          cmd.append(" splot \"" + dataFile + "\" index " + config.getDataSetIndex());
          cmd.append(" title \"" + sanitize(config.getLabel()) + "\" with pm3d;");
      }
    }

    cmd.append(" unset multiplot; set output;");

    if (cmdFile != null) {
      try (FileWriter fw = new FileWriter(new File(cmdFile))) {
        fw.append(cmd.toString());
        fw.append("\n");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

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
