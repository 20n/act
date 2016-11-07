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
