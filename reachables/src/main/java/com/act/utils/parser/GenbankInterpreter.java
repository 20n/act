package com.act.utils.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.act.biointerpretation.BiointerpretationDriver;
import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.DBReferenceInfo;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.io.GenbankReaderHelper;
import org.biojava.nbio.core.sequence.template.AbstractSequence;

public class GenbankInterpreter {
    private File dnaFile;
    private DNASequence sequence;

    public static final String OPTION_GENBANK_PATH = "p";
    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getFormatterLogger(BiointerpretationDriver.class);

    public GenbankInterpreter(String file) throws Exception {
        dnaFile = new File(file);
        readSequence();
    }

    /**
     * Parses the sequence object from the Genbank File
     * @throws Exception
     */
    public void readSequence() throws Exception {
        Map<String, DNASequence> dnaSequences = GenbankReaderHelper.readGenbankDNASequence(dnaFile);
        // ADDRESS: why do DNA sequences only have 1 entry?
        if (dnaSequences.size() == 1) {
            String key = dnaSequences.keySet().iterator().next();
            sequence = dnaSequences.get(key);
        }
    }

    /**
     * Prints and returns the genetic sequence of the Genbank Entry
     */
    public String getSequence() {
        if (sequence == null) {
            String msg = String.format("Class hasn't been appropriately initialized, no sequence object");
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
        System.out.println("Sequence:");
        System.out.println(sequence.getSequenceAsString());
        System.out.println("\n");
        return sequence.getSequenceAsString();
    }

    /**
     * Prints all the Features and corresponding Qualifiers for a particular sequence
     */
    public void printFeaturesAndQualifiers() {
        if (sequence == null) {
            String msg = String.format("Class hasn't been appropriately initialized, no sequence object");
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
        List<FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound>> features = sequence.getFeatures();
        for (FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature : features) {
            System.out.println("Type: " + feature.getType() + "; Source: " + feature.getSource() + "\n");
            Map<String, List<Qualifier>> qualifiers = feature.getQualifiers();
            for (List<Qualifier> qual_list : qualifiers.values()) {
                for (Qualifier qual : qual_list) {
                    if (qual.getName().equals("dbxref")) {
                        System.out.println("/" + qual.getName() + "=\"" + ((DBReferenceInfo) qual).getDatabase() + ":" + ((DBReferenceInfo)qual).getId() + "\" |");
                    }
                    else {
                        System.out.println("/" + qual.getName() + "=\"" + qual.getValue() + "\" |");
                    }
                }
            }
            System.out.println("=======================\n");
        }
    }

    /**
     * @return - returns an array of all feature types in the Genbank file
     */
    public ArrayList<String> getFeatures() {
        if (sequence == null) {
            String msg = String.format("Class hasn't been appropriately initialized, no sequence object");
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
        ArrayList<String> feature_types = new ArrayList<String> ();
        List<FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound>> features = sequence.getFeatures();
        for (FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature : features) {
            feature_types.add(feature.getType());
        }
        return feature_types;
    }

    /**
     * Given a feature_type and feature_source, returns a map of the corresponding qualifiers
     * @param feature_type
     * @param feature_source
     * @return
     */
    public Map<String, List<Qualifier>> getQualifiers(String feature_type, String feature_source) {
        if (sequence == null) {
            String msg = String.format("Class hasn't been appropriately initialized, no sequence object");
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
        List<FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound>> features = sequence.getFeatures();
        for (FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature : features) {
            if (feature.getType().equals(feature_type) && feature.getSource().equals(feature_source)) {
                return feature.getQualifiers();
            }
        }
        return null;
    }
    /**
     * Adds a Qualifier to a particular Feature i.e. /organism="Escherichia Coli"
     * @param name - i.e. organism
     * @param value - i.e. "Escherichia Coli"
     */
    public Qualifier constructQualifier(String name, String value) {
        return new Qualifier(name, value);
    }

    /**
     * Adds a Qualifier to a particular Feature i.e. /organism="Escherichia Coli"
     * @param feature
     * @param qualifier
     */
    public void addQualifier(AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature, Qualifier qualifier) {
        feature.addQualifier(qualifier.getName(), qualifier);
    }

    /**
     * Constructs a Feature with a particular type (i.e. gene) and source (i.e. 1..678)
     * @param type
     * @param source
     * @return - the constructed Feature object
     */
    public AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> constructFeature(String type, String source) {
        return new AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound>(type, source) {};
    }

    /**
     * Once the Feature has been constructed and all the qualifiers have been added, this method adds the feature to the sequence
     * @param bioStart
     * @param bioEnd
     * @param feature
     * @throws Exception
     */
    public void addFeature(int bioStart, int bioEnd, AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature) {
        if (sequence == null) {
            String msg = String.format("Class hasn't been appropriately initialized, no sequence object");
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
        sequence.addFeature(bioStart, bioEnd, feature);
    }

    public static final String HELP_MESSAGE = StringUtils.join(new String[]{
            "This class parses Genbank files. It can be used on the command line with a file path as a parameter."
    }, "");

    public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
        add(Option.builder(OPTION_GENBANK_PATH)
                .argName("genbank file")
                .desc("genbank file containing sequence and annotations")
                .hasArg()
                .longOpt("genbank")
        );
    }};

    public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

    static {
        HELP_FORMATTER.setWidth(100);
    }

    public static void main(String[] args) throws Exception {
        Options opts = new Options();
        for (Option.Builder b : OPTION_BUILDERS) {
            opts.addOption(b.build());
        }

        CommandLine cl = null;
        try {
            CommandLineParser parser = new DefaultParser();
            cl = parser.parse(opts, args);
        } catch (ParseException e) {
            System.err.format("Argument parsing failed: %s\n", e.getMessage());
            HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
            System.exit(1);
        }

        if (cl.hasOption("genbank")) {
            String genbankFile = cl.getOptionValue("genbank");
            if (genbankFile == null) {
                String msg = String.format("Genbank file path is null");
                LOGGER.error(msg);
                throw new RuntimeException(msg);
            }
            else {
                GenbankInterpreter reader = new GenbankInterpreter(genbankFile);
                reader.getSequence();
                reader.printFeaturesAndQualifiers();
            }
        }
        else {
            String msg = String.format("No Genbank file path given");
            LOGGER.error(msg);
            throw new RuntimeException(msg);
        }
    }
}