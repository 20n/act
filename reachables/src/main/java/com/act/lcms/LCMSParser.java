package com.act.lcms;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A class that can parse a particular format of LCMS apparatus output.
 */
public abstract class LCMSParser {
  /**
   * Returns an iterator over the LCMS time points in the given input file.
   * @param inputFile The LCMS input file to read.
   * @return An iterator over the LCMS time points in that file, represented by LCMSSpectrum objects.
   * @throws ParserConfigurationException
   * @throws IOException
   * @throws XMLStreamException
   */
  public abstract Iterator<LCMSSpectrum> getIterator(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException;

  /**
   * Parses an LCMS file, returning all time points in that file as LCMSSpectra.
   *
   * Note: expect this function to be very memory-intensive.  Use getIterator() instead if possible.
   * @param inputFile The LCMS input file to read.
   * @return The list of all LCMS time points read from that file, represented by LCMSSpectrum objects.
   * @throws ParserConfigurationException
   * @throws IOException
   * @throws XMLStreamException
   */
  public List<LCMSSpectrum> parse(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException {
    List<LCMSSpectrum> spectra = new ArrayList<>();
    Iterator<LCMSSpectrum> iter = this.getIterator(inputFile);
    while (iter.hasNext()) {
      spectra.add(iter.next());
    }

    return spectra;
  }

}
