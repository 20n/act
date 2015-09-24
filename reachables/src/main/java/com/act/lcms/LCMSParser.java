package com.act.lcms;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class LCMSParser {
  public abstract Iterator<LCMSSpectrum> getIterator(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException;

  public List<LCMSSpectrum> parse(String inputFile) throws Exception {
    List<LCMSSpectrum> spectra = new ArrayList<>();
    Iterator<LCMSSpectrum> iter = this.getIterator(inputFile);
    while (iter.hasNext()) {
      spectra.add(iter.next());
    }

    return spectra;
  }

}
