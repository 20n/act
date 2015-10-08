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


public abstract class MzMLParser<S> {
  public static final String SPECTRUM_OBJECT_TAG = "spectrum";
  public static final String XML_PREAMBLE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";

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
  protected static final ThreadLocal<XPathFactory> XPATH_FACTORY = new ThreadLocal<XPathFactory>() {
    @Override
    protected XPathFactory initialValue() {
      return XPathFactory.newInstance();
    }
  };

  /**
   * Helper function: builds an XML DocumentBuilderFactory that can be used repeatedly in this class.
   * <p>
   * TODO: move this to an XML utility class, as I'm sure we'll use it again some day.
   *
   * @return An XML DocumentBuilderFactory.
   * @throws ParserConfigurationException
   */
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

  protected static List<Double> base64ToDoubleList(String b64) {
    byte[] decodedBytes = Base64.getDecoder().decode(b64);
    ByteBuffer buf = ByteBuffer.wrap(decodedBytes).order(ByteOrder.LITTLE_ENDIAN);
    List<Double> values = new ArrayList<>(decodedBytes.length / 8);
    while (buf.hasRemaining()) {
      values.add(buf.getDouble());
    }
    return values;
  }

  // TODO: isn't there some library method for this?  The Interwebs seem to say there isn't...
  protected static <K, V> List<Pair<K, V>> zipLists(List<K> keys, List<V> vals) {
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

  public MzMLParser() {
  }

  protected XPathFactory getXPathFactory() {
    return XPATH_FACTORY.get();
  }

  public Iterator<S> getIterator(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {
    DocumentBuilderFactory docFactory = mkDocBuilderFactory();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

    final XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
    final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

    return new Iterator<S>() {
      boolean inEntry = false;

      XMLEventReader xr = xmlInputFactory.createXMLEventReader(new FileInputStream(inputFile), "utf-8");
      // TODO: is the use of the XML version/encoding tag definitely necessary?
      StringWriter w = new StringWriter().append(XML_PREAMBLE).append("\n");
      XMLEventWriter xw = xmlOutputFactory.createXMLEventWriter(w);

      S next = null;

      /* Because we're handling the XML as a stream, we can only determine whether we have another Spectrum to return
       * by attempting to parse the next one.  `this.next()` reads
       */
      private S getNextSpectrum() {
        S spectrum = null;
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
              // Don't stop parsing if handleSpectrumEntry didn't like this spectrum document.
              if (spectrum != null) {
                break;
              }
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

      private S tryParseNext() {
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
      public S next() {
        // Prime the pump like we do in hasNext().
        if (this.next == null) {
          this.next = tryParseNext();
        }

        // Take available spectrum and return it.
        S res = this.next;
        /* Advance to the next element immediately, making next() do the heavy lifting most of the time.  Otherwise,
         * the parsing will resume on hasNext(), which seems like it ought to be a light-weight operation. */
        this.next = tryParseNext();

        return res;
      }

    };
  }

  public List<S> parse(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {
    List<S> spectra = new ArrayList<>();
    Iterator<S> iter = this.getIterator(inputFile);
    while (iter.hasNext()) {
      spectra.add(iter.next());
    }

    return spectra;
  }

  protected abstract S handleSpectrumEntry(Document doc) throws XPathException;
}
