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

public class LCMSXMLParser extends LCMSParser {

  public static final String SPECTRUM_OBJECT_TAG = "spectrum";
  public static final String XML_PREAMBLE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";

  // Paths for invariant checking.
  public static final String SPECTRUM_PATH_EXPECTED_VERSION = "/spectrum/cvParam[@name='MS1 spectrum']";
  public static final String SPECTRUM_PATH_EXPECTED_VERSION_DIODE_ARRAY = "/spectrum/cvParam[@name='electromagnetic radiation spectrum']";
  public static final String SPECTRUM_PATH_SCAN_LIST_COUNT = "/spectrum/scanList/@count";
  public static final String SPECTRUM_PATH_BINARY_DATA_ARRAY_LIST_COUNT =
      "/spectrum/binaryDataArrayList/@count";

  // Paths for data extraction.
  public static final String SPECTRUM_PATH_INDEX = "/spectrum/@index";
  public static final String SPECTRUM_PATH_ID = "/spectrum/@id";
  public static final String SPECTRUM_PATH_BASE_PEAK_MZ = "/spectrum/cvParam[@name='base peak m/z']/@value";
  public static final String SPECTRUM_PATH_BASE_PEAK_INTENSITY = "/spectrum/cvParam[@name='base peak intensity']/@value";
  public static final String SPECTRUM_PATH_SCAN_START_TIME =
      "/spectrum/scanList/scan/cvParam[@name='scan start time']/@value";
  public static final String SPECTRUM_PATH_SCAN_START_TIME_UNIT =
      "/spectrum/scanList/scan/cvParam[@name='scan start time']/@unitName";
  public static final String SPECTRUM_PATH_MZ_BINARY_DATA =
      "/spectrum/binaryDataArrayList/binaryDataArray[./cvParam/@name='m/z array']/binary/text()";
  public static final String SPECTRUM_PATH_INTENSITY_BINARY_DATA =
      "/spectrum/binaryDataArrayList/binaryDataArray[./cvParam/@name='intensity array']/binary/text()";

  public static final Pattern SPECTRUM_EXTRACTION_REGEX =
      Pattern.compile("function=(\\d+) *process=(\\d+) scan=(\\d+)");


  // XPathFactory is known to be non-thread-safe.
  private static final ThreadLocal<XPathFactory> XPATH_FACTORY = new ThreadLocal<XPathFactory>() {
    @Override
    protected XPathFactory initialValue() {
      return XPathFactory.newInstance();
    }
  };

  public static DocumentBuilderFactory mkDocBuilderFactory() throws ParserConfigurationException {
    /* This factory must be configured within the context of a method call for exception handling.
     * TODO: can we work around this w/ dependency injection? */
    // from http://stackoverflow.com/questions/155101/make-documentbuilder-parse-ignore-dtd-references
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    docFactory.setValidating(false);
    docFactory.setNamespaceAware(true);
    docFactory.setFeature("http://xml.org/sax/features/namespaces", false);
    docFactory.setFeature("http://xml.org/sax/features/validation", false);
    docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
    docFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    return docFactory;
  }

  private static List<Double> base64ToDoubleList(String b64) {
    byte[] decodedBytes = Base64.getDecoder().decode(b64);
    ByteBuffer buf = ByteBuffer.wrap(decodedBytes).order(ByteOrder.LITTLE_ENDIAN);
    List<Double> values = new ArrayList<>(decodedBytes.length / 8);
    while (buf.hasRemaining()) {
      values.add(buf.getDouble());
    }
    return values;
  }

  // TODO: isn't there some library method for this?  The Interwebs seem to say there isn't...
  private static <K, V> List<Pair<K, V>> zipLists(List<K> keys, List<V> vals) {
    if (keys.size() != vals.size()) {
      throw new RuntimeException(String.format("Mismatched list sizes: %d vs %d", keys.size(), vals.size()));
    }
    List<Pair<K, V>> res = new ArrayList<>(keys.size());
    Iterator<K> ki = keys.listIterator();
    Iterator<V> vi = vals.listIterator();
    while (ki.hasNext() && vi.hasNext()) { // Length check should ensure these are exhausted simultaneously.
      K k = ki.next();
      V v = vi.next();
      res.add(Pair.of(k, v));
    }
    return res;
  }

  public LCMSXMLParser() {
  }

  private LCMSSpectrum handleSpectrumEntry(Document doc) throws Exception {
    XPath xpath = XPATH_FACTORY.get().newXPath();

    Double spectrumIndexD = (Double)xpath.evaluate(SPECTRUM_PATH_INDEX, doc, XPathConstants.NUMBER);
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

    String spectrumId = (String)xpath.evaluate(SPECTRUM_PATH_ID, doc, XPathConstants.STRING);
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
        ((Double)xpath.evaluate(SPECTRUM_PATH_BINARY_DATA_ARRAY_LIST_COUNT, doc, XPathConstants.NUMBER)).intValue();
    if (!Integer.valueOf(2).equals(binaryDataCount)) {
      System.err.format("WARNING: unexpected number of binary data entries in spectrum document %d: %d",
          spectrumIndex, binaryDataCount);
      return null;
    }

    Double basePeakMz = (Double)xpath.evaluate(SPECTRUM_PATH_BASE_PEAK_MZ, doc, XPathConstants.NUMBER);
    if (basePeakMz == null) {
      System.err.format("WARNING: no base peak m/z found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    Double basePeakIntensity = (Double)xpath.evaluate(SPECTRUM_PATH_BASE_PEAK_INTENSITY, doc, XPathConstants.NUMBER);
    if (basePeakIntensity == null) {
      System.err.format("WARNING: no base peak intensity found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    Double scanStartTime = (Double)xpath.evaluate(SPECTRUM_PATH_SCAN_START_TIME, doc, XPathConstants.NUMBER);
    if (scanStartTime == null) {
      System.err.format("WARNING: no scan start time found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    String scanStartTimeUnit = (String)xpath.evaluate(SPECTRUM_PATH_SCAN_START_TIME_UNIT, doc, XPathConstants.STRING);
    if (scanStartTimeUnit == null) {
      System.err.format("WARNING: no scan start time unit found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    String mzData = (String)xpath.evaluate(SPECTRUM_PATH_MZ_BINARY_DATA, doc, XPathConstants.STRING);
    if (mzData == null){
      System.err.format("WARNING: no m/z data found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    String intensityData = (String)xpath.evaluate(SPECTRUM_PATH_INTENSITY_BINARY_DATA, doc, XPathConstants.STRING);
    if (intensityData == null){
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
  public Iterator<LCMSSpectrum> getIterator(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {
    DocumentBuilderFactory docFactory = mkDocBuilderFactory();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
    final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

    return new Iterator<LCMSSpectrum>() {
      boolean inEntry = false;

      XMLEventReader xr = xmlInputFactory.createXMLEventReader(new FileInputStream(inputFile), "utf-8");
      // TODO: is the use of the XML version/encoding tag definitely necessary?
      StringWriter w = new StringWriter().append(XML_PREAMBLE).append("\n");
      XMLEventWriter xw = xmlOutputFactory.createXMLEventWriter(w);


      LCMSSpectrum next = null;

      /* Because we're handling the XML as a stream, we can only determine whether we have another Spectrum to return
       * by attempting to parse the next one.  `this.next()` reads
       */
      private LCMSSpectrum getNextSpectrum() {
        LCMSSpectrum spectrum = null;
        if (xr == null || !xr.hasNext()) {
          return null;
        }

        try {
          while (xr.hasNext()) {
            XMLEvent e = xr.nextEvent();
            if (!inEntry && e.isStartElement() &&
                e.asStartElement().getName().getLocalPart().equals((SPECTRUM_OBJECT_TAG))) {
              xw.add(e);
              inEntry = true;
            } else if (e.isEndElement() && e.asEndElement().getName().getLocalPart().equals(SPECTRUM_OBJECT_TAG)) {
              xw.add(e);
              xw.flush();
              /* TODO: the XMLOutputFactory docs don't make it clear if/how events can be written directly into a new
               * document structure, so we incur the cost of extracting each spectrum entry, serializing it, and
               * re-reading it into its own document so it can be handled by XPath.  Master this strange corner of the
               * Java ecosystem and get rid of <></>his doc -> string -> doc conversion. */
              Document doc = docBuilder.parse(new ReaderInputStream(new StringReader(w.toString())));
              spectrum = handleSpectrumEntry(doc);
              xw.close();
              /* Note: this can also be accomplished with `w.getBuffer().setLength(0);`, but using a new event writer
               * seems safer. */
              w = new StringWriter();
              w.append(XML_PREAMBLE).append("\n");
              xw = xmlOutputFactory.createXMLEventWriter(w);
              inEntry = false;
              break;
            } else if (inEntry) {
              // Add this element if we're in an entry
              xw.add(e);
            }
          }

          // We've reached the end of the document; close the reader to show that we're done.
          if (!xr.hasNext()) {
            xr.close();
            xr = null;
          }
        } catch (Exception e) {
          // TODO: do better.  We seem to run into this sort of thing with Iterators a lot...
          throw new RuntimeException(e);
        }

        return spectrum;
      }

      private LCMSSpectrum tryParseNext() {
        // Fail the attempt if the reader is closed.
        if (xr == null || !xr.hasNext()) {
          return null;
        }

        // No checks on whether we already have a spectrum stored: we expect the callers to do that.
        return getNextSpectrum();
      }

      @Override
      public boolean hasNext() {
        // Prime the pump if the iterator doesn't have a value stored yet.
        if (this.next == null) {
          this.next = tryParseNext();
        }

        // If we have an entry waiting, return true; otherwise read the next entry and return true if successful.
        return this.next != null;
      }

      @Override
      public LCMSSpectrum next() {
        // Prime the pump like we do in hasNext().
        if (this.next == null) {
          this.next = tryParseNext();
        }

        // Take available spectrum and return it.
        LCMSSpectrum res = this.next;
        /* Advance to the next element immediately, making next() do the heavy lifting most of the time.  Otherwise,
         * the parsing will resume on hasNext(), which seems like it ought to be a light-weight operation. */
        this.next = tryParseNext();

        return res;
      }

    };
  }
}
