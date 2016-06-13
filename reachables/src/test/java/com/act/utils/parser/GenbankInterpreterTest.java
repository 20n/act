package com.act.utils.parser;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.DBReferenceInfo;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenbankInterpreterTest {
  protected GenbankInterpreter gi;

  /*
    http://www.ncbi.nlm.nih.gov/nuccore/KR653209
    ACCESSION   KR653209 REGION: 26625..27302
   */
  final String SEQ = "ATGTTTATCAGTGATAAAGTGTCAAGCATGACAAAGTTGCAGCCGAATACAGTGATCCGTGCCGCCCTGGACCTGTTGAACGA" +
      "GGTCGGCGTAGACGGTCTGACGACACGCAAACTGGCGGAACGGTTGGGGGTTCAGCAGCCGGCGCTTTACTGGCACTTCAGGAACAAG" +
      "CGGGCGCTGCTCGACGCACTGGCCGAAGCCATGCTGGCGGAGAATCATACGCATTCGGTGCCGAGAGCCGACGACGACTGGCGCTCAT" +
      "TTCTGATCGGGAATGCCCGCAGCTTCAGGCAGGCGCTGCTCGCCTACCGCGATGGCGCGCGCATCCATGCCGGCACGCGACCGGGCGC" +
      "ACCGCAGATGGAAACGGCCGACGCGCAGCTTCGCTTCCTCTGCGAGGCGGGTTTTTCGGCCGGGGACGCCGTCAATGCGCTGATGACA" +
      "ATCAGCTACTTCACTGTTGGGGCCGTGCTTGAGGAGCAGGCCGGCGACAGCGATGCCGGCGAGCGCGGCGGCACCGTTGAACAGGCTC" +
      "CGCTCTCGCCGCTGTTGCGGGCCGCGATAGACGCCTTCGACGAAGCCGGTCCGGACGCAGCGTTCGAGCAGGGACTCGCGGTGATTGT" +
      "CGATGGATTGGCGAAAAGGAGGCTCGTTGTCAGGAACGTTGAAGGACCGAGAAAGGGTGACGATTGA";

  @Before
  public void setUp() throws Exception {
    gi = new GenbankInterpreter(new File(this.getClass().getResource("genbank_test.gb").getFile()));
    gi.init();
  }

  @Test
  public void testReadSequence() {
    assertEquals("test whether parser extracts sequence accurately", SEQ, gi.getSequences().get(0));
  }

  @Test
  public void testReadFeatures() {
    List<String> feature_types = new ArrayList<>(Arrays.asList("source", "gene", "CDS"));
    for (String feature_type : feature_types) {
      assertTrue("test whether parser extracts feature types accurately", gi.getFeatures().get(0).contains(feature_type));
    }
  }

  @Test
  public void testReadQualifiers() {
    HashMap<String, String> qualifier_name_to_value_1 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_2 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_3 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_4 = new HashMap();

    Pair<String, String> feature_type_and_source_1 = new ImmutablePair<>("source", "1..678");
    Pair<String, String> feature_type_and_source_2 = new ImmutablePair<>("gene", "1..678");
    Pair<String, String> feature_type_and_source_3 = new ImmutablePair<>("CDS", "1..678");
    Pair<String, String> feature_type_and_source_4 = new ImmutablePair<>("restriction_site", "1..678");

    Map<Pair<String, String>, HashMap<String, String>> feature_to_qualifiers = new HashMap();
    qualifier_name_to_value_1.put("organism", "Escherichia coli");
    qualifier_name_to_value_1.put("mol_type", "genomic DNA");
    qualifier_name_to_value_1.put("strain", "GDZ13");
    qualifier_name_to_value_1.put("host", "chicken");
    qualifier_name_to_value_1.put("dbxref", "taxon:562");
    qualifier_name_to_value_1.put("plasmid", "pGD0503Z13");

    qualifier_name_to_value_2.put("gene", "tetR");

    qualifier_name_to_value_3.put("gene", "tetR");
    qualifier_name_to_value_3.put("note", "Transcriptional regulator, TetR family");
    qualifier_name_to_value_3.put("codon_start", "1");
    qualifier_name_to_value_3.put("transl_table", "11");
    qualifier_name_to_value_3.put("product", "TetR");
    qualifier_name_to_value_3.put("protein_id", "AKT72247.1");
    qualifier_name_to_value_3.put("dbxref", "GI:908773452");
    qualifier_name_to_value_3.put("translation", "MFISDKVSSMTKLQPNTVIRAALDLLNEVGVDGLTTRKLAERLG" +
        "VQQPALYWHFRNKRALLDALAEAMLAENHTHSVPRADDDWRSFLIGNARSFRQALLAYRDGARIHAGTRPGAPQMETADAQ" +
        "LRFLCEAGFSAGDAVNALMTISYFTVGAVLEEQAGDSDAGERGGTVEQAPLSPLLRAAIDAFDEAGPDAAFEQGLAVIVDG" +
        "LAKRRLVVRNVEGPRKGDD");

    qualifier_name_to_value_4.put("gene", "test_gene");
    qualifier_name_to_value_4.put("note", "test_case");

    feature_to_qualifiers.put(feature_type_and_source_1, qualifier_name_to_value_1);
    feature_to_qualifiers.put(feature_type_and_source_2, qualifier_name_to_value_2);
    feature_to_qualifiers.put(feature_type_and_source_3, qualifier_name_to_value_3);
    feature_to_qualifiers.put(feature_type_and_source_4, qualifier_name_to_value_4);

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
    Qualifier qualifier = gi.constructQualifier("test_name", "test_value");

    gi.addQualifier(feature, qualifier);
    gi.addFeature(1, 687, feature, 0);

    assertTrue("tests whether the feature was correctly written to the sequence object",
        gi.getFeatures().get(0).contains("test_type"));
    assertTrue("tests whether the qualifier name was correctly written to the sequence object",
        gi.getQualifiers(0, "test_type", "test_source").keySet().contains("test_name"));
    assertTrue("tests whether the qualifier value was correctly written to the sequence object",
        gi.getQualifiers(0, "test_type", "test_source").get("test_name").contains(qualifier));
  }
}
