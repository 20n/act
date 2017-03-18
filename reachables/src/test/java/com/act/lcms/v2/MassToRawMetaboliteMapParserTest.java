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


import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MassToRawMetaboliteMapParserTest {

  private MassToRawMetaboliteMapParser parser;
  private String inchiHeader = "inchi";
  private String formulaHeader = "formula";
  private String massHeader = "mass";
  private String nameHeader = "name";


  @Test(expected=RuntimeException.class)
  public void testEmptyHeaders() {
    // Setup
    List<String> headers = new ArrayList<>();
    parser = new MassToRawMetaboliteMapParser();
    // Try validating empty headers
    parser.validateHeaders(headers);
  }

  @Test
  public void testValidateSingleHeader() {
    // Setup
    List<String> headers = Collections.singletonList(inchiHeader);
    parser = new MassToRawMetaboliteMapParser();
    // Perform validation of a single header
    parser.validateHeaders(headers);
    // Test the validation
    Integer expectedHeaderPosition = 0;
    assertEquals(parser.getMetaboliteIndex(), expectedHeaderPosition);
    assertEquals(parser.getMassToMoleculeMap().getKind(), MassToRawMetaboliteMap.RawMetaboliteKind.INCHI);
  }

  @Test
  public void testValidateMassHeader() {
    // Setup
    List<String> headers = Arrays.asList(formulaHeader, massHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    // Perform header validation
    parser.validateHeaders(headers);
    // Check that:
    // 1) the map has the correct kind
    // 2) indices are extracted as expected
    Integer expectedFormulaHeaderPosition = 0;
    Integer expectedMassHeaderPosition = 1;
    Integer expectedNameHeaderPosition = 2;
    assertEquals(parser.getMassToMoleculeMap().getKind(), MassToRawMetaboliteMap.RawMetaboliteKind.FORMULA);
    assertEquals(expectedFormulaHeaderPosition, parser.getMetaboliteIndex());
    assertEquals(expectedMassHeaderPosition, parser.getMassIndex());
    assertEquals(expectedNameHeaderPosition, parser.getNameIndex());
  }

  @Test
  public void testAddRawMetabolites() {
    // Setup
    List<String> headers = Arrays.asList(formulaHeader, massHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);

    // Add a metabolite
    String testCase = "C8H9NO2\t151.063";
    parser.addRawMetabolite(testCase);

    // Test whether the metabolite was added correctly
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    assertTrue(map.containsKey(151.063));
    List<RawMetabolite> value = map.get(151.063);
    assertEquals(1, value.size());
    assertEquals("C8H9NO2", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
  }

  @Test
  public void testAddRawMetabolitesWithNames() {
    // Setup
    List<String> headers = Arrays.asList(formulaHeader, massHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);

    // Add a metabolite with name
    String testCase = "C8H9NO2\t151.063\tAPAP";
    parser.addRawMetabolite(testCase);

    // Test whether the metabolite was added correctly
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    assertTrue(map.containsKey(151.063));
    List<RawMetabolite> value = map.get(151.063);
    assertEquals(1, value.size());
    assertEquals("C8H9NO2", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP", value.get(0).getName().get());
  }

  @Test
  public void testAddRawMetabolitesWithoutMass() {
    // Setup
    List<String> headers = Arrays.asList(inchiHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);

    // Add inchi metabolite without mass
    String testCase = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)\tAPAP";
    parser.addRawMetabolite(testCase);

    // Test whether the metabolite was added correctly
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    Double testKey = map.ceilingKey(151.0);
    assertEquals(testKey, 151.063, 0.001);
    List<RawMetabolite> value = map.get(testKey);
    assertEquals(1, value.size());
    assertEquals("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP", value.get(0).getName().get());
  }


  @Test
  public void testAddMultipleRawMetabolites() {
    // Setup
    List<String> headers = Arrays.asList(formulaHeader, massHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);

    // Add multiple metabolites
    String testCase1 = "C8H9NO2\t151.063\tAPAP";
    String testCase2 = "C8H9NO2\t151.063\tAPAP2";
    parser.addRawMetabolite(testCase1);
    parser.addRawMetabolite(testCase2);

    // Test whether the metabolites were added correctly
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    assertTrue(map.containsKey(151.063));
    List<RawMetabolite> value = map.get(151.063);
    assertEquals(2, value.size());
    assertEquals("C8H9NO2", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP", value.get(0).getName().get());
    assertEquals("C8H9NO2", value.get(1).getMolecule());
    assertEquals(151.063, value.get(1).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP2", value.get(1).getName().get());
  }
}
