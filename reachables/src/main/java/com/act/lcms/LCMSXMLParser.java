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
import javax.xml.stream.events.XMLEvent;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.Serializable;
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
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.EOFException;

public class LCMSXMLParser {

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

  private void handleSpectrumEntry(Document doc) throws Exception {
    XPath xpath = XPATH_FACTORY.get().newXPath();

    Double spectrumIndexD = (Double)xpath.evaluate(SPECTRUM_PATH_INDEX, doc, XPathConstants.NUMBER);
    if (spectrumIndexD == null) {
      System.err.format("WARNING: found spectrum document without index attribute.\n");
      return;
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

      return;
    }

    String spectrumId = (String)xpath.evaluate(SPECTRUM_PATH_ID, doc, XPathConstants.STRING);
    if (spectrumId == null) {
      System.err.format("WARNING: no spectrum id found for documnt %d\n", spectrumIndex);
      return;
    }

    Matcher matcher = SPECTRUM_EXTRACTION_REGEX.matcher(spectrumId);
    if (!matcher.find()) {
      System.err.format("WARNING: spectrum id for documnt %d did not match regex: %s\n", spectrumIndex, spectrumId);
      return;
    }
    Integer spectrumFunction = Integer.parseInt(matcher.group(1));
    Integer spectrumScan = Integer.parseInt(matcher.group(3));

    Integer scanListCount =
        ((Double) xpath.evaluate(SPECTRUM_PATH_SCAN_LIST_COUNT, doc, XPathConstants.NUMBER)).intValue();
    if (!Integer.valueOf(1).equals(scanListCount)) {
      System.err.format("WARNING: unexpected number of scan entries in spectrum document %d: %d",
          spectrumIndex, scanListCount);
      return;
    }

    Integer binaryDataCount =
        ((Double)xpath.evaluate(SPECTRUM_PATH_BINARY_DATA_ARRAY_LIST_COUNT, doc, XPathConstants.NUMBER)).intValue();
    if (!Integer.valueOf(2).equals(binaryDataCount)) {
      System.err.format("WARNING: unexpected number of binary data entries in spectrum document %d: %d",
          spectrumIndex, binaryDataCount);
      return;
    }

    Double basePeakMz = (Double)xpath.evaluate(SPECTRUM_PATH_BASE_PEAK_MZ, doc, XPathConstants.NUMBER);
    if (basePeakMz == null) {
      System.err.format("WARNING: no base peak m/z found for spectrum document %d\n", spectrumIndex);
      return;
    }

    Double basePeakIntensity = (Double)xpath.evaluate(SPECTRUM_PATH_BASE_PEAK_INTENSITY, doc, XPathConstants.NUMBER);
    if (basePeakIntensity == null) {
      System.err.format("WARNING: no base peak intensity found for spectrum document %d\n", spectrumIndex);
      return;
    }

    Double scanStartTime = (Double)xpath.evaluate(SPECTRUM_PATH_SCAN_START_TIME, doc, XPathConstants.NUMBER);
    if (scanStartTime == null) {
      System.err.format("WARNING: no scan start time found for spectrum document %d\n", spectrumIndex);
      return;
    }

    String scanStartTimeUnit = (String)xpath.evaluate(SPECTRUM_PATH_SCAN_START_TIME_UNIT, doc, XPathConstants.STRING);
    if (scanStartTimeUnit == null) {
      System.err.format("WARNING: no scan start time unit found for spectrum document %d\n", spectrumIndex);
      return;
    }

    String mzData = (String)xpath.evaluate(SPECTRUM_PATH_MZ_BINARY_DATA, doc, XPathConstants.STRING);
    if (mzData == null){
      System.err.format("WARNING: no m/z data found for spectrum document %d\n", spectrumIndex);
      return;
    }

    String intensityData = (String)xpath.evaluate(SPECTRUM_PATH_INTENSITY_BINARY_DATA, doc, XPathConstants.STRING);
    if (intensityData == null){
      System.err.format("WARNING: no intensity data found for spectrum document %d\n", spectrumIndex);
      return;
    }

    List<Double> mzs = base64ToDoubleList(mzData);
    List<Double> intensities = base64ToDoubleList(intensityData);
    List<Pair<Double, Double>> mzIntensityPairs = zipLists(mzs, intensities);

    LCMSSpectrum spectrum = new LCMSSpectrum(spectrumIndex, scanStartTime, scanStartTimeUnit,
        mzIntensityPairs, basePeakMz, basePeakIntensity, spectrumFunction, spectrumScan);
    this.parsedSpectra.add(spectrum);
  }

  private List<LCMSSpectrum> parsedSpectra = new ArrayList<>();

  public List<LCMSSpectrum> getParsedSpectra() {
    return this.parsedSpectra;
  }

  public List<LCMSSpectrum> parse(String inputFile) throws Exception {
    DocumentBuilderFactory docFactory = mkDocBuilderFactory();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

    XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
    XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
    StringWriter w = new StringWriter();
    w.append(XML_PREAMBLE).append("\n"); // TODO: is the use of the XML version/encoding tag definitely necessary?
    XMLEventWriter xw = xmlOutputFactory.createXMLEventWriter(w);

    XMLEventReader xr = null;
    try ( FileInputStream fis = new FileInputStream(inputFile) ) {
      xr = xmlInputFactory.createXMLEventReader(fis, "utf-8");
      boolean inEntry = false;
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
           * document structure, so we incur the cost of extracting each spectrum entry, serializing it, and re-reading
           * it into its own document so it can be handled by XPath.  Master this strange corner of the Java ecosystem
           * and get rid of this doc -> string -> doc conversion. */
          Document doc = docBuilder.parse(new ReaderInputStream(new StringReader(w.toString())));

          // process the doc and add it to this.parsedSpectra
          handleSpectrumEntry(doc);

          xw.close();
          /* Note: this can also be accomplished with `w.getBuffer().setLength(0);`, but using a new event writer
           * seems safer. */
          w = new StringWriter();
          w.append(XML_PREAMBLE).append("\n");
          xw = xmlOutputFactory.createXMLEventWriter(w);
          inEntry = false;
        } else if (inEntry) {
          // Add this element if we're in an entry
          xw.add(e);
        }
      }
    } finally {
      if (xr != null) {
        xr.close();
      }
    }

    return this.parsedSpectra;
  }

  public void serialize(String toFile) throws IOException {
    try {
      OutputStream file = new FileOutputStream(toFile);
      OutputStream buffer = new BufferedOutputStream(file);
      ObjectOutput output = new ObjectOutputStream(buffer);
      try {
        for (LCMSSpectrum spectraObj : this.parsedSpectra)
          output.writeObject(spectraObj);
      } finally {
        output.close();
      }
    } catch(IOException ex) {
      throw ex;
    }
  }

  public List<LCMSSpectrum> deserialize(String fromFile) throws Exception {
    try {
      InputStream file = new FileInputStream(fromFile);
      InputStream buffer = new BufferedInputStream(file);
      ObjectInput input = new ObjectInputStream(buffer);
      LCMSSpectrum s = null;
      while (true) {
        try {
          // readObject does not return null on EOF instead throws EOFException. The recommended way to
          // terminate the loop is the catch the EOF, close the stream and break.
          // http://stackoverflow.com/questions/2626163/java-fileinputstream-objectinputstream-reaches-end-of-file-eof
          s = (LCMSSpectrum)input.readObject();
          this.parsedSpectra.add(s);
        } catch (EOFException exc) {
          input.close();
          break;
        }
      }
    } catch(Exception ex) {
      throw ex;
    }

    return this.parsedSpectra;
  }

  public static class LCMSSpectrum implements Serializable {
    private static final long serialVersionUID = -1329555801774532939L;

    private Integer index;
    private Double timeVal;
    private String timeUnit;
    private List<Pair<Double, Double>> intensities;
    private Double basePeakMZ;
    private Double basePeakIntensity;
    private Integer function;
    private Integer scan;

    public LCMSSpectrum(Integer index, Double timeVal, String timeUnit, List<Pair<Double, Double>> intensities,
                        Double basePeakMZ, Double basePeakIntensity, Integer function, Integer scan) {
      this.index = index;
      this.timeVal = timeVal;
      this.timeUnit = timeUnit;
      this.intensities = intensities;
      this.basePeakMZ = basePeakMZ;
      this.basePeakIntensity = basePeakIntensity;
      this.function = function;
      this.scan = scan;
    }

    public Integer getIndex() {
      return index;
    }

    public Double getTimeVal() {
      return timeVal;
    }

    public String getTimeUnit() {
      return timeUnit;
    }

    public List<Pair<Double, Double>> getIntensities() {
      return intensities;
    }

    public Double getBasePeakMZ() {
      return basePeakMZ;
    }

    public Double getBasePeakIntensity() {
      return basePeakIntensity;
    }

    public Integer getFunction() {
      return function;
    }

    public Integer getScan() {
      return scan;
    }
  }
}
