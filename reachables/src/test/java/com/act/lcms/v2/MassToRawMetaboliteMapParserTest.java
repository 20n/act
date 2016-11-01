package com.act.lcms.v2;


import chemaxon.formats.MolFormatException;
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
    List<String> headers = new ArrayList<>();
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);
  }

  @Test
  public void testValidateSingleHeader() {
    List<String> headers = Collections.singletonList(inchiHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);
    Integer expectedHeaderPosition = 0;
    assertEquals(parser.getMetaboliteIndex(), expectedHeaderPosition);
    assertEquals(parser.getMassToMoleculeMap().getKind(), MassToRawMetaboliteMap.RawMetaboliteKind.INCHI);
  }

  @Test
  public void testValidateMassHeader() {
    List<String> headers = Arrays.asList(formulaHeader, massHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);
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
    List<String> headers = Arrays.asList(formulaHeader, massHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);
    String testCase = "C8H9NO2\t151.063";
    parser.addRawMetabolite(testCase);
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    assertTrue(map.getMassToMoleculeMap().containsKey(151.063));
    List<RawMetabolite> value = map.getMassToMoleculeMap().get(151.063);
    assertEquals(1, value.size());
    assertEquals("C8H9NO2", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
  }

  @Test
  public void testAddRawMetabolitesWithNames() {
    List<String> headers = Arrays.asList(formulaHeader, massHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);
    String testCase = "C8H9NO2\t151.063\tAPAP";
    parser.addRawMetabolite(testCase);
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    assertTrue(map.getMassToMoleculeMap().containsKey(151.063));
    List<RawMetabolite> value = map.getMassToMoleculeMap().get(151.063);
    assertEquals(1, value.size());
    assertEquals("C8H9NO2", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP", value.get(0).getName());
  }

  @Test
  public void testAddRawMetabolitesWithoutMass() {
    List<String> headers = Arrays.asList(inchiHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);
    String testCase = "InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)\tAPAP";
    parser.addRawMetabolite(testCase);
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    Double testKey = map.getMassToMoleculeMap().ceilingKey(151.0);
    assertEquals(testKey, 151.063, 0.001);
    List<RawMetabolite> value = map.getMassToMoleculeMap().get(testKey);
    assertEquals(1, value.size());
    assertEquals("InChI=1S/C8H9NO2/c1-6(10)9-7-2-4-8(11)5-3-7/h2-5,11H,1H3,(H,9,10)", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP", value.get(0).getName());
  }


  @Test
  public void testAddMultipleRawMetabolites() {
    List<String> headers = Arrays.asList(formulaHeader, massHeader, nameHeader);
    parser = new MassToRawMetaboliteMapParser();
    parser.validateHeaders(headers);
    String testCase1 = "C8H9NO2\t151.063\tAPAP";
    String testCase2 = "C8H9NO2\t151.063\tAPAP2";
    parser.addRawMetabolite(testCase1);
    parser.addRawMetabolite(testCase2);
    MassToRawMetaboliteMap map = parser.getMassToMoleculeMap();
    assertTrue(map.getMassToMoleculeMap().containsKey(151.063));
    List<RawMetabolite> value = map.getMassToMoleculeMap().get(151.063);
    assertEquals(2, value.size());
    assertEquals("C8H9NO2", value.get(0).getMolecule());
    assertEquals(151.063, value.get(0).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP", value.get(0).getName());
    assertEquals("C8H9NO2", value.get(1).getMolecule());
    assertEquals(151.063, value.get(1).getMonoIsotopicMass(), 0.001);
    assertEquals("APAP2", value.get(1).getName());
  }
}
