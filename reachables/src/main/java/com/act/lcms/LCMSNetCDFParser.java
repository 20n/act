package com.act.lcms;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parses NetCDF files produced by an LCMS apparatus, converting the time points contained therein into
 * {@link com.act.lcms.LCMSSpectrum} objects.
 *
 * <a href="http://www.unidata.ucar.edu/software/netcdf/">NetCDF</a> is a generic data format for storing array-oriented
 * data.  The Waters LCMS apparatus produces NetCDF files that are structured as follows:
 * <ul>
 *   <li>
 *     The mass/charge and intensity values are stored as two long parallel arrays of values.  The mass/charges
 *     from each scan are concatenated together to form an enormous 1-d array; the same is done for intensities.
 *   </li>
 *   <li>
 *     Several other parallel arrays are available in the file that represent attributes of each time-point (scan).
 *     These include the scan acquisition time, the number of mass/charge points acquired in the scan, and the offset
 *     of those points in the concatenated mass/charge and intensity arrays.
 *   </li>
 *   <li>
 *     To extract the set of {mass/charge, intensity} pairs for a given scan <i>i</i>, we grab the exclusive range
 *     mass_values[scan_index[i]:scan_index[i]+point_count[i]] and zip it with the corresponding intensity values.
 *   </li>
 * </ul>
 *
 * The NetCDF API exposed in the ucar.ma2 package makes it easy to read and access these point arrays.  Note, however,
 * that the time/space performance characteristics of this library have not been thoroughly tested at 20n, so beware of
 * excessive GC overhead or heap consumption.
 */
public class LCMSNetCDFParser implements LCMSParser {
  public static final String MASS_VALUES = "mass_values";
  public static final String INTENSITY_VALUES = "intensity_values";
  public static final String SCAN_TIME = "scan_acquisition_time";
  public static final String SCAN_POINTS_START = "scan_index";
  public static final String SCAN_POINTS_COUNT = "point_count";
  public static final String TOTAL_INTENSITY = "total_intensity";

  @Override
  public Iterator<LCMSSpectrum> getIterator(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {

    final NetcdfFile netcdfFile = NetcdfFile.open(inputFile);

    // Assumption: all referenced Variables will always exist in the NetcdfFfile.

    // Assumption: these arrays will have the same length.
    final Array mzValues = netcdfFile.findVariable(MASS_VALUES).read();
    final Array intensityValues = netcdfFile.findVariable(INTENSITY_VALUES).read();
    assert(mzValues.getSize() == intensityValues.getSize());
    // Assumption: the mz/intensity values are always floats.
    assert(mzValues.getDataType() == DataType.FLOAT &&
        intensityValues.getDataType() == DataType.FLOAT);

    // Assumption: all of these variables' arrays will have the same lengths.
    final Array scanTimeArray = netcdfFile.findVariable(SCAN_TIME).read();
    final Array scanPointsStartArray = netcdfFile.findVariable(SCAN_POINTS_START).read();
    final Array scanPointsCountArray = netcdfFile.findVariable(SCAN_POINTS_COUNT).read();
    final Array totalIntensityArray = netcdfFile.findVariable(TOTAL_INTENSITY).read();
    assert(scanTimeArray.getSize() == scanPointsStartArray.getSize() &&
        scanPointsStartArray.getSize() == scanPointsCountArray.getSize() &&
        scanPointsCountArray.getSize() == totalIntensityArray.getSize());
    // Assumption: the following four columns always have these types.
    assert(scanTimeArray.getDataType() == DataType.DOUBLE &&
        scanPointsStartArray.getDataType() == DataType.INT &&
        scanPointsCountArray.getDataType() == DataType.INT &&
        totalIntensityArray.getDataType() == DataType.DOUBLE);

    final long size = scanTimeArray.getSize();

    System.err.format("Total size of scantimearray: %d\n", size);

    return new Iterator<LCMSSpectrum>() {
      private int i = 0;
      @Override
      public boolean hasNext() {
        return this.i < size;
      }

      @Override
      public LCMSSpectrum next() {
        int pointCount = scanPointsCountArray.getInt(i);
        List<Pair<Double, Double>> mzIntPairs = new ArrayList<>(pointCount);

        int pointsStart = scanPointsStartArray.getInt(i);
        int pointsEnd = pointsStart + pointCount;
        for (int p = pointsStart; p < pointsEnd; p++) {
          Double mz = Float.valueOf(mzValues.getFloat(p)).doubleValue();
          Double intensity = Float.valueOf(intensityValues.getFloat(p)).doubleValue();
          mzIntPairs.add(Pair.of(mz, intensity));
        }

        LCMSSpectrum s = new LCMSSpectrum(i, scanTimeArray.getDouble(i), "s", mzIntPairs,
            null, null, null, i, totalIntensityArray.getDouble(i));

        // Don't forget to advance the counter!
        this.i++;

        // Close the file if we're done with all the array contents.
        if (i >= size) {
          try {
            netcdfFile.close();
          } catch (IOException e) {
            throw new RuntimeException(e); // TODO: can we do better?
          }
        }

        return s;
      }
    };
  }

  /**
   * Print information about a specific Variable from a NetCDF file.  Used for debugging.
   * @param name A human-readable name for this variable.
   * @param v The variable whose details to print.
   * @throws IOException
   */
  public static void printVariableDetails(String name, Variable v) throws IOException {
    System.out.format("%s name and dimensions: %s\n", name, v.getNameAndDimensions());
    Array a = v.read();
    System.out.format("  rank: %d\n", a.getRank());
    System.out.format("  shape size: %d\n", a.getShape().length);
    System.out.format("  shape: %s\n", a.shapeToString());

    System.out.format("  array data type: %s\n", a.getDataType());
  }

  /**
   * Print top-level details about a NetCDF file.  Used for debugging.
   * @param netcdfFile The NetCDF file whose details to print.
   */
  public static void printNetcdfFileDetails(NetcdfFile netcdfFile) {
    System.out.format("Details: %s\n", netcdfFile.getDetailInfo());
    System.out.format("File type description: %s\n", netcdfFile.getFileTypeDescription());
    System.out.format("Title: %s\n", netcdfFile.getTitle());
    System.out.println("Variables:");
    for (Variable v : netcdfFile.getVariables()) {
      System.out.format("  %s\n", v.getNameAndDimensions());
    }
    System.out.println("Global attributes:");
    for (Attribute a : netcdfFile.getGlobalAttributes()) {
      System.out.format("  %s: %s (%s)\n", a.getFullName(), a.getStringValue(), a.getDataType().toString());
    }
  }

  @Override
  public List<LCMSSpectrum> parse(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {
    List<LCMSSpectrum> spectra = new ArrayList<>();
    Iterator<LCMSSpectrum> iter = this.getIterator(inputFile);
    while (iter.hasNext()) {
      spectra.add(iter.next());
    }

    return spectra;
  }

  public static void main(String[] args) throws Exception {
    NetcdfFile netcdfFile = NetcdfFile.open(args[0]);
    printNetcdfFileDetails(netcdfFile);
    netcdfFile.close();

    List<Triple<Double, Double, Integer>> rangesWIdx = new ArrayList<Triple<Double, Double, Integer>>(){{
      int i = 0;
      for (double center = 50.0; center <= 950.0; center += 0.01, i++) {
        add(Triple.of(center - 0.01, center + 0.01, i));
      }
    }};

    List<List<Double>> allTraces = new ArrayList<List<Double>>(rangesWIdx.size()) {{
      for (int i = 0; i < rangesWIdx.size(); i++) {
        add(new ArrayList<>());
      }
    }};
    List<Double> times = new ArrayList<>();

    LCMSNetCDFParser parser = new LCMSNetCDFParser();
    Iterator<LCMSSpectrum> iter = parser.getIterator(args[0]);
    while (iter.hasNext()) {
      LCMSSpectrum spectrum = iter.next();
      Double time = spectrum.getTimeVal();
      // Store one list of the time values so we can knit times and intensity sums later to form XZs.
      times.add(time);

      // Make sure we have a time entry for this timepoint.  The one we're working on is always the last in the list.
      for (List<Double> trace : allTraces) {
        trace.add(0.0d);
      }

      // TODO: use a logger, but not in this class.
      System.out.format("Processing timepoint %f\n", time);

      LinkedList<Triple<Double, Double, Integer>> workingQueue = new LinkedList<>();
      // TODO: can we reuse these instead of creating fresh?
      LinkedList<Triple<Double, Double, Integer>> tbdQueue = new LinkedList<>(rangesWIdx);


      for (Pair<Double, Double> mzIntensity : spectrum.getIntensities()) {
        Double mz = mzIntensity.getLeft();
        Double intensity = mzIntensity.getRight();

        //System.out.format("  mz:        %.3f\n", mz);
        //System.out.format("  pre  working: %s\n", StringUtils.join(workingQueue.stream().map(t -> String.format("%d=%.3f-%.3f", t.getRight(), t.getLeft(), t.getMiddle())).collect(Collectors.toList()), ", "));
        //System.out.format("  pre  tbd:     %s, ...\n", StringUtils.join(tbdQueue.stream().limit(3).map(t -> String.format("%d=%.3f-%.3f", t.getRight(), t.getLeft(), t.getMiddle())).collect(Collectors.toList()), ", "));

        // First, shift any applicable ranges onto the working queue based on their minimum mz.
        while (!tbdQueue.isEmpty() && tbdQueue.peekFirst().getLeft() <= mz) {
          workingQueue.add(tbdQueue.pop());
        }

        // Next, remove any ranges we've passed.
        while (!workingQueue.isEmpty() && workingQueue.peekFirst().getMiddle() < mz) {
          workingQueue.pop();
        }

        //System.out.format("  post working: %s\n", StringUtils.join(workingQueue.stream().map(t -> String.format("%d=%.3f-%.3f", t.getRight(), t.getLeft(), t.getMiddle())).collect(Collectors.toList()), ", "));
        //System.out.format("  post tbd:     %s, ...\n", StringUtils.join(tbdQueue.stream().limit(3).map(t -> String.format("%d=%.3f-%.3f", t.getRight(), t.getLeft(), t.getMiddle())).collect(Collectors.toList()), ", "));

        if (workingQueue.isEmpty()) {
          if (tbdQueue.isEmpty()) {
            // If both queues are empty, there are no more windows to consider at all.  One to the next timepoint!
            break;
          }

          // If there's nothing that happens to fit in this range, skip it!
          continue;
        }

        // The working queue should now hold only ranges that include this m/z value.  Sweep line swept!

        // Now add this intensity to the most recent XZ value for each of the items in the working queue (max 2?).
        for (Triple<Double, Double, Integer> triple : workingQueue) {
          List<Double> trace = allTraces.get(triple.getRight());
          Double accumulator = trace.get(trace.size() - 1); // Get the current XZ, i.e. the one at this timepoint.
          trace.set(trace.size() - 1, accumulator + intensity);
        }
      }
    }

    int i = 0;
    for (List<Double> trace : allTraces) {
      System.out.format("Trace %d length: %d\n", i, trace.size());
      i++;
    }
  }
}
