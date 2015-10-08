package com.act.lcms;

import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.regex.Matcher;

public class LCMS2mzMLParser extends MzMLParser<LCMS2MZSelection> {
  public static final String SPECTRUM_PATH_MS_LEVEL = "/spectrum/cvParam[@name='ms level']/@value";
  public static final String SPECTRUM_PATH_ISOLATION_WINDOW_TARGET =
      "/spectrum/precursorList/precursor/isolationWindow/cvParam[@name='isolation window target m/z']/@value";
  public static final String SPECTRUM_PATH_SELECTED_ION_MZ =
      "/spectrum/precursorList/precursor/selectedIonList/selectedIon/cvParam[@name='selected ion m/z']/@value";
  public static final String SPECTRUM_PATH_COLLISION_ENERGY =
      "/spectrum/precursorList/precursor/activation/cvParam[@name='collision energy']/@value";

  @Override
  protected LCMS2MZSelection handleSpectrumEntry(Document doc) throws XPathException {
    XPath xpath = XPATH_FACTORY.get().newXPath();
/*
    try {
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

      transformer.transform(new DOMSource(doc),
          new StreamResult(new OutputStreamWriter(System.out, "UTF-8")));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    */

    Double spectrumIndexD = (Double)xpath.evaluate(SPECTRUM_PATH_INDEX, doc, XPathConstants.NUMBER);
    if (spectrumIndexD == null) {
      System.err.format("WARNING: found spectrum document without index attribute.\n");
      return null;
    }
    Integer spectrumIndex = spectrumIndexD.intValue();

    Double msLevelD = (Double)xpath.evaluate(SPECTRUM_PATH_MS_LEVEL, doc, XPathConstants.NUMBER);
    if (msLevelD == null || msLevelD.intValue() != 2) {
      // If it is not MS2 Spectrum data then we will skip from the output.  The 'ms level' value can be null.
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

    Double isolationWindowTarget =
        (Double)xpath.evaluate(SPECTRUM_PATH_ISOLATION_WINDOW_TARGET, doc, XPathConstants.NUMBER);
    if (isolationWindowTarget == null) {
      System.err.format("WARNING: no isolation window target found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    Double selectedIonMZ = (Double)xpath.evaluate(SPECTRUM_PATH_SELECTED_ION_MZ, doc, XPathConstants.NUMBER);
    if (selectedIonMZ == null) {
      System.err.format("WARNING: no selection ion m/z found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    Double collisionEnergy = (Double)xpath.evaluate(SPECTRUM_PATH_COLLISION_ENERGY, doc, XPathConstants.NUMBER);
    if (collisionEnergy == null) {
      System.err.format("WARNING: no collision energy found for spectrum document %d\n", spectrumIndex);
      return null;
    }

    return new LCMS2MZSelection(spectrumIndex, scanStartTime, scanStartTimeUnit, spectrumScan,
        isolationWindowTarget, selectedIonMZ, collisionEnergy);
  }

  public static void main(String[] args) throws Exception {
    Iterator<LCMS2MZSelection> selections = new LCMS2mzMLParser().getIterator(args[0]);
    while (selections.hasNext()) {
      LCMS2MZSelection s = selections.next();
      System.out.format("%d: %d %f %f %f %f\n", s.getIndex(), s.getScan(), s.getTimeVal(), s.getSelectedIonMZ(),
          s.getSelectedIonMZ(), s.getCollisionEnergy());
    }
  }
}
