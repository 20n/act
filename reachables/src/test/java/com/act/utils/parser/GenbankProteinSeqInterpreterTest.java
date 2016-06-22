package com.act.utils.parser;

import org.apache.commons.lang3.tuple.Pair;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;
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

public class GenbankProteinSeqInterpreterTest {
  protected GenbankProteinSeqInterpreter gi;

  /*
    http://www.ncbi.nlm.nih.gov/protein/CUB13083
    ACCESSION CUB13083
   */
  final String SEQ = "MMTNLQKEFFKRLKIPAKEITFNDLDEILLKMGLTLPYENLDIMAGTIKDISKNNLVEKILIQKRGGLCYELNSLLYYFLMDCG" +
      "FQVYKVAGTVYDLYDNKWKPDDGHVIIVLTHNNKDYVIDAGFASHLPLHPVPFNGEVISSQTGEYRIRKRTTRKGTHILEMRKGANGES" +
      "TNFLQSEPSHEWKVGYAFTLDPIDEKKVNNIQKVIVEHKESPFNKGAITCKLTDYGHVSLTNKNYTETFKGTKNKRPIESKDYAHILRE" +
      "SFGITQVKYVGKTLERG";

  @Before
  public void setUp() throws Exception {
    gi = new GenbankProteinSeqInterpreter(new File(this.getClass().getResource("genbank_test.gb").getFile()));
    gi.init();
  }

  @Test
  public void testReadSequence() {
    assertEquals("test whether parser extracts sequence accurately", SEQ, gi.getSequences().get(0));
  }

  @Test
  public void testReadFeatures() {
    List<String> feature_types = new ArrayList<>(Arrays.asList("source", "Protein", "Region",
        "CDS", "restriction_site"));
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


    Pair<String, String> feature_type_and_source_1 = Pair.of("source", "1..279");
    Pair<String, String> feature_type_and_source_2 = Pair.of("Protein", "1..279");
    Pair<String, String> feature_type_and_source_3 = Pair.of("Region", "25..266");
    Pair<String, String> feature_type_and_source_4 = Pair.of("CDS", "1..279");
    Pair<String, String> feature_type_and_source_5 = Pair.of("restriction_site", "1..279");


    Map<Pair<String, String>, HashMap<String, String>> feature_to_qualifiers = new HashMap();
    qualifier_name_to_value_1.put("organism", "Bacillus cereus");
    qualifier_name_to_value_1.put("isolate", "JRS1");
    qualifier_name_to_value_1.put("dbxref", "taxon:1396");

    qualifier_name_to_value_2.put("product", "Arylamine N-acetyltransferase");
    qualifier_name_to_value_2.put("EC_number", "2.3.1.5");

    qualifier_name_to_value_3.put("region_name", "Acetyltransf_2");
    qualifier_name_to_value_3.put("note", "N-acetyltransferase; cl00949");
    qualifier_name_to_value_3.put("dbxref", "CDD:260716");

    qualifier_name_to_value_4.put("gene", "nat_1");
    qualifier_name_to_value_4.put("locus_tag", "BN2127_JRS1_04775");
    qualifier_name_to_value_4.put("coded_by", "complement(CYHI01000402.1:9425..10264)");
    qualifier_name_to_value_4.put("inference", "ab initio prediction:Prodigal:2.60");
    qualifier_name_to_value_4.put("note", "*protein_id: CGR:BN2127_JRS1_04775");

    qualifier_name_to_value_5.put("gene", "test_gene");
    qualifier_name_to_value_5.put("note", "test_case");

    feature_to_qualifiers.put(feature_type_and_source_1, qualifier_name_to_value_1);
    feature_to_qualifiers.put(feature_type_and_source_2, qualifier_name_to_value_2);
    feature_to_qualifiers.put(feature_type_and_source_3, qualifier_name_to_value_3);
    feature_to_qualifiers.put(feature_type_and_source_4, qualifier_name_to_value_4);
    feature_to_qualifiers.put(feature_type_and_source_5, qualifier_name_to_value_5);

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
    AbstractFeature<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature =
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

  @Test
  public void testCompressedGenbankFilesAreAutomaticallyRead() throws Exception {
    GenbankInterpreter gi2 =
        new GenbankInterpreter(new File(this.getClass().getResource("genbank_test.gb.gz").getFile()));
    gi2.init();
    assertEquals("test whether parser extracts sequence accurately", SEQ, gi2.getSequences().get(0));
  }
}
