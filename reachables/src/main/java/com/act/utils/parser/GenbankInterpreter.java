package com.act.utils.parser;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.io.GenbankReaderHelper;
import org.biojava.nbio.core.sequence.template.AbstractSequence;

public class GenbankInterpreter {
    private static File dnaFile;
    private static DNASequence sequence;

    public GenbankInterpreter(String file) {
        dnaFile = new File(file);
    }

    public static void readSequence() throws Exception {
        LinkedHashMap<String, DNASequence> dnaSequences = GenbankReaderHelper.readGenbankDNASequence(dnaFile);
        if (dnaSequences.size() == 1) {
            Object key = dnaSequences.keySet().iterator().next();
            sequence = dnaSequences.get(key);
        }
    }

    // prints the genetic sequence of the Genbank Entry
    public static void printSequence() throws Exception {
        if (sequence == null) {
            readSequence();
        }
        System.out.println("Sequence:");
        System.out.println(sequence.getSequenceAsString());
        System.out.println("\n");
    }

    // prints all the Features and corresponding Qualifiers for a particular sequence
    public static void printFeatures() throws Exception {
        if (sequence == null) {
            readSequence();
        }
        List<FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound>> features = sequence.getFeatures();
        for (FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature : features) {
            System.out.println("Type: " + feature.getType() + "; Source: " + feature.getSource() + "\n");
            Map<String, List<Qualifier>> qualifiers = feature.getQualifiers();
            for (List<Qualifier> qual_list : qualifiers.values()) {
                for (Qualifier qual : qual_list) {
                    System.out.println("/" + qual.getName() + "=\"" + qual.getValue() + "\" |");
                }
            }
            System.out.println("=======================\n");
        }
    }

    // adds a Qualifier to a particular Feature
    public static void addQualifier(AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature, String key, String name, String value) {
        Qualifier qual = new Qualifier(name, value);
        feature.addQualifier(key, qual);
    }

    // constructs a Feature with a particular type and source
    public static AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> constructFeature(String type, String source) {
        return new AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound>(type, source) {};
    }

    // once the Feature has been constructed and all the qualifiers have been added, this method adds the feature to the sequence
    public static void addFeature(int bioStart, int bioEnd, AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature) throws Exception {
        if (sequence == null) {
            readSequence();
        }
        sequence.addFeature(bioStart, bioEnd, feature);
    }

    public static void main(String[] args) throws Exception {
        // tests the reading/parsing capability
        GenbankInterpreter reader = new GenbankInterpreter("src/main/java/act/installer/genbank/ncbi.gb");
        reader.printSequence();
        reader.printFeatures();

        // tests the writing capability
        AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature = reader.constructFeature("type_test", "1..678");
        reader.addQualifier(feature, "test_key", "test_name", "test_value");
        reader.addFeature(1, 50, feature);
        reader.printSequence();
        reader.printFeatures();
    }
}