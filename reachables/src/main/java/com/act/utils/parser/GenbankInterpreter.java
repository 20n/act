package com.act.utils.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.act.lcms.db.io.LoadPlateCompositionIntoDB;
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
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.features.AbstractFeature;
import org.biojava.nbio.core.sequence.features.DBReferenceInfo;
import org.biojava.nbio.core.sequence.features.FeatureInterface;
import org.biojava.nbio.core.sequence.features.Qualifier;
import org.biojava.nbio.core.sequence.io.GenbankReaderHelper;
import org.biojava.nbio.core.sequence.template.AbstractSequence;

public class GenbankInterpreter {
  private static final Logger LOGGER = LogManager.getFormatterLogger(GenbankInterpreter.class);
  public static final String OPTION_GENBANK_PATH = "p";

  public static final String HELP_MESSAGE = StringUtils.join(new String[]{
      "This class parses Genbank files. It can be used on the command line with a file path as a parameter."
  }, "");

  public static final List<Option.Builder> OPTION_BUILDERS = new ArrayList<Option.Builder>() {{
    add(Option.builder(OPTION_GENBANK_PATH)
        .argName("genbank file")
        .desc("genbank file containing sequence and annotations")
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

  private File dnaFile;
  private ArrayList<DNASequence> sequences = new ArrayList<>();

  /**
   * Parses every sequence object from the Genbank File
   *
   * @throws Exception
   */
  public void init() throws Exception {
    Map<String, DNASequence> dnaSequences = GenbankReaderHelper.readGenbankDNASequence(dnaFile);
    for (DNASequence dnaSequence : dnaSequences.values()) {
      sequences.add(dnaSequence);
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


  public GenbankInterpreter(String file) {
    dnaFile = new File(file);
  }

  /**
   * Prints and returns the genetic sequences of the Genbank Entries
   */
  public ArrayList<String> getSequences() {
    checkInit();
    ArrayList<String> sequences = new ArrayList<>();
    for (DNASequence sequence : this.sequences) {
      System.out.println("Sequence:");
      System.out.println(sequence.getSequenceAsString());
      System.out.println("\n");
      sequences.add(sequence.getSequenceAsString());
    }
    return sequences;
  }

  /**
   * Prints all the Features and corresponding Qualifiers for each sequence object
   */
  public void printFeaturesAndQualifiers() {
    checkInit();
    for (DNASequence sequence : sequences) {
      List<FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound>> features = sequence.getFeatures();
      for (FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature : features) {
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
   * @return - returns an array of all feature types in the Genbank file
   */
  public ArrayList<ArrayList<String>> getFeatures() {
    checkInit();
    ArrayList<ArrayList<String>> all_feature_types = new ArrayList<>();
    for (DNASequence sequence : sequences) {
      ArrayList<String> feature_types = new ArrayList<String>();
      List<FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound>> features = sequence.getFeatures();
      for (FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature : features) {
        feature_types.add(feature.getType());
      }
      all_feature_types.add(feature_types);
    }
    return all_feature_types;
  }

  /**
   * Given a sequence index, feature_type and feature_source, returns a map of the corresponding qualifiers with the
   * key being the Qualifier name (i.e. organism, mol_type, etc) and the value being the Qualifier value
   * (i.e. Eschericia Coli, genomic DNA, etc)
   * @param sequence_index
   * @param feature_type
   * @param feature_source
   * @return
   */
  public Map<String, List<Qualifier>> getQualifiers(int sequence_index, String feature_type, String feature_source) {
    checkInit();
    List<FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound>> features =
        sequences.get(sequence_index).getFeatures();
    for (FeatureInterface<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature : features) {
      if (feature.getType().equals(feature_type) && feature.getSource().equals(feature_source)) {
        return feature.getQualifiers();
      }
    }
    return null;
  }

  /**
   * Adds a Qualifier to a particular Feature i.e. /organism="Escherichia Coli"
   * @param name  - i.e. organism
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
  public void addQualifier(AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> feature,
                           Qualifier qualifier) {
    feature.addQualifier(qualifier.getName(), qualifier);
  }

  /**
   * Constructs a Feature with a particular type (i.e. gene) and source (i.e. 1..678)
   * @param type
   * @param source
   * @return - the constructed Feature object
   */
  public AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound> constructFeature(String type,
                                                                                                    String source) {
    return new AbstractFeature<AbstractSequence<NucleotideCompound>, NucleotideCompound>(type, source) {
    };
  }

  /**
   * Once the Feature has been constructed and all the qualifiers have been added, this method adds the feature to
   * a specific sequence
   *
   * @param bioStart
   * @param bioEnd
   * @param feature
   * @param sequence_index
   * @throws Exception
   */
  public void addFeature(int bioStart, int bioEnd, AbstractFeature<AbstractSequence<NucleotideCompound>,
      NucleotideCompound> feature, int sequence_index) {
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
      System.err.format("Argument parsing failed: %s\n", e.getMessage());
      HELP_FORMATTER.printHelp(LoadPlateCompositionIntoDB.class.getCanonicalName(), HELP_MESSAGE, opts, null, true);
      System.exit(1);
    }

    String genbankFile = cl.getOptionValue("genbank");
    if (!new File(genbankFile).exists()) {
      String msg = String.format("Genbank file path is null");
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    } else {
      GenbankInterpreter reader = new GenbankInterpreter(genbankFile);
      reader.init();
      reader.getSequences();
      reader.printFeaturesAndQualifiers();
    }
  }
}