package com.act.utils.parser;

import com.act.utils.parser.GenbankInterpreter;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.DBReferenceInfo;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.template.AbstractSequence;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenbankInterpreterTest {
    public GenbankInterpreter gi;
    HashMap<String, String> qualifier_name_to_value_1 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_2 = new HashMap();
    HashMap<String, String> qualifier_name_to_value_3 = new HashMap();

    List<String> feature_type_and_source_1 = new ArrayList<>(Arrays.asList("source", "1..678"));
    List<String> feature_type_and_source_2 = new ArrayList<>(Arrays.asList("gene", "1..678"));
    List<String> feature_type_and_source_3 = new ArrayList<>(Arrays.asList("CDS", "1..678"));

    Map<List<String>, HashMap<String, String>> feature_to_qualifiers = new HashMap();

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
        gi = new GenbankInterpreter("/var/20n/home/nishant/code/20n/act/reachables/src/test/resources/com/act/utils/ncbi.gb");

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

        feature_to_qualifiers.put(feature_type_and_source_1, qualifier_name_to_value_1);
        feature_to_qualifiers.put(feature_type_and_source_2, qualifier_name_to_value_2);
        feature_to_qualifiers.put(feature_type_and_source_3, qualifier_name_to_value_3);
    }

    @After
    public void tearDown() {
        // do nothing
    }

    @Test
    public void testReadSequence() {
        assertEquals(gi.getSequence(), SEQ);
    }

    @Test
    public void testReadFeatures() {
        List<String> feature_types = new ArrayList<>(Arrays.asList("source", "gene", "CDS"));
        for (String feature_type : feature_types) {
            assertTrue(gi.getFeatures().contains(feature_type));
        }
    }

    @Test
    public void testReadQualifiers() {
        for (List<String> feature_type_and_source : feature_to_qualifiers.keySet()) {
            for (List<Qualifier> qual_list : gi.getQualifiers(feature_type_and_source.get(0), feature_type_and_source.get(1)).values()) {
                for (Qualifier qual : qual_list) {
                    HashMap qual_map = feature_to_qualifiers.get(feature_type_and_source);
                    assertTrue(qual_map.containsKey(qual.getName()));
                    if (qual.getName().equals("dbxref")) {
                        assertEquals(qual_map.get(qual.getName()), ((DBReferenceInfo) qual).getDatabase() + ":" + ((DBReferenceInfo) qual).getId());
                    }
                    else {
                        assertEquals(qual_map.get(qual.getName()), qual.getValue());
                    }
                }
            }
        }
    }

    @Test
    public void testWriteFeatureAndQualifier() {
        AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature = gi.constructFeature("test_type", "test_source");
        Qualifier qualifier = gi.constructQualifier("test_name", "test_value");

        gi.addQualifier(feature, qualifier);
        gi.addFeature(1, 687, feature);

        assertTrue(gi.getFeatures().contains("test_type"));
        assertTrue(gi.getQualifiers("test_type", "test_source").keySet().contains("test_name"));
        assertTrue(gi.getQualifiers("test_type", "test_source").get("test_name").contains(qualifier));
    }

}

