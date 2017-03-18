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

package com.act.lcms.v2;

import chemaxon.license.LicenseManager;
import chemaxon.license.LicenseProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory to build IsotopeCalculators with the Chemaxon license
 */

public class LcmsIsotopeCalculatorFactory {

  private static final Logger LOGGER = LogManager.getFormatterLogger(LcmsIsotopeCalculatorFactory.class);

  private static LcmsIsotopeCalculator isotopeCalculator;

  static {
    isotopeCalculator = new LcmsIsotopeCalculator();
  }

  public static void setLicense(String pathToLicense) {
    try {
      LicenseManager.setLicenseFile(pathToLicense);
    } catch (LicenseProcessingException e) {
      String msg = String.format("Chemaxon license could not be processed: %s", e);
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  public static LcmsIsotopeCalculator getLcmsIsotopeCalculator() {
    return isotopeCalculator;
  }
}
