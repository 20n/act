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

package act.installer.reachablesexplorer;

import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolFormatException;
import chemaxon.marvin.calculations.HlbPlugin;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.marvin.calculations.pKaPlugin;
import chemaxon.marvin.plugin.PluginException;
import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeExporter;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;


public class PhysiochemicalPropertiesCalculator {

  private static final Logger LOGGER = LogManager.getFormatterLogger(PhysiochemicalPropertiesCalculator.class);

  private logPPlugin logpPlugin = new logPPlugin();  // Capitalized incorrectly to avoid conflicting with class name.
  private HlbPlugin hlbPlugin = HlbPlugin.Builder.createNew();
  private pKaPlugin pkaPlugin = new pKaPlugin(); // Capitalized incorrectly to avoid conflicting with class name.

  private PhysiochemicalPropertiesCalculator() {
  }

  private void init() throws PluginException {
    logpPlugin.setlogPMethod(LogPMethod.CONSENSUS);

    logpPlugin.setUserTypes("logPTrue,logPMicro,logPNonionic"); // These arguments were chosen via experimentation.

    // From the documentation.  Not sure what these knobs do...
    pkaPlugin.setBasicpKaLowerLimit(-5.0);
    pkaPlugin.setAcidicpKaUpperLimit(25.0);

    // Set biologically plausible pH values
    // Note: these are only used for microspecies calculation, which we are not making use of currently.
    pkaPlugin.setpHLower(6.0);
    pkaPlugin.setpHUpper(8.0);
    pkaPlugin.setpHStep(0.5);
  }

  /**
   * This function computes the LogP, HLB, and pKa for the specified InChI.
   * @param inchi The molecule whose features to compute.
   * @return An object containing features calculated by this class.
   * @throws MolFormatException
   * @throws PluginException
   * @throws IOException
   */
  public Features computeFeatures(String inchi) throws PluginException, IOException {
    return computeFeatures(MoleculeImporter.importMolecule(inchi));
  }

  /**
   * This function computes the LogP, HLB, and pKa for the specified InChI.
   * @param inputMol The molecule whose features to compute.
   * @return An object containing features calculated by this class.
   * @throws PluginException
   * @throws IOException
   */
  public Features computeFeatures(Molecule inputMol) throws MolFormatException, PluginException, IOException {
    Cleaner.clean(inputMol, 3); // TODO: can this be 2D instead?

    try {
      // LogP calculation
      logpPlugin.standardize(inputMol);
      logpPlugin.setMolecule(inputMol);
      logpPlugin.run();
    } catch (ArrayIndexOutOfBoundsException e) {
      LOGGER.error("Failed to compute logP physio-chemical property for %s", MoleculeExporter.exportAsStdInchi(inputMol));
      return null;
    }
    // Capture the standardized molecule for reuse.
    Molecule mol = logpPlugin.getResultMolecule();
    Double logP = logpPlugin.getlogPTrue();

    // HLB calculation
    hlbPlugin.setMolecule(mol);
    hlbPlugin.run();
    double hlbVal = hlbPlugin.getHlbValue();

    // pKa calculation
    pkaPlugin.standardize(mol);
    pkaPlugin.setMolecule(mol);
    pkaPlugin.run();
    double[] pKaAcidVals = new double[3];
    int[] pKaAcidIndices = new int[3];
    // Get the first (lowest pH) pKa for this molecule.
    pkaPlugin.getMacropKaValues(pkaPlugin.ACIDIC, pKaAcidVals, pKaAcidIndices);

    return new Features(logP, hlbVal, pKaAcidVals[0]);
  }



  public static class Factory {
    // TODO: Take a license file as an optional argument.
    public PhysiochemicalPropertiesCalculator build() throws PluginException {
      PhysiochemicalPropertiesCalculator calculator = new PhysiochemicalPropertiesCalculator();
      calculator.init();
      return calculator;
    }
  }

  public static class Features {
    Double logP;
    Double hlb;
    double pKa;

    public Features(Double logP, Double hlb, double pKa) {
      this.logP = logP;
      this.hlb = hlb;
      this.pKa = pKa;
    }

    public Double getLogP() {
      return logP;
    }

    public Double getHlb() {
      return hlb;
    }

    public double getpKa() {
      return pKa;
    }
  }
}
