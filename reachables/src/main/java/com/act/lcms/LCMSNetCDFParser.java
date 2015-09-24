package com.act.lcms;

import org.apache.commons.lang3.tuple.Pair;
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
import java.util.List;


public class LCMSNetCDFParser extends LCMSParser {
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

  public static void printVariableDetails(String name, Variable v) throws IOException {
    System.out.format("%s name and dimensions: %s\n", name, v.getNameAndDimensions());
    Array a = v.read();
    System.out.format("  rank: %d\n", a.getRank());
    System.out.format("  shape size: %d\n", a.getShape().length);
    System.out.format("  shape: %s\n", a.shapeToString());

    System.out.format("  array data type: %s\n", a.getDataType());
  }

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

}
