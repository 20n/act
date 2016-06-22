package com.act.utils.parser;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.compound.AminoAcidCompound;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.DBReferenceInfo;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.io.GenbankReaderHelper;
import org.biojava.nbio.core.sequence.template.AbstractSequence;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;


public class GenbankProteinSeqInterpreter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankProteinSeqInterpreter.class);
  public static final String OPTION_GENBANK_PATH = "p";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class parses Genbank Protein sequence files. It can be used on the command line with" +
          "a file path as a parameter."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_GENBANK_PATH)
        .argName("genbank file")
        .desc("genbank dna sequence file containing sequence and annotations")
        .hasArg()
        .longOpt("genbank")
        .required()
    );
    add(Option.builder("h")
        .argName("help")
        .desc("Example of usage: -p filepath.gb")
        .longOpt("help")
    );
  }};

  public static final HelpFormatter HELP_FORMATTER = new HelpFormatter();

  static {
    HELP_FORMATTER.setWidth(100);
  }

  private File protFile;
  public ArrayList<ProteinSequence> sequences = new ArrayList<>();

  /**
   * Parses every sequence object from the Genbank File
   * @throws Exception
   */
  public void init() throws Exception {
    Map<String, ProteinSequence> proteinSequences;
    // Automatically decompress any gzip'd genbank files.  This should save storage space and I/O.
    if (protFile.getName().endsWith(".gz")) {
      try (InputStream is = new GZIPInputStream(new FileInputStream(protFile))) {
        proteinSequences = GenbankReaderHelper.readGenbankProteinSequence(is);
      }
    } else {
      proteinSequences = GenbankReaderHelper.readGenbankProteinSequence(protFile);
    }

    for (ProteinSequence proteinSequence : proteinSequences.values()) {
      sequences.add(proteinSequence);
    }
  }

  /**
   * Checks if sequence object has been initialized, throws RuntimeException if not
   */
  public void checkInit() {
    if (sequences == null) {
      String msg = String.format("Class hasn't been appropriately initialized, no sequence object");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  public GenbankProteinSeqInterpreter(File GenbankFile) {
    protFile = GenbankFile;
  }

  /**
   * Prints the genetic sequences extracted from the sequence objects
   */
  public void printSequences() {
    checkInit();
    for (ProteinSequence sequence : this.sequences) {
      System.out.println("Sequence:");
      System.out.println(sequence.getSequenceAsString());
      System.out.println("\n");
    }
  }

  /**
   * Extracts the genetic sequence as a string from the sequence objects
   * @return A list of genetic sequences as strings
   */
  public ArrayList<String> getSequences() {
    checkInit();
    ArrayList<String> sequences = new ArrayList<>();
    for (ProteinSequence sequence : this.sequences) {
      sequences.add(sequence.getSequenceAsString());
    }
    return sequences;
  }

  /**
   * Prints all the Features and corresponding Qualifiers for each sequence object
   */
  public void printFeaturesAndQualifiers() {
    checkInit();
    for (ProteinSequence sequence : sequences) {
      List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features =
          sequence.getFeatures();
      for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
        System.out.println("Type: " + feature.getType() + "; Source: " + feature.getSource() + "\n");
        Map<String, List<Qualifier>> qualifiers = feature.getQualifiers();
        for (List<Qualifier> qual_list : qualifiers.values()) {
          for (Qualifier qual : qual_list) {
            if (qual.getName().equals("dbxref")) {
              System.out.println("/" + qual.getName() + "=\"" + ((DBReferenceInfo) qual).getDatabase() + ":" +
                  ((DBReferenceInfo) qual).getId() + "\" |");
            } else {
              System.out.println("/" + qual.getName() + "=\"" + qual.getValue() + "\" |");
            }
          }
        }
        System.out.println("=======================\n");
      }
    }
  }

  /**
   * Extracts feature types from the sequence object
   * @return list of all feature types in the Genbank file
   */
  public ArrayList<ArrayList<String>> getFeatures() {
    checkInit();
    ArrayList<ArrayList<String>> all_feature_types = new ArrayList<>();
    for (ProteinSequence sequence : sequences) {
      ArrayList<String> feature_types = new ArrayList<String>();
      List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features =
          sequence.getFeatures();
      for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
        feature_types.add(feature.getType());
      }
      all_feature_types.add(feature_types);
    }
    return all_feature_types;
  }

  /**
   * Extracts qualifiers for a particular feature in the sequence object
   * @param sequence_index the index of the sequence object of interest in the sequences list
   * @param feature_type i.e. "source", "gene", "CDS", etc
   * @param feature_source i.e. "1..678"
   * @return Map of the corresponding qualifiers with the key being the Qualifier name (i.e. organism, mol_type, etc)
   * and the value being the list of Qualifiers that have the same name as the key
   */
  public Map<String, List<Qualifier>> getQualifiers(int sequence_index, String feature_type, String feature_source) {
    checkInit();
    List<FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>> features =
        sequences.get(sequence_index).getFeatures();
    for (FeatureInterface<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature : features) {
      if (feature.getType().equals(feature_type) && feature.getSource().equals(feature_source)) {
        return feature.getQualifiers();
      }
    }
    return null;
  }

  /**
   * Adds a Qualifier to a particular Feature i.e. /organism="Escherichia Coli"
   * @param feature the feature object you'd like to add the qualifier to
   * @param qual_name e.g. "organism"
   * @param qual_value e.g. "Escherichia Coli"
   */
  public void addQualifier(AbstractFeature<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> feature,
                           String qual_name, String qual_value) {
    feature.addQualifier(qual_name, new Qualifier(qual_name, qual_value));
  }

  /**
   * Constructs a Feature with a particular type (i.e. gene) and source (i.e. 1..678)
   * @param type e.g. "gene", "source", "CDS"
   * @param source e,g. "1..678"
   * @return the constructed Feature object
   */
  public AbstractFeature<AbstractSequence<AminoAcidCompound>, AminoAcidCompound> constructFeature(String type,
                                                                                                    String source) {
    return new AbstractFeature<AbstractSequence<AminoAcidCompound>, AminoAcidCompound>(type, source) {};
  }

  /**
   * prints the description string for each sequence
   */
  public void printDescription() {
    for (ProteinSequence sequence : sequences) {
      System.out.println(sequence.getDescription());
    }
  }

  /**
   * prints the Accession ID for each sequence
   */
  public void printAccessionID() {
    for (ProteinSequence sequence : sequences) {
      System.out.println(sequence.getAccession().getID());
    }
  }

  public void printHeader() {
    for (ProteinSequence sequence : sequences) {
      System.out.println(sequence.getOriginalHeader());
    }
  }

  /**
   * Once the Feature has been constructed and all the qualifiers have been added, this method adds the feature to
   * a specific sequence
   * @param bioStart the start index of the source of the feature
   * @param bioEnd the end index of the source of the feature
   * @param feature the feature object to be added
   * @param sequence_index the index of the sequence of interest in the sequences list
   */
  public void addFeature(int bioStart, int bioEnd, AbstractFeature<AbstractSequence<AminoAcidCompound>,
      AminoAcidCompound> feature, int sequence_index) {
    checkInit();
    sequences.get(sequence_index).addFeature(bioStart, bioEnd, feature);
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
      if (cl.hasOption("help")) {
        HELP_FORMATTER.printHelp(GenbankProteinSeqInterpreter.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
        return;
      }
    }

    File genbankFile = new File(cl.getOptionValue(OPTION_GENBANK_PATH));
    if (!genbankFile.exists()) {
      String msg = String.format("Genbank file path is null");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      GenbankProteinSeqInterpreter reader = new GenbankProteinSeqInterpreter(genbankFile);
      reader.init();
      reader.printSequences();
    }
  }
}
