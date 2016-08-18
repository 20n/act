package com.act.biointerpretation.Utils;

import act.shared.Organism;
import com.act.biointerpretation.test.util.MockedNoSQLAPI;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class OrgMinimalPrefixGeneratorTest {

  private MockedNoSQLAPI mockAPI;
  private Map<String, String> minimalPrefixMapping;

  @Before
  public void setUp() {
    Map<Long, String> testOrgNames = new HashMap<>();
    testOrgNames.put(4000003474L, "Mus musculus");
    testOrgNames.put(4000003475L, "Mus musculus sp.");
    testOrgNames.put(4000003476L, "Mus musculus sp. 123");
    testOrgNames.put(4000003477L, "Lactobacillus");
    testOrgNames.put(4000003478L, "Lactobacillus buchneris");
    testOrgNames.put(4000003479L, "Lactobacillus salivarius");
    testOrgNames.put(4000003480L, "Lactobacillus sp. SK007");
    testOrgNames.put(4000003481L, "Lactobacillus sp.");
    testOrgNames.put(4000003482L, "Streptococcus");
    testOrgNames.put(4000003483L, "Streptococcus mitior");
    testOrgNames.put(4000003484L, "Streptococcus sanguinis");
    testOrgNames.put(400008594L, "Homo sapiens");

    mockAPI = new MockedNoSQLAPI();
    mockAPI.installMocks(new ArrayList<>(), new ArrayList<>(), testOrgNames, new HashMap<>());

    Iterator<Organism> orgIterator = mockAPI.getMockNoSQLAPI().readOrgsFromInKnowledgeGraph();

    OrgMinimalPrefixGenerator prefixGenerator = new OrgMinimalPrefixGenerator(orgIterator);
    minimalPrefixMapping = prefixGenerator.getMinimalPrefixMapping();
  }

  @Test
  public void testMinimalPrefixMapping() {
    assertEquals("testing minimal prefix mapping", "Mus musculus", minimalPrefixMapping.get("Mus musculus"));
    assertEquals("testing minimal prefix mapping", "Mus musculus", minimalPrefixMapping.get("Mus musculus sp."));
    assertEquals("testing minimal prefix mapping", "Mus musculus", minimalPrefixMapping.get("Mus musculus sp. 123"));

    assertEquals("testing minimal prefix mapping", "Lactobacillus", minimalPrefixMapping.get("Lactobacillus"));
    assertEquals("testing minimal prefix mapping", "Lactobacillus", minimalPrefixMapping.get("Lactobacillus buchneris"));
    assertEquals("testing minimal prefix mapping", "Lactobacillus", minimalPrefixMapping.get("Lactobacillus salivarius"));
    assertEquals("testing minimal prefix mapping", "Lactobacillus", minimalPrefixMapping.get("Lactobacillus sp. SK007"));
    assertEquals("testing minimal prefix mapping", "Lactobacillus", minimalPrefixMapping.get("Lactobacillus sp."));

    assertEquals("testing minimal prefix mapping", "Streptococcus", minimalPrefixMapping.get("Streptococcus"));
    assertEquals("testing minimal prefix mapping", "Streptococcus", minimalPrefixMapping.get("Streptococcus mitior"));
    assertEquals("testing minimal prefix mapping", "Streptococcus", minimalPrefixMapping.get("Streptococcus sanguinis"));

    assertEquals("testing minimal prefix mapping", "Homo sapiens", minimalPrefixMapping.get("Homo sapiens"));
  }

}
