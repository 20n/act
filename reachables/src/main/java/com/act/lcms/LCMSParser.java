/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

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
public interface LCMSParser {
  /**
   * Returns an iterator over the LCMS time points in the given input file.
   * @param inputFile The LCMS input file to read.
   * @return An iterator over the LCMS time points in that file, represented by LCMSSpectrum objects.
   * @throws ParserConfigurationException
   * @throws IOException
   * @throws XMLStreamException
   */
  Iterator<LCMSSpectrum> getIterator(String inputFile)
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
  List<LCMSSpectrum> parse(String inputFile)
      throws ParserConfigurationException, IOException, XMLStreamException;
}
