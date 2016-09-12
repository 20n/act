package com.act.lcms;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;

/**
 * Parses mzXML lcms, converting the time points contained therein into {@link com.act.lcms.LCMSSpectrum} objects.
 *
 * mzXML has a few quirks that the user ought to be aware of:
 * <ul>
 *   <li>
 *     Each mzXML file may contain data for several kinds of scans, differentiated by their "function" value.  For
 *     the Waters instrument used by ECL, there may be three different scan functions; we currently are only interested
 *     in function 2.
 *   </li>
 *   <li>
 *     The mass/charge and intensity data for a given spectrum (time point) are stored as base64-encoded lists of
 *     little-endian IEEE 754 floating point numbers.  These lists are unpacked and zipped together by this parser.
 *   </li>
 *   <li>
 *     Each spectrum has a "base peak m/z" and "base peak intensity" value specified, which is the
 *     mass/charge with the highest intensity value (and that intensity value) at the current time point.  This
 *     {mass/charge, intensity} pair does <b>not</b> reappear in the spectrum data: it seems to be plucked out of the
 *     spectrum data.
 *   </li>
 * </ul>
 *
 * Note that the {@link #parse(String)} function for this class is a memory hog.  Use {@link #getIterator(String)}
 * wherever possible instead.
 */
public class LCMSmzMLParser extends MzMLParser<LCMSSpectrum> implements LCMSParser {

  // Paths for invariant checking.
  public static final String SPECTRUM_PATH_EXPECTED_VERSION = "/spectrum/cvParam[@name='MS1 spectrum']";
  public static final String SPECTRUM_PATH_EXPECTED_VERSION_DIODE_ARRAY = "/spectrum/cvParam[@name='electromagnetic radiation spectrum']";
  public static final String SPECTRUM_PATH_SCAN_LIST_COUNT = "/spectrum/scanList/@count";
  public static final String SPECTRUM_PATH_BINARY_DATA_ARRAY_LIST_COUNT =
      "/spectrum/binaryDataArrayList/@count";

  public LCMSmzMLParser() {
    super();
  }

  protected LCMSSpectrum handleSpectrumEntry(Document doc) throws XPathException {
    XPath xpath = getXPathFactory().newXPath();

    Double spectrumIndexD = (Double) xpath.evaluate(SPECTRUM_PATH_INDEX, doc, XPathConstants.NUMBER);
    if (spectrumIndexD == null) {
      System.err.format("WARNING: found spectrum document without index attribute.\n");
      return null;
    }
    Integer spectrumIndex = spectrumIndexD.intValue();

    if (xpath.evaluate(SPECTRUM_PATH_EXPECTED_VERSION, doc, XPathConstants.NODE) == null) {
      // if it is not MS1 Spectrum data then we will skip from the output.

      // check if it entry we see here is the diode array data, those we expect to silently skip
      // if on the other hand, even that is not matched; we truly have some unexpected entries, so report to user
      if (xpath.evaluate(SPECTRUM_PATH_EXPECTED_VERSION_DIODE_ARRAY, doc, XPathConstants.NODE) == null) {
        System.err.format("WARNING: found unexpected MS spectrum version in spectrum document %d.  Skipping.\n",
            spectrumIndex);
      }

      return null;
    }

    String spectrumId = (String) xpath.evaluate(SPECTRUM_PATH_ID, doc, XPathConstants.STRING);
    if (spectrumId == null) {
      System.err.format("WARNING: no spectrum id found for documnt %d\n", spectrumIndex);
      return null;
    }

    Matcher matcher = SPECTRUM_EXTRACTION_REGEX.matcher(spectrumId);
    if (!matcher.find()) {
      System.err.format("WARNING: spectrum id for documnt %d did not match regex: %s\n", spectrumIndex, spectrumId);
      return null;
    }
    Integer spectrumFunction = Integer.parseInt(matcher.group(1));
    Integer spectrumScan = Integer.parseInt(matcher.group(3));

    Integer scanListCount =
        ((Double) xpath.evaluate(SPECTRUM_PATH_SCAN_LIST_COUNT, doc, XPathConstants.NUMBER)).intValue();
    if (!Integer.valueOf(1).equals(scanListCount)) {
      System.err.format("WARNING: unexpected number of scan entries in spectrum document %d: %d",
          spectrumIndex, scanListCount);
      return null;
    }

    Integer binaryDataCount =
        ((Double) xpath.evaluate(SPECTRUM_PATH_BINARY_DATA_ARRAY_LIST_COUNT, doc, XPathConstants.NUMBER)).intValue();
    if (!Integer.valueOf(2).equals(binaryDataCount)) {
      System.err.format("WARNING: unexpected number of binary data entries in spectrum document %d: %d",
          spectrumIndex, binaryDataCount);
      return null;
    }

    Double basePeakMz = (Double) xpath.evaluate(SPECTRUM_PATH_BASE_PEAK_MZ, doc, XPathConstants.NUMBER);
    if (basePeakMz == null) {
      System.err.format("WARNING: no base peak m/z found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    Double basePeakIntensity = (Double) xpath.evaluate(SPECTRUM_PATH_BASE_PEAK_INTENSITY, doc, XPathConstants.NUMBER);
    if (basePeakIntensity == null) {
      System.err.format("WARNING: no base peak intensity found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    Double scanStartTime = (Double) xpath.evaluate(SPECTRUM_PATH_SCAN_START_TIME, doc, XPathConstants.NUMBER);
    if (scanStartTime == null) {
      System.err.format("WARNING: no scan start time found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    String scanStartTimeUnit = (String) xpath.evaluate(SPECTRUM_PATH_SCAN_START_TIME_UNIT, doc, XPathConstants.STRING);
    if (scanStartTimeUnit == null) {
      System.err.format("WARNING: no scan start time unit found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    String mzData = (String) xpath.evaluate(SPECTRUM_PATH_MZ_BINARY_DATA, doc, XPathConstants.STRING);
    if (mzData == null) {
      System.err.format("WARNING: no m/z data found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    String intensityData = (String) xpath.evaluate(SPECTRUM_PATH_INTENSITY_BINARY_DATA, doc, XPathConstants.STRING);
    if (intensityData == null) {
      System.err.format("WARNING: no intensity data found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    List<Double> mzs = base64ToDoubleList(mzData);
    List<Double> intensities = base64ToDoubleList(intensityData);
    List<Pair<Double, Double>> mzIntensityPairs = zipLists(mzs, intensities);

    return new LCMSSpectrum(spectrumIndex, scanStartTime, scanStartTimeUnit,
        mzIntensityPairs, basePeakMz, basePeakIntensity, spectrumFunction, spectrumScan, null);
  }

  @Override
  public List<LCMSSpectrum> parse(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {
    return super.parse(inputFile);
  }

  @Override
  public Iterator<LCMSSpectrum> getIterator(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {
    return super.getIterator(inputFile);
  }
}
