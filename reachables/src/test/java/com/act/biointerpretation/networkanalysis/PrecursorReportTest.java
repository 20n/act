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

package com.act.biointerpretation.networkanalysis;

import com.act.lcms.MS1;
import com.act.lcms.v2.DetectedPeak;
import com.act.lcms.v2.Ion;
import com.act.lcms.v2.IonCalculator;
import com.act.lcms.v2.LcmsIon;
import com.act.lcms.v2.Metabolite;
import com.act.lcms.v2.MolecularStructure;
import com.act.lcms.v2.PeakSpectrum;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Tests for class PrecursorReport.
 */
public class PrecursorReportTest {

  private static final String INCHI_1 = "A";
  private static final String INCHI_2 = "B";

  private static final Double MASS_1 = 10.0;
  private static final Double MASS_2 = 30.0;

  private static final Ion MET_1_ION_1 = new LcmsIon(15.0, Mockito.mock(MS1.MetlinIonMass.class));
  private static final Ion MET_1_ION_2 = new LcmsIon(20.0, Mockito.mock(MS1.MetlinIonMass.class));
  private static final Ion MET_2_ION_1 = new LcmsIon(25.0, Mockito.mock(MS1.MetlinIonMass.class));

  private static Metabolite METABOLITE_1;
  private static Metabolite METABOLITE_2;

  private static List<NetworkNode> nodes = new ArrayList<>();
  private static Map<String, Integer> inchiToID = new HashMap<>();

  private static final Double MASS_TOLERANCE = 0.01;

  private static final Set<String> ions = new HashSet<String>() {{
    add("M+H");
    add("M+Na");
  }};

  private static final List<DetectedPeak> NO_PEAKS = Collections.EMPTY_LIST;
  private static final List<DetectedPeak> SOME_PEAKS = new ArrayList<DetectedPeak>() {{
    add(Mockito.mock(DetectedPeak.class));
  }};

  private IonCalculator mockIonCalculator = Mockito.mock(IonCalculator.class);

  @Before
  public void createMockNodes() {
    METABOLITE_1 = getMockMetabolite(INCHI_1, MASS_1);
    METABOLITE_2 = getMockMetabolite(INCHI_2, MASS_2);

    Mockito.when(mockIonCalculator.getSelectedIons(METABOLITE_1, ions, MS1.IonMode.POS)).thenReturn(
        Arrays.asList(MET_1_ION_1, MET_1_ION_2));
    Mockito.when(mockIonCalculator.getSelectedIons(METABOLITE_2, ions, MS1.IonMode.POS)).thenReturn(
        Arrays.asList(MET_2_ION_1));

    nodes.add(new NetworkNode(METABOLITE_1));
    nodes.add(new NetworkNode(METABOLITE_2));
    nodes.forEach(n -> inchiToID.put(n.getMetabolite().getStructure().get().getInchi(), n.getUID()));
  }

  private Metabolite getMockMetabolite(String s, double mass) {
    MolecularStructure structure = Mockito.mock(MolecularStructure.class);
    Mockito.when(structure.getInchi()).thenReturn(s);
    Mockito.when(structure.getMonoIsotopicMass()).thenReturn(mass);

    Metabolite m = Mockito.mock(Metabolite.class);
    Mockito.when(m.getStructure()).thenReturn(Optional.of(structure));
    Mockito.when(m.getMonoIsotopicMass()).thenReturn(mass);
    return m;
  }

  /**
   * Ensures that the process of matching peaks from a PeakSpectrum to nodes in a PrecursorReport is done properly.
   * If any of a node's ions matches a peak, it should come out as a hit.
   * if none of a node's ions match any peak, it should come out as a miss.
   */
  @Test
  public void testAddLcmsData() {
    // Arrange
    MetabolismNetwork network = new MetabolismNetwork();
    nodes.forEach(network::addNode);
    network.addEdgeFromInchis(Arrays.asList(INCHI_1), Arrays.asList(INCHI_2));
    NetworkNode node1 = network.getNodeByInchi(INCHI_1);
    NetworkNode node2 = network.getNodeByInchi(INCHI_2);
    PrecursorReport report = network.getPrecursorReport(node2, 1);

    PeakSpectrum spectrum = Mockito.mock(PeakSpectrum.class);
    Mockito.when(spectrum.getPeaksByMZ(Mockito.any(), Mockito.any())).thenReturn(NO_PEAKS);
    Mockito.when(spectrum.getPeaksByMZ(MET_1_ION_1.getMzValue(), 1.0)).thenReturn(SOME_PEAKS);

    // Act
    report.addLcmsData(spectrum, mockIonCalculator, ions);

    // Assert
    assertEquals("Metabolite 1, matching at one ion, is positive.", new Double(1.0), report.getLcmsConfidence(node1));
    assertEquals("Metabolite 2, matching at no ions, is negative.", new Double(0.0), report.getLcmsConfidence(node2));
  }
}
