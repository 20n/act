package com.act.utils.parser;

import org.apache.commons.lang3.tuple.Pair;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.DBReferenceInfo;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenbankDNASeqInterpreterTest {
  protected GenbankDNASeqInterpreter gi;

  /*
    http://www.ncbi.nlm.nih.gov/nuccore/AB000097
    ACCESSION AB000097
   */
  final String SEQ = "AAAAAACCTAGCTAAACGAAGAAAATCATTCCAATACACATGGCTTCCGAAAGGCAAGCGTTAATGCTTATTCTCTTAACAAC" +
      "ATTCTTCTTCACCATAAAGCCTTCACAGGCCAGTACTACTGGTGGCATAACAATCTACTGGGGCCAAAACATTGACGACGGCACCTTG" +
      "ACCTCCACATGCGACACTGGAAACTTCGAGATTGTCAACCTAGCTTTCCTCAATGCGTTTGGTTGCGGCATAACTCCATCATGGAACT" +
      "TCGCTGGCCACTGTGGGGACTGGAACCCTTGTTCCATACTAGAACCCCAAATACAATACTGCCAGCAGAAAGGTGTCAAAGTCTTCCT" +
      "TTCCCTCGGTGGTGCTAAAGGAACCTACTCCCTCTGCTCACCCGAGGACGCAAAAGAAGTTGCCAATTACCTTTATCAAAACTTCCTC" +
      "AGTGGCAAACCCGGTCCACTTGGAAGTGTAACATTGGAAGGCATCGATTTCGACATTGAACTTGGTTCCAACCTCTATTGGGGCGACC" +
      "TTGCCAAGGAACTAGATGCTCTCAGGCACCAAAACGACCACTACTTCTACTTGTCCGCAGCCCCACAATGTTTTATGCCTGATTACCA" +
      "CCTCGACAATGCCATCAAAACTGGTCTTTTCGATCATGTAAACGTTCAGTTCTACAATAACCCTCCATGCCAATACTCACCTGGCAAT" +
      "ACTCAATTGCTTTTTAATTCATGGGATGATTGGACTTCAAATGTTCTTCCCAATAACTCTGTTTTCTTTGGACTACCAGCATCTCCCG" +
      "ACGCTGCTCCAAGTGGTGGTTATATACCACCACAGGTGCTCATTTCTGAGGTGCTTCCCTATGTAAAGCAAGCTTCCAACTATGGAGG" +
      "AGTTATGCTGTGGGACAGGTACCATGATGTTTTAAATTATCACAGCGATCAGATAAAGGATTATGTTCCAAAATATGCAATGCGGTTT" +
      "GTGACCGCAGTTTCCGACGCTATTTATGAGAGTGTCTCTGCACGTACGCACCGCATCTTACAGAAGAAACCATATTAGAAATATGGGG" +
      "AGCCGTACGTGCAAACTATTTATCAGCTATCTATGCATGTGTCCGTCTCTGTAACGTTTGTATGGAAAAATGGAAATAAGTAACAAAT" +
      "TGTTATTAGTTGTTACCTTTGTGGCATCTACTCCAGCTTTGATTTCCTAGCTAGTTGTTATGTAATGTAACCAATATAATCGAAGCAT" +
      "GTTGAGAATAAAATACTCCCTACTT";

  @Before
  public void setUp() throws Exception {
    gi = new GenbankDNASeqInterpreter(new File(this.getClass().getResource("genbank_test2.gb").getFile()));
    gi.init();
  }

  @Test
  public void testReadSequence() {
    assertEquals("test whether parser extracts sequence accurately", SEQ, gi.getSequences().get(0));
  }

  @Test
  public void testReadFeatures() {
    List<String> feature_types = new ArrayList<>(Arrays.asList("source", "5'UTR", "CDS", "sig_peptide",
        "mat_peptide", "regulatory"));
    for (String feature_type : feature_types) {
      assertTrue("test whether parser extracts feature types accurately",
          gi.getFeatures().get(0).contains(feature_type));
    }
  }

  @Test
  public void testReadQualifiers() {
    HashMap<String, String> qualifier_name_to_value_1 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_2 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_3 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_4 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_5 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_6 = new HashMap();


    Pair<String, String> feature_type_and_source_1 = Pair.of("source", "1..1252");
    Pair<String, String> feature_type_and_source_2 = Pair.of("5'UTR", "<1..39");
    Pair<String, String> feature_type_and_source_3 = Pair.of("CDS", "40..1041");
    Pair<String, String> feature_type_and_source_4 = Pair.of("sig_peptide", "40..114");
    Pair<String, String> feature_type_and_source_5 = Pair.of("mat_peptide", "115..1038");
    Pair<String, String> feature_type_and_source_6 = Pair.of("regulatory", "1234..1239");


    Map<Pair<String, String>, HashMap<String, String>> feature_to_qualifiers = new HashMap();
    qualifier_name_to_value_1.put("organism", "Glycine max");
    qualifier_name_to_value_1.put("mol_type", "mRNA");
    qualifier_name_to_value_1.put("cultivar", "Bonminori");
    qualifier_name_to_value_1.put("dbxref", "taxon:3847");

    qualifier_name_to_value_3.put("EC_number", "3.2.1.14");
    qualifier_name_to_value_3.put("product", "class III acidic endochitinase");
    qualifier_name_to_value_3.put("protein_id", "BAA25015.1");
    qualifier_name_to_value_3.put("dbxref", "GI:2934696");
    qualifier_name_to_value_3.put("translation", "MASERQALMLILLTTFFFTIKPSQASTTGGITIYWGQNIDDGTL" +
        "TSTCDTGNFEIVNLAFLNAFGCGITPSWNFAGHCGDWNPCSILEPQIQYCQQKGVKVF" +
        "LSLGGAKGTYSLCSPEDAKEVANYLYQNFLSGKPGPLGSVTLEGIDFDIELGSNLYWG" +
        "DLAKELDALRHQNDHYFYLSAAPQCFMPDYHLDNAIKTGLFDHVNVQFYNNPPCQYSP" +
        "GNTQLLFNSWDDWTSNVLPNNSVFFGLPASPDAAPSGGYIPPQVLISEVLPYVKQASN" +
        "YGGVMLWDRYHDVLNYHSDQIKDYVPKYAMRFVTAVSDAIYESVSARTHRILQKKPY");

    qualifier_name_to_value_5.put("product", "unnamed");

    qualifier_name_to_value_6.put("regulatory_class", "polyA_signal_sequence");


    feature_to_qualifiers.put(feature_type_and_source_1, qualifier_name_to_value_1);
    feature_to_qualifiers.put(feature_type_and_source_2, qualifier_name_to_value_2);
    feature_to_qualifiers.put(feature_type_and_source_3, qualifier_name_to_value_3);
    feature_to_qualifiers.put(feature_type_and_source_4, qualifier_name_to_value_4);
    feature_to_qualifiers.put(feature_type_and_source_5, qualifier_name_to_value_5);
    feature_to_qualifiers.put(feature_type_and_source_6, qualifier_name_to_value_6);


    for (Pair<String, String> feature_type_and_source : feature_to_qualifiers.keySet()) {
      for (List<Qualifier> qual_list : gi.getQualifiers(0, feature_type_and_source.getLeft(),
          feature_type_and_source.getRight()).values()) {
        for (Qualifier qual : qual_list) {
          HashMap qual_map = feature_to_qualifiers.get(feature_type_and_source);
          assertTrue("testing whether the qualifier name extracted is accurate", qual_map.containsKey(qual.getName()));
          if (qual.getName().equals("dbxref")) {
            assertEquals("testing whether the extracted value of the db_xref qualifier is accurate",
                qual_map.get(qual.getName()),
                ((DBReferenceInfo) qual).getDatabase() + ":" + ((DBReferenceInfo) qual).getId());
          } else {
            assertEquals("testing whether the extracted value of the qualifier is accurate",
                qual_map.get(qual.getName()), qual.getValue());
          }
        }
      }
    }
  }

  @Test
  public void testWriteFeatureAndQualifier() {
    AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature =
        gi.constructFeature("test_type", "test_source");

    gi.addQualifier(feature, "test_name", "test_value");
    gi.addFeature(1, 687, feature, 0);

    assertTrue("tests whether the feature was correctly written to the sequence object",
        gi.getFeatures().get(0).contains("test_type"));
    assertTrue("tests whether the qualifier map identifier was correctly written to the sequence object",
        gi.getQualifiers(0, "test_type", "test_source").keySet().contains("test_name"));
    assertEquals("tests whether the qualifier name was correctly written to the sequence object", "test_name",
        gi.getQualifiers(0, "test_type", "test_source").get("test_name").get(0).getName());
    assertEquals("tests whether the qualifier value was correctly written to the sequence object", "test_value",
        gi.getQualifiers(0, "test_type", "test_source").get("test_name").get(0).getValue());
  }
}
